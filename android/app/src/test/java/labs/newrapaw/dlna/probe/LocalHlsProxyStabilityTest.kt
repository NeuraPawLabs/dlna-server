package labs.newrapaw.dlna.probe

import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
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
}
