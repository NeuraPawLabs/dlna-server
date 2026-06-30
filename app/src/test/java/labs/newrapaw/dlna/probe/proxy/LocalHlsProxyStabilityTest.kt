package labs.newrapaw.dlna.probe.proxy

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.SocketException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            val method = LocalHlsProxy::class.java.getDeclaredMethod("shouldSuppressRequestFailureLog", Throwable::class.java)
            method.isAccessible = true

            val suppressed = method.invoke(proxy, SocketException("Broken pipe")) as Boolean

            assertTrue(suppressed)
            assertTrue(logs.none { it.contains("Broken pipe") })
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
        val forwardingDeadlineMs = 1_700L
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
                                val body = """
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
                                """.trimIndent().toByteArray(Charsets.UTF_8)
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(body)
                                output.flush()
                            }
                            "/seg-0.ts" -> {
                                val body = "segment-0".toByteArray(Charsets.UTF_8)
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(body)
                                output.flush()
                            }
                            "/seg-1.ts", "/seg-2.ts" -> {
                                val body = path.removePrefix("/").removeSuffix(".ts").toByteArray(Charsets.UTF_8)
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(body)
                                output.flush()
                            }
                            "/seg-3.ts" -> {
                                val head = ByteArray(8) { 'A'.code.toByte() }
                                val tail = ByteArray(8) { 'B'.code.toByte() }
                                output.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${head.size + tail.size}\r\nConnection: close\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8),
                                )
                                output.write(head)
                                output.flush()
                                Thread.sleep(upstreamCompletionDelayMs)
                                output.write(tail)
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
            val assetPath = "$sessionPathRoot/asset/video-3.ts"

            val startedAt = System.nanoTime()
            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write("GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                val input = socket.getInputStream()
                readHttpHeaders(input)
                val firstBodyByte = input.read()
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

                assertTrue(firstBodyByte >= 0)
                assertTrue(
                    "forwarded first body byte arrived too late: ${elapsedMs}ms (expected < ${forwardingDeadlineMs}ms before upstream completed in ${upstreamCompletionDelayMs}ms)",
                    elapsedMs < forwardingDeadlineMs,
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
}
