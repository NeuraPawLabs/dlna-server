package labs.newrapaw.dlna.probe.proxy

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalHlsProxyStabilityTest {
    @Test
    fun routeExceptionsReturnHttp500AndLogError() {
        val logs = mutableListOf<String>()
        val proxy = LocalHlsProxy(
            log = { logs.add(it) },
            dlnaConfig = { error("description failed") },
        )

        proxy.start()
        try {
            val response = rawHttpRequest(proxy.port, "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 500"))
            assertTrue(response.contains("Internal Server Error"))
            assertTrue(logs.any { it.contains("Request failed") && it.contains("description failed") })
        } finally {
            proxy.close()
        }
    }

    @Test
    fun brokenPipeIsNotLoggedAsRequestFailure() {
        val logs = mutableListOf<String>()
        val proxy = LocalHlsProxy(log = { logs.add(it) })

        proxy.start()
        try {
            val suppressed = shouldSuppressProxyRequestFailureLog(SocketException("Broken pipe"))

            assertTrue(suppressed)
            assertTrue(logs.none { it.contains("Broken pipe") })
        } finally {
            proxy.close()
        }
    }

    @Test
    fun connectionResetByPeerIsNotLoggedAsRequestFailure() {
        val logs = mutableListOf<String>()
        val proxy = LocalHlsProxy(log = { logs.add(it) })

        proxy.start()
        try {
            val suppressed = shouldSuppressProxyRequestFailureLog(SocketException("Connection reset by peer"))

            assertTrue(suppressed)
            assertTrue(logs.none { it.contains("Connection reset by peer") })
        } finally {
            proxy.close()
        }
    }

    @Test
    fun adminPagesRenderAsSeparateRoutes() {
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val play = rawHttpRequest(proxy.port, "GET /play HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val cache = rawHttpRequest(proxy.port, "GET /cache HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val logs = rawHttpRequest(proxy.port, "GET /logs-page HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val settings = rawHttpRequest(proxy.port, "GET /settings HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(play.startsWith("HTTP/1.1 200"))
            assertTrue(cache.startsWith("HTTP/1.1 200"))
            assertTrue(logs.startsWith("HTTP/1.1 200"))
            assertTrue(settings.startsWith("HTTP/1.1 200"))
            assertTrue(play.contains(">播放<"))
            assertTrue(cache.contains(">缓存<"))
            assertTrue(logs.contains(">日志<"))
            assertTrue(settings.contains(">设置<"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun compatibilityProxyRoutesAreRemoved() {
        val upstreamHits = AtomicInteger(0)
        val upstream = singleResponseServer(upstreamHits, "segment-bytes")
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${upstream.localPort}/seg0.ts"
            val path = "/proxy/segment.ts?u=${labs.newrapaw.dlna.probe.core.encodeProxyUrl(upstreamUrl)}"

            val segment = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val manifest = rawHttpRequest(
                proxy.port,
                "GET /proxy/hls.m3u8?u=${labs.newrapaw.dlna.probe.core.encodeProxyUrl("http://127.0.0.1:${upstream.localPort}/index.m3u8")} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertTrue(segment.startsWith("HTTP/1.1 404"))
            assertTrue(manifest.startsWith("HTTP/1.1 404"))
            assertEquals(0, upstreamHits.get())
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun cacheClearRouteStillReturnsSuccessWithoutLegacySegmentCache() {
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 200"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun cacheClearRouteStopsPlaybackAndClearsActiveSession() {
        val stopRequested = AtomicBoolean(false)
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
            onStopRequested = { stopRequested.set(true) },
        )

        proxy.start()
        try {
            val playBody = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${playBody.length}\r\n\r\n$playBody",
            )
            assertTrue(proxy.activeSessionInfo() != null)

            val clearResponse = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n",
            )

            assertTrue(clearResponse.startsWith("HTTP/1.1 200"))
            assertTrue(stopRequested.get())
            assertEquals(null, proxy.activeSessionInfo())
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun segmentedPostBodyIsReadCompletelyBeforeDispatchingPlayRequest() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val sourceUrl = "http://127.0.0.1:${upstream.localPort}/video.m3u8"
            val body = "url=${URLEncoder.encode(sourceUrl, "UTF-8")}"
            val splitAt = body.indexOf("%2Fvideo").coerceAtLeast(body.length / 2)

            val response = rawSegmentedHttpRequest(
                port = proxy.port,
                headers = "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n",
                firstBodyChunk = body.substring(0, splitAt),
                secondBodyChunk = body.substring(splitAt),
            )

            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertEquals(1, requestedUrls.size)
            assertTrue(requestedUrls.single().startsWith("http://127.0.0.1:${proxy.port}/session/"))
            assertTrue(requestedUrls.single().endsWith("/manifest.m3u8"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun truncatedPostBodyIsRejectedWithoutDispatchingPlayRequest() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val sourceUrl = "http://127.0.0.1:${upstream.localPort}/video.m3u8"
            val fullBody = "url=${URLEncoder.encode(sourceUrl, "UTF-8")}"
            val truncatedBody = fullBody.dropLast(4)

            val response = rawTruncatedHttpRequest(
                port = proxy.port,
                headers = "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${fullBody.length}\r\n\r\n",
                partialBody = truncatedBody,
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertTrue(requestedUrls.isEmpty())
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun malformedFormEncodingIsRejectedWithoutDispatchingPlayRequest() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=http%3A%2F%2Fexample.com%2Fvideo%ZZ.m3u8"
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertTrue(requestedUrls.isEmpty())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun truncatedHttpHeadersReturn400InsteadOfDispatchingRoute() {
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun malformedHttpHeaderLineReturns400InsteadOfDispatchingRoute() {
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Bad-Header\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun malformedRequestLineReturns400InsteadOfDispatchingRoute() {
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "GET /description.xml\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun invalidContentLengthReturns400InsteadOfDispatchingBodylessControlRoute() {
        val stopRequested = AtomicBoolean(false)
        val proxy = LocalHlsProxy(
            log = {},
            onStopRequested = { stopRequested.set(true) },
        )

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: nope\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertFalse(stopRequested.get())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun duplicateContentLengthReturns400InsteadOfDispatchingBodylessControlRoute() {
        val stopRequested = AtomicBoolean(false)
        val proxy = LocalHlsProxy(
            log = {},
            onStopRequested = { stopRequested.set(true) },
        )

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: nope\r\n" +
                    "Content-Length: 0\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertFalse(stopRequested.get())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun oversizedContentLengthReturns400InsteadOfAttemptingBodyAllocation() {
        val stopRequested = AtomicBoolean(false)
        val proxy = LocalHlsProxy(
            log = {},
            onStopRequested = { stopRequested.set(true) },
        )

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: 10485761\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertTrue(response.contains("request body too large"))
            assertFalse(stopRequested.get())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun dlnaSoapRequestWithUtf8BodyIsReadByByteLength() {
        val played = CopyOnWriteArrayList<String>()
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = played::add,
        )

        proxy.start()
        try {
            val metadata = "<dc:title>中文标题</dc:title>"
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                      <CurrentURI>https://example.com/video.m3u8</CurrentURI>
                      <CurrentURIMetaData>$metadata</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()
            val contentLength = body.toByteArray(Charsets.UTF_8).size

            val response = rawHttpRequest(
                proxy.port,
                "POST /upnp/control/AVTransport HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Type: text/xml; charset=utf-8\r\n" +
                    "SOAPACTION: \"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"\r\n" +
                    "Content-Length: $contentLength\r\n\r\n" +
                    body,
            )

            assertTrue(response.startsWith("HTTP/1.1 200"))
            val playBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><Speed>1</Speed></u:Play>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()
            val playResponse = rawHttpRequest(
                proxy.port,
                "POST /upnp/control/AVTransport HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Type: text/xml; charset=utf-8\r\n" +
                    "SOAPACTION: \"urn:schemas-upnp-org:service:AVTransport:1#Play\"\r\n" +
                    "Content-Length: ${playBody.toByteArray(Charsets.UTF_8).size}\r\n\r\n" +
                    playBody,
            )

            assertTrue(playResponse.startsWith("HTTP/1.1 200"))
            assertEquals(1, played.size)
            assertTrue(played.single().startsWith("http://127.0.0.1:${proxy.port}/session/"))
            assertTrue(played.single().endsWith("/manifest.m3u8"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun proxyServerStartFailureClosesSocketAndResetsPort() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdownNow()
        val server = LocalHlsProxyServer(
            executor = executor,
            handleSocket = {},
            safeLog = {},
        )

        try {
            runCatching { server.start() }
                .onSuccess { throw AssertionError("expected LocalHlsProxyServer.start() to fail with a closed executor") }

            assertEquals(0, server.port)
            assertEquals("http://127.0.0.1:0", server.baseUrl)
        } finally {
            server.close()
        }
    }

    @Test
    fun acceptedProxySocketsReceiveReadTimeoutBeforeDispatch() {
        val executor = Executors.newFixedThreadPool(2)
        val recordedTimeoutMs = AtomicInteger(-1)
        val handled = CountDownLatch(1)
        val server = LocalHlsProxyServer(
            executor = executor,
            handleSocket = { socket ->
                recordedTimeoutMs.set(socket.soTimeout)
                handled.countDown()
                socket.close()
            },
            safeLog = {},
        )

        try {
            server.start()

            Socket("127.0.0.1", server.port).use { socket ->
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }

            assertTrue("accepted socket was not dispatched", handled.await(5, TimeUnit.SECONDS))
            assertEquals(LocalHlsProxyServer.DEFAULT_SOCKET_TIMEOUT_MS, recordedTimeoutMs.get())
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun saturatedProxyServerRejectsExcessSocketsInsteadOfStallingAcceptLoop() {
        val executor = productionProxyExecutor(maxThreads = 2, queueCapacity = 1)
        val releaseHandlers = CountDownLatch(1)
        val enteredHandlers = CountDownLatch(1)
        val handledCount = AtomicInteger(0)
        val server = LocalHlsProxyServer(
            executor = executor,
            handleSocket = { socket ->
                val index = handledCount.incrementAndGet()
                if (index == 1) {
                    enteredHandlers.countDown()
                    runCatching { releaseHandlers.await(5, TimeUnit.SECONDS) }
                } else if (index == 2) {
                    runCatching { releaseHandlers.await(5, TimeUnit.SECONDS) }
                }
                socket.close()
            },
            safeLog = {},
        )

        val firstSocket = Socket()
        val secondSocket = Socket()
        try {
            server.start()

            firstSocket.connect(java.net.InetSocketAddress("127.0.0.1", server.port))
            assertTrue("first handler did not start", enteredHandlers.await(5, TimeUnit.SECONDS))

            secondSocket.connect(java.net.InetSocketAddress("127.0.0.1", server.port))

            Socket("127.0.0.1", server.port).use { thirdSocket ->
                thirdSocket.soTimeout = 750
                thirdSocket.getOutputStream().write("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.UTF_8))
                thirdSocket.getOutputStream().flush()
                val response = runCatching {
                    thirdSocket.getInputStream().readBytes().toString(Charsets.UTF_8)
                }.getOrElse {
                    throw AssertionError("saturated proxy socket should fail fast instead of stalling", it)
                }

                assertTrue(response.startsWith("HTTP/1.1 503"))
            }
        } finally {
            releaseHandlers.countDown()
            runCatching { firstSocket.close() }
            runCatching { secondSocket.close() }
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun closingProxyServerClosesAcceptedSockets() {
        val executor = Executors.newFixedThreadPool(2)
        val acceptedSocket = AtomicReference<Socket?>()
        val enteredHandler = CountDownLatch(1)
        val releaseHandler = CountDownLatch(1)
        val server = LocalHlsProxyServer(
            executor = executor,
            handleSocket = { socket ->
                acceptedSocket.set(socket)
                enteredHandler.countDown()
                runCatching { releaseHandler.await(5, TimeUnit.SECONDS) }
            },
            safeLog = {},
        )

        try {
            server.start()

            Socket("127.0.0.1", server.port).use { client ->
                client.getOutputStream().write("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.UTF_8))
                client.getOutputStream().flush()

                assertTrue("handler did not receive accepted socket", enteredHandler.await(5, TimeUnit.SECONDS))
                server.close()

                eventually(timeoutMs = 2_000L) {
                    assertTrue(acceptedSocket.get()?.isClosed == true)
                }
            }
        } finally {
            releaseHandler.countDown()
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun proxyStartFailureClosesCoreProxyBeforeRethrowing() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyHost.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("runCatching {"))
        assertTrue(source.contains("coreProxy.start()"))
        assertTrue(source.contains("proxyServer.start()"))
        assertTrue(source.contains("runCatching { coreProxy.close() }"))
        assertTrue(source.contains("runCatching { proxyServer.close() }"))
        assertTrue(source.contains("throw it"))
    }

    @Test
    fun playRequestUsesSessionizedManifestAndServesAudioAndSubtitleAssets() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val logs = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/master.m3u8" to """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Main",LANGUAGE="zh",DEFAULT=YES,URI="audio/index.m3u8"
                    #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="ZH",LANGUAGE="zh",DEFAULT=YES,URI="subs/index.m3u8"
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="audio",SUBTITLES="subs"
                    video/index.m3u8
                """.trimIndent(),
                "/video/index.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/audio/index.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    audio-1.aac
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/subs/index.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    sub-1.vtt
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/video/seg-1.ts" to "segment-one",
                "/audio/audio-1.aac" to "audio-one",
                "/subs/sub-1.vtt" to "WEBVTT\n\n00:00:00.000 --> 00:00:04.000\n字幕一\n",
            ),
            contentTypes = mapOf(
                "/subs/sub-1.vtt" to "text/vtt",
            ),
        )
        val proxy = LocalHlsProxy(
            log = logs::add,
            onPlayRequested = requestedUrls::add,
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(),
            ),
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/master.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val manifestPath = java.net.URI(requestedUrls.single()).path
            val sessionId = manifestPath.substringAfter("/session/").substringBefore("/")

            val masterResponse = rawHttpRequest(
                proxy.port,
                "GET $manifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertTrue(masterResponse.startsWith("HTTP/1.1 200"))
            assertTrue(masterResponse.contains("/session/$sessionId/audio/"))
            assertTrue(masterResponse.contains("/session/$sessionId/subtitle/"))

            val audioPlaylistPath = masterResponse.lineSequence()
                .first { it.contains("TYPE=AUDIO") }
                .substringAfter("URI=\"")
                .substringBefore("\"")
            val subtitlePlaylistPath = masterResponse.lineSequence()
                .first { it.contains("TYPE=SUBTITLES") }
                .substringAfter("URI=\"")
                .substringBefore("\"")

            val audioPlaylistResponse = rawHttpRequest(
                proxy.port,
                "GET $audioPlaylistPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            val subtitlePlaylistResponse = rawHttpRequest(
                proxy.port,
                "GET $subtitlePlaylistPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertTrue(audioPlaylistResponse.startsWith("HTTP/1.1 200"))
            assertTrue(subtitlePlaylistResponse.startsWith("HTTP/1.1 200"))
            assertTrue(audioPlaylistResponse.contains("/session/$sessionId/asset/audio-"))
            assertTrue(subtitlePlaylistResponse.contains("/session/$sessionId/asset/subtitle-"))

            val audioAssetPath = audioPlaylistResponse.lineSequence()
                .first { it.startsWith("/session/$sessionId/asset/") }
            val subtitleAssetPath = subtitlePlaylistResponse.lineSequence()
                .first { it.startsWith("/session/$sessionId/asset/") }

            val audioAssetResponse = rawHttpRequest(
                proxy.port,
                "GET $audioAssetPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            val subtitleAssetResponse = rawHttpRequest(
                proxy.port,
                "GET $subtitleAssetPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertTrue(audioAssetResponse.startsWith("HTTP/1.1 200"))
            assertTrue(audioAssetResponse.contains("audio-one"))
            assertTrue(subtitleAssetResponse.startsWith("HTTP/1.1 200"))
            assertTrue(subtitleAssetResponse.contains("WEBVTT"))
            assertFalse(logs.any { it.startsWith("[diag]") })
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun playRequestUsesAppSessionUrlForLocalPlayback() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )

            val playbackUrl = requestedUrls.single()
            val playbackPort = playbackUrl.substringAfter("http://127.0.0.1:").substringBefore("/").toInt()

            assertEquals("local playback should use the app proxy port", proxy.port, playbackPort)
            assertTrue(playbackUrl.endsWith("/manifest.m3u8"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun activeSessionCanBeRebuiltWithoutDispatchingFreshRemotePlayCallback() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )

            val initialSession = proxy.activeSessionInfo()
            assertTrue(initialSession != null)
            assertEquals(1, requestedUrls.size)

            val rebuildMethod = LocalHlsProxy::class.java.methods.firstOrNull {
                it.name == "recoverActivePlaybackSession" && it.parameterCount == 0
            }
            assertTrue(rebuildMethod != null)
            val rebuiltUrl = rebuildMethod!!.invoke(proxy) as String
            val rebuiltSession = proxy.activeSessionInfo()

            assertTrue(rebuiltSession != null)
            assertEquals(initialSession!!.sourceUrl, rebuiltSession!!.sourceUrl)
            assertTrue(rebuiltUrl != initialSession.localManifestUrl)
            assertTrue(rebuiltSession.localManifestUrl == rebuiltUrl)
            assertEquals(1, requestedUrls.size)

            val oldManifestPath = java.net.URI(initialSession.localManifestUrl).path
            val staleResponse = rawHttpRequest(
                proxy.port,
                "GET $oldManifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            assertTrue(staleResponse.startsWith("HTTP/1.1 410"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun clearedPlaybackSessionCannotBeRecovered() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val initialSession = proxy.activeSessionInfo()
            assertTrue(initialSession != null)

            proxy.clearActivePlaybackSession()

            assertEquals(null, proxy.activeSessionInfo())
            assertEquals(null, proxy.recoverActivePlaybackSession())
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun rangeRequestReturnsPartialAssetResponse() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-0.ts"

            val response = rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=2-5\r\nConnection: close\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 206"))
            assertTrue(response.contains("Content-Range: bytes 2-5/11"))
            assertTrue(response.endsWith("gmen"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun multiRangeRequestIsRejectedInsteadOfSilentlyServingOnlyFirstRange() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-0.ts"

            val response = rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=0-1,4-5\r\nConnection: close\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 416"))
            assertTrue(response.contains("Requested Range Not Satisfiable"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun malformedRangeRequestIsRejectedInsteadOfSilentlyServingFullAsset() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-0.ts"

            val response = rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=abc\r\nConnection: close\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 416"))
            assertTrue(response.contains("Requested Range Not Satisfiable"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun unsupportedRangeUnitIsRejectedInsteadOfServingFullAsset() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-0.ts"

            val response = rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: items=0-1\r\nConnection: close\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 416"))
            assertTrue(response.contains("Requested Range Not Satisfiable"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun hangingDlnaEventCallbacksDoNotBlockProxyHttpRoutes() {
        val acceptedCallbacks = CountDownLatch(7)
        val releaseCallbacks = CountDownLatch(1)
        val hangingSockets = CopyOnWriteArrayList<Socket>()
        val callbackServer = ServerSocket(0)
        Thread {
            runCatching {
                while (!callbackServer.isClosed) {
                    val socket = runCatching { callbackServer.accept() }.getOrNull() ?: break
                    hangingSockets += socket
                    acceptedCallbacks.countDown()
                    Thread {
                        socket.use {
                            releaseCallbacks.await(5, TimeUnit.SECONDS)
                        }
                    }.start()
                }
            }
        }.start()
        val proxy = LocalHlsProxy(
            client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build(),
            log = {},
        )

        proxy.start()
        try {
            repeat(7) { index ->
                val response = rawHttpRequest(
                    proxy.port,
                    """
                    SUBSCRIBE /upnp/event/AVTransport HTTP/1.1
                    Host: 127.0.0.1
                    CALLBACK: <http://127.0.0.1:${callbackServer.localPort}/callback-$index>
                    NT: upnp:event
                    TIMEOUT: Second-300
                    
                    
                    """.trimIndent().replace("\n", "\r\n"),
                )
                assertTrue(response.startsWith("HTTP/1.1 200"))
            }
            assertTrue("callback connections were not established in time", acceptedCallbacks.await(2, TimeUnit.SECONDS))

            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 750
                socket.getOutputStream().write("GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                val response = try {
                    socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                } catch (_: SocketTimeoutException) {
                    fail("proxy route timed out while DLNA notify callbacks were hanging")
                    return
                }

                assertTrue(response.startsWith("HTTP/1.1 503") || response.startsWith("HTTP/1.1 200"))
            }
        } finally {
            releaseCallbacks.countDown()
            hangingSockets.forEach { runCatching { it.close() } }
            runCatching { callbackServer.close() }
            proxy.close()
        }
    }

    @Test
    fun hangingDlnaEventCallbacksEventuallyReleaseNotifyWorkers() {
        val acceptedCallbacks = CountDownLatch(9)
        val releaseCallbacks = CountDownLatch(1)
        val hangingSockets = CopyOnWriteArrayList<Socket>()
        val callbackServer = ServerSocket(0)
        Thread {
            runCatching {
                while (!callbackServer.isClosed) {
                    val socket = runCatching { callbackServer.accept() }.getOrNull() ?: break
                    hangingSockets += socket
                    acceptedCallbacks.countDown()
                    Thread {
                        socket.use {
                            releaseCallbacks.await(5, TimeUnit.SECONDS)
                        }
                    }.start()
                }
            }
        }.start()
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            repeat(9) { index ->
                val response = rawHttpRequest(
                    proxy.port,
                    """
                    SUBSCRIBE /upnp/event/AVTransport HTTP/1.1
                    Host: 127.0.0.1
                    CALLBACK: <http://127.0.0.1:${callbackServer.localPort}/callback-$index>
                    NT: upnp:event
                    TIMEOUT: Second-300
                    
                    
                    """.trimIndent().replace("\n", "\r\n"),
                )
                assertTrue(response.startsWith("HTTP/1.1 200"))
            }

            assertTrue(
                "queued DLNA notify callback never started after earlier callbacks hung",
                acceptedCallbacks.await(4, TimeUnit.SECONDS),
            )
        } finally {
            releaseCallbacks.countDown()
            hangingSockets.forEach { runCatching { it.close() } }
            runCatching { callbackServer.close() }
            proxy.close()
        }
    }

    @Test
    fun repeatedFailedDlnaEventCallbacksArePruned() {
        val callbackCount = AtomicInteger()
        val callbackServer = ServerSocket(0)
        val callbackWorker = Thread {
            runCatching {
                while (!callbackServer.isClosed) {
                    val socket = runCatching { callbackServer.accept() }.getOrNull() ?: break
                    Thread {
                        socket.use {
                            callbackCount.incrementAndGet()
                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            it.getOutputStream().write(
                                (
                                    "HTTP/1.1 500 Internal Server Error\r\n" +
                                        "Content-Length: 0\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(Charsets.UTF_8),
                            )
                            it.getOutputStream().flush()
                        }
                    }.start()
                }
            }
        }
        callbackWorker.isDaemon = true
        callbackWorker.start()
        val proxy = LocalHlsProxy(log = {})

        proxy.start()
        try {
            val subscribeResponse = rawHttpRequest(
                proxy.port,
                """
                SUBSCRIBE /upnp/event/AVTransport HTTP/1.1
                Host: 127.0.0.1
                CALLBACK: <http://127.0.0.1:${callbackServer.localPort}/callback>
                NT: upnp:event
                TIMEOUT: Second-300
                
                
                """.trimIndent().replace("\n", "\r\n"),
            )
            assertTrue(subscribeResponse.startsWith("HTTP/1.1 200"))

            eventually(timeoutMs = 2_000L) {
                assertEquals(1, callbackCount.get())
            }

            proxy.updateDlnaTransportState(transportState = "PLAYING", positionMs = 1_000L)
            proxy.updateDlnaTransportState(transportState = "PAUSED_PLAYBACK", positionMs = 2_000L)

            eventually(timeoutMs = 2_000L) {
                assertEquals(3, callbackCount.get())
            }

            proxy.updateDlnaTransportState(transportState = "STOPPED", positionMs = 3_000L)
            Thread.sleep(250L)

            assertEquals(3, callbackCount.get())
        } finally {
            runCatching { callbackServer.close() }
            proxy.close()
        }
    }

    @Test
    fun malformedRangeWithExtraDashIsRejectedInsteadOfServingOpenEndedSlice() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:4.0,
                    seg-1.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-0.ts"

            val response = rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=2-5-\r\nConnection: close\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 416"))
            assertTrue(response.contains("Requested Range Not Satisfiable"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun manifestRequestDoesNotBlockOnStartupSegmentWarmup() {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:1.0,
                    seg-0.ts
                    #EXTINF:1.0,
                    seg-1.ts
                    #EXTINF:1.0,
                    seg-2.ts
                    #EXTINF:1.0,
                    seg-3.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-0.ts" to "seg0",
                "/seg-1.ts" to "seg1",
                "/seg-2.ts" to "seg2",
                "/seg-3.ts" to "seg3",
            ),
            responseDelayMs = mapOf(
                "/seg-0.ts" to 1_200L,
                "/seg-1.ts" to 1_200L,
                "/seg-2.ts" to 1_200L,
                "/seg-3.ts" to 1_200L,
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val manifestPath = java.net.URI(requestedUrls.single()).path

            val startedAt = System.nanoTime()
            val masterResponse = rawHttpRequest(
                proxy.port,
                "GET $manifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue(masterResponse.startsWith("HTTP/1.1 200"))
            assertFalse("manifest request should not wait for startup segments, elapsed=${elapsedMs}ms", elapsedMs >= 1_000L)
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun sessionAssetTrafficLogsAreSuppressedByDefault() {
        val logs = CopyOnWriteArrayList<String>()
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = multiResponseServer(
            responses = mapOf(
                "/video.m3u8" to """
                    #EXTM3U
                    #EXTINF:1.0,
                    seg-0.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
                "/seg-0.ts" to "seg0",
            ),
        )
        val proxy = LocalHlsProxy(
            log = logs::add,
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val manifestPath = java.net.URI(requestedUrls.single()).path
            val masterResponse = rawHttpRequest(
                proxy.port,
                "GET $manifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            val videoPlaylistPath = masterResponse.lineSequence()
                .first { it.startsWith("/session/") && it.endsWith("/video.m3u8") }
            val videoPlaylistResponse = rawHttpRequest(
                proxy.port,
                "GET $videoPlaylistPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )
            val assetPath = videoPlaylistResponse.lineSequence()
                .first { it.startsWith("/session/") && it.contains("/asset/") }
            rawHttpRequest(
                proxy.port,
                "GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
            )

            assertFalse(logs.any { it.contains("Session asset request") })
            assertFalse(logs.any { it.contains("Session asset response status=200") })
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun sessionAssetForwardingStartsStreamingBeforeCoreSegmentCompletes() {
        val upstreamCompletionDelayMs = 2_000L
        val delayedSegmentIndex = 19
        val upstreamHeadSentAt = AtomicLong(0L)
        val upstreamCompletedAt = AtomicLong(0L)
        val upstreamCompleted = CountDownLatch(1)
        val requestedUrls = CopyOnWriteArrayList<String>()
        val upstream = ServerSocket(0)
        Thread {
            runCatching {
                while (!upstream.isClosed) {
                    val socket = runCatching { upstream.accept() }.getOrNull() ?: break
                    socket.use {
                        val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        while (reader.readLine().orEmpty().isNotEmpty()) {
                            // drain headers
                        }
                        val path = requestLine.split(" ").getOrElse(1) { "/" }
                        val output = it.getOutputStream()
                        when (path) {
                            "/video.m3u8" -> {
                                val body = buildString {
                                    append("#EXTM3U\n")
                                    repeat(delayedSegmentIndex + 1) { index ->
                                        append("#EXTINF:1.0,\n")
                                        append("seg-$index.ts\n")
                                    }
                                    append("#EXT-X-ENDLIST\n")
                                }.toByteArray(Charsets.UTF_8)
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(body)
                                output.flush()
                            }
                            "/seg-$delayedSegmentIndex.ts" -> {
                                val head = ByteArray(8) { 'A'.code.toByte() }
                                val tail = ByteArray(8) { 'B'.code.toByte() }
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${head.size + tail.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(head)
                                output.flush()
                                upstreamHeadSentAt.set(System.nanoTime())
                                Thread.sleep(upstreamCompletionDelayMs)
                                upstreamCompletedAt.set(System.nanoTime())
                                output.write(tail)
                                output.flush()
                                upstreamCompleted.countDown()
                            }
                            else -> {
                                val body = path.removePrefix("/").removeSuffix(".ts").toByteArray(Charsets.UTF_8)
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(body)
                                output.flush()
                            }
                        }
                    }
                }
            }
        }.start()
        val proxy = LocalHlsProxy(
            log = {},
            onPlayRequested = requestedUrls::add,
        )

        proxy.start()
        try {
            val body = "url=${URLEncoder.encode("http://127.0.0.1:${upstream.localPort}/video.m3u8", "UTF-8")}"
            rawHttpRequest(
                proxy.port,
                "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
            val sessionPathRoot = java.net.URI(requestedUrls.single()).path.substringBeforeLast("/")
            val assetPath = "$sessionPathRoot/asset/video-$delayedSegmentIndex.ts"

            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write("GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                val input = socket.getInputStream()
                readHttpHeaders(input)
                val firstBodyByte = input.read()
                val firstBodyByteAt = System.nanoTime()

                assertTrue(firstBodyByte >= 0)
                assertTrue("upstream did not emit the first chunk", upstreamHeadSentAt.get() > 0L)
                assertTrue("upstream did not finish the delayed segment in time", upstreamCompleted.await(5, TimeUnit.SECONDS))
                assertTrue(
                    "forwarded first body byte arrived after upstream completed: first=$firstBodyByteAt head=${upstreamHeadSentAt.get()} complete=${upstreamCompletedAt.get()}",
                    firstBodyByteAt < upstreamCompletedAt.get(),
                )
            }
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    private fun rawHttpRequest(port: Int, request: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            socket.shutdownOutput()
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun rawSegmentedHttpRequest(
        port: Int,
        headers: String,
        firstBodyChunk: String,
        secondBodyChunk: String,
    ): String {
        Socket("127.0.0.1", port).use { socket ->
            val output = socket.getOutputStream()
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.write(firstBodyChunk.toByteArray(Charsets.UTF_8))
            output.flush()
            Thread.sleep(150L)
            output.write(secondBodyChunk.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.shutdownOutput()
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun rawTruncatedHttpRequest(
        port: Int,
        headers: String,
        partialBody: String,
    ): String {
        Socket("127.0.0.1", port).use { socket ->
            val output = socket.getOutputStream()
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.write(partialBody.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.shutdownOutput()
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun singleResponseServer(hits: AtomicInteger, body: String): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                val socket = server.accept()
                socket.use {
                    hits.incrementAndGet()
                    val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: video/mp2t\r\n" +
                        "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n" +
                        body
                    it.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    it.getOutputStream().flush()
                }
            }
        }.start()
        return server
    }

    private fun multiResponseServer(
        responses: Map<String, String>,
        contentTypes: Map<String, String> = emptyMap(),
        responseDelayMs: Map<String, Long> = emptyMap(),
    ): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use {
                        val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        while (reader.readLine().orEmpty().isNotEmpty()) {
                            // drain headers
                        }
                        val path = requestLine.split(" ").getOrElse(1) { "/" }
                        val body = responses[path] ?: "not-found"
                        responseDelayMs[path]?.let(Thread::sleep)
                        val contentType = contentTypes[path]
                            ?: if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t"
                        val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            body
                        it.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                        it.getOutputStream().flush()
                    }
                }
            }
        }.start()
        return server
    }

    private fun readHttpHeaders(input: InputStream) {
        val sentinel = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        var matched = 0
        while (matched < sentinel.size) {
            val next = input.read()
            if (next < 0) throw AssertionError("unexpected eof while reading headers")
            matched = if (next.toByte() == sentinel[matched]) matched + 1 else if (next.toByte() == sentinel[0]) 1 else 0
        }
    }

    private fun eventually(timeoutMs: Long = 1_000L, block: () -> Unit) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var lastFailure: AssertionError? = null
        while (System.nanoTime() < deadline) {
            try {
                block()
                return
            } catch (error: AssertionError) {
                lastFailure = error
                Thread.sleep(25L)
            }
        }
        throw lastFailure ?: AssertionError("eventually block did not succeed within ${timeoutMs}ms")
    }

    private fun productionProxyExecutor(
        maxThreads: Int,
        queueCapacity: Int,
    ): ThreadPoolExecutor {
        val companionField = LocalHlsProxyHost::class.java.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companion = companionField.get(null)
        val method = companion.javaClass.getDeclaredMethod(
            "boundedExecutor\$default",
            companion.javaClass,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Any::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(companion, companion, maxThreads, queueCapacity, false, 4, null) as ThreadPoolExecutor
    }
}
