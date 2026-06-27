package labs.newrapaw.dlna.probe

import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHlsProxyStabilityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun proxyManagementRoutesAddSelectAndDeleteProxySettings() {
        val store = InMemoryProxySettingsStore()
        val proxy = LocalHlsProxy(
            log = {},
            proxySettingsStore = store,
        )

        proxy.start()
        try {
            val encoded = URLEncoder.encode("socks5h://proxy.example:1080", "UTF-8")
            val addResponse = rawHttpRequest(
                proxy.port,
                "POST /control/proxy/add HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${"proxyUrl=$encoded".length}\r\n\r\n" +
                    "proxyUrl=$encoded",
            )

            assertTrue(addResponse.startsWith("HTTP/1.1 200"))
            assertEquals("socks5h-proxy.example-1080", store.load().selectedProxyId)
            assertEquals(1, store.load().proxies.size)

            val selectResponse = rawHttpRequest(
                proxy.port,
                "POST /control/proxy/select HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${"proxyId=direct".length}\r\n\r\n" +
                    "proxyId=direct",
            )

            assertTrue(selectResponse.startsWith("HTTP/1.1 200"))
            assertEquals("direct", store.load().selectedProxyId)

            val deleteBody = "proxyId=socks5h-proxy.example-1080"
            val deleteResponse = rawHttpRequest(
                proxy.port,
                "POST /control/proxy/delete HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${deleteBody.length}\r\n\r\n" +
                    deleteBody,
            )

            assertTrue(deleteResponse.startsWith("HTTP/1.1 200"))
            assertTrue(store.load().proxies.isEmpty())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun segmentRouteCachesUpstreamBytesAcrossRepeatedRequests() {
        val upstreamHits = AtomicInteger(0)
        val upstream = singleResponseServer(upstreamHits, "segment-bytes")
        val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024)
        val proxy = LocalHlsProxy(
            log = {},
            segmentCache = cache,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${upstream.localPort}/seg0.ts"
            val path = "/proxy/segment.ts?u=${encodeProxyUrl(upstreamUrl)}"

            val first = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            val second = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(first.contains("segment-bytes"))
            assertTrue(second.contains("segment-bytes"))
            assertEquals(1, upstreamHits.get())
            assertEquals(1, cache.stats().hits)
            assertEquals(1, cache.stats().misses)
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun cacheClearRouteDeletesCachedSegments() {
        val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024)
        cache.getOrFetch("https://cdn.example/seg.ts") { byteArrayOf(1, 2, 3) }
        val proxy = LocalHlsProxy(
            log = {},
            segmentCache = cache,
        )

        proxy.start()
        try {
            val response = rawHttpRequest(
                proxy.port,
                "POST /control/cache/clear HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n",
            )

            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertEquals(0, cache.stats().entries)
            assertEquals(0, cache.stats().sizeBytes)
        } finally {
            proxy.close()
        }
    }

    @Test
    fun raceModeReturnsFirstSuccessfulProxyResponseWhenDirectIsSlow() {
        val directHits = AtomicInteger(0)
        val directStarted = CountDownLatch(1)
        val direct = delayedResponseServer(directHits, directStarted, "direct-slow", delayMillis = 800)
        val proxyHits = AtomicInteger(0)
        val httpProxy = singleResponseServer(proxyHits, "proxy-fast")
        val proxyConfig = ProxyConfig("p1", ProxyType.HTTP, "127.0.0.1", httpProxy.localPort)
        val store = InMemoryProxySettingsStore(
            ProxySettingsState(
                proxies = listOf(proxyConfig),
                selectedProxyId = proxyConfig.id,
                upstreamMode = UpstreamMode.RACE_DIRECT_AND_PROXY,
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            proxySettingsStore = store,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${direct.localPort}/seg-race.ts"
            val path = "/proxy/segment.ts?u=${encodeProxyUrl(upstreamUrl)}"

            val response = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(response.contains("proxy-fast"))
            assertTrue(directStarted.await(2, TimeUnit.SECONDS))
            assertEquals(1, proxyHits.get())
        } finally {
            proxy.close()
            direct.close()
            httpProxy.close()
        }
    }

    @Test
    fun raceModeAlsoAppliesToManifestRequests() {
        val directHits = AtomicInteger(0)
        val directStarted = CountDownLatch(1)
        val direct = delayedResponseServer(directHits, directStarted, "#EXTM3U\n#EXT-X-ENDLIST", delayMillis = 800)
        val proxyHits = AtomicInteger(0)
        val httpProxy = singleResponseServer(proxyHits, "#EXTM3U\n#EXT-X-ENDLIST")
        val proxyConfig = ProxyConfig("p1", ProxyType.HTTP, "127.0.0.1", httpProxy.localPort)
        val store = InMemoryProxySettingsStore(
            ProxySettingsState(
                proxies = listOf(proxyConfig),
                selectedProxyId = proxyConfig.id,
                upstreamMode = UpstreamMode.RACE_DIRECT_AND_PROXY,
            ),
        )
        val proxy = LocalHlsProxy(
            log = {},
            proxySettingsStore = store,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${direct.localPort}/index.m3u8"
            val path = "/proxy/hls.m3u8?u=${encodeProxyUrl(upstreamUrl)}"

            val response = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(response.contains("#EXTM3U"))
            assertTrue(directStarted.await(2, TimeUnit.SECONDS))
            assertEquals(1, proxyHits.get())
        } finally {
            proxy.close()
            direct.close()
            httpProxy.close()
        }
    }

    @Test
    fun vodManifestStartsSustainedPrefetchSession() {
        val logs = CopyOnWriteArrayList<String>()
        val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024 * 1024)
        val manifestBody = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXTINF:4.0,
            seg-2.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val upstream = multiResponseServer(
            mapOf(
                "/index.m3u8" to manifestBody,
                "/seg-1.ts" to "segment-one",
                "/seg-2.ts" to "segment-two",
            ),
        )
        val proxy = LocalHlsProxy(
            log = logs::add,
            segmentCache = cache,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${upstream.localPort}/index.m3u8"
            val path = "/proxy/hls.m3u8?u=${encodeProxyUrl(upstreamUrl)}"

            val response = rawHttpRequest(proxy.port, "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 200"))
            eventually {
                assertTrue(logs.any { it.contains("VOD prefetch session created") })
                assertTrue(cache.stats().entries >= 2)
            }
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun prefetchConcurrencyRouteAppliesToActiveSession() {
        val logs = CopyOnWriteArrayList<String>()
        val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024 * 1024)
        val store = InMemoryProxySettingsStore(ProxySettingsState(prefetchConcurrency = 1))
        val manifestBody = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXTINF:4.0,
            seg-2.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val upstream = multiResponseServer(
            mapOf(
                "/index.m3u8" to manifestBody,
                "/seg-1.ts" to "segment-one",
                "/seg-2.ts" to "segment-two",
            ),
        )
        val proxy = LocalHlsProxy(
            log = logs::add,
            proxySettingsStore = store,
            segmentCache = cache,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${upstream.localPort}/index.m3u8"
            val manifestPath = "/proxy/hls.m3u8?u=${encodeProxyUrl(upstreamUrl)}"
            rawHttpRequest(proxy.port, "GET $manifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            val body = "prefetchConcurrency=5"
            val updateResponse = rawHttpRequest(
                proxy.port,
                "POST /control/prefetch/config HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${body.length}\r\n\r\n" +
                    body,
            )

            assertTrue(updateResponse.startsWith("HTTP/1.1 200"))
            assertEquals(5, store.load().prefetchConcurrency)
            eventually {
                assertTrue(logs.any { it.contains("VOD prefetch concurrency updated: 5") })
                assertTrue(logs.any { it.contains("Prefetch concurrency updated: 5") })
            }
        } finally {
            proxy.close()
            upstream.close()
        }
    }

    @Test
    fun loggingConfigRoutePersistsDetailedDiagnosticsFlag() {
        val store = InMemoryProxySettingsStore()
        val proxy = LocalHlsProxy(
            log = {},
            proxySettingsStore = store,
        )

        proxy.start()
        try {
            val enableBody = "detailedDiagnosticsEnabled=true"
            val enableResponse = rawHttpRequest(
                proxy.port,
                "POST /control/logging/config HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${enableBody.length}\r\n\r\n" +
                    enableBody,
            )

            assertTrue(enableResponse.startsWith("HTTP/1.1 200"))
            assertTrue(store.load().detailedDiagnosticsEnabled)

            val disableResponse = rawHttpRequest(
                proxy.port,
                "POST /control/logging/config HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: 0\r\n\r\n",
            )

            assertTrue(disableResponse.startsWith("HTTP/1.1 200"))
            assertTrue(!store.load().detailedDiagnosticsEnabled)
        } finally {
            proxy.close()
        }
    }

    @Test
    fun detailedDiagnosticsLogsOnlyAfterLoggingRouteEnablesThem() {
        val logs = CopyOnWriteArrayList<String>()
        val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024 * 1024)
        val store = InMemoryProxySettingsStore()
        val manifestBody = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val upstream = multiResponseServer(
            mapOf(
                "/index.m3u8" to manifestBody,
                "/seg-1.ts" to "segment-one",
            ),
        )
        val proxy = LocalHlsProxy(
            log = logs::add,
            proxySettingsStore = store,
            segmentCache = cache,
        )

        proxy.start()
        try {
            val upstreamUrl = "http://127.0.0.1:${upstream.localPort}/index.m3u8"
            val manifestPath = "/proxy/hls.m3u8?u=${encodeProxyUrl(upstreamUrl)}"
            val segmentPath = "/proxy/segment.ts?u=${encodeProxyUrl("http://127.0.0.1:${upstream.localPort}/seg-1.ts")}"

            rawHttpRequest(proxy.port, "GET $manifestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            rawHttpRequest(proxy.port, "GET $segmentPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(logs.none { it.contains("[diag]") })

            val enableBody = "detailedDiagnosticsEnabled=true"
            rawHttpRequest(
                proxy.port,
                "POST /control/logging/config HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: ${enableBody.length}\r\n\r\n" +
                    enableBody,
            )
            rawHttpRequest(proxy.port, "GET $segmentPath HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            eventually {
                assertTrue(logs.any { it.contains("[diag]") })
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

    private fun delayedResponseServer(
        hits: AtomicInteger,
        started: CountDownLatch,
        body: String,
        delayMillis: Long,
    ): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                val socket = server.accept()
                socket.use {
                    hits.incrementAndGet()
                    started.countDown()
                    Thread.sleep(delayMillis)
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

    private fun multiResponseServer(responses: Map<String, String>): ServerSocket {
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
                        val contentType = if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t"
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

    private fun eventually(timeoutMs: Long = 2000, assertion: () -> Unit) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastError: AssertionError? = null
        while (System.nanoTime() < deadline) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                lastError = error
                Thread.sleep(25)
            }
        }
        throw lastError ?: AssertionError("eventually block did not complete")
    }
}
