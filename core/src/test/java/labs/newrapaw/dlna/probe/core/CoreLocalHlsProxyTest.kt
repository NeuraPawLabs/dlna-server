package labs.newrapaw.dlna.probe.core

import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer

class CoreLocalHlsProxyTest {
    @Test
    fun componentsDoNotCreateProxyServerWhenHttpServingIsDisabled() {
        val components = CoreLocalHlsProxyComponents(
            client = OkHttpClient(),
            proxySettingsStore = InMemoryProxySettingsStore(),
            sessionAssetRootDir = Files.createTempDirectory("core-proxy-components").toFile(),
            serveHttp = false,
            safeLog = {},
            shouldSuppressRequestFailureLog = { false },
        )

        try {
            assertNull(components.proxyServer)
        } finally {
            components.close()
        }
    }

    @Test
    fun connectionResetByPeerIsNotLoggedAsRequestFailure() {
        val suppressed = shouldSuppressRequestFailureLog(SocketException("Connection reset by peer"))

        assertTrue(suppressed)
    }

    @Test
    fun brokenPipeIsNotLoggedAsRequestFailure() {
        val suppressed = shouldSuppressRequestFailureLog(SocketException("Broken pipe"))

        assertTrue(suppressed)
    }

    @Test
    fun boundedExecutorUsesAbortPolicyByDefault() {
        val executor = boundedExecutor(
            maxThreads = 2,
            queueCapacity = 3,
        )

        try {
            assertEquals(2, executor.corePoolSize)
            assertEquals(2, executor.maximumPoolSize)
            assertEquals(3, executor.queue.remainingCapacity() + executor.queue.size)
            assertTrue(executor.allowsCoreThreadTimeOut())
            assertTrue(executor.rejectedExecutionHandler is ThreadPoolExecutor.AbortPolicy)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun boundedExecutorCanUseCallerRunsPolicyWhenRequested() {
        val executor = boundedExecutor(
            maxThreads = 2,
            queueCapacity = 3,
            callerRunsOnSaturation = true,
        )

        try {
            assertTrue(executor.rejectedExecutionHandler is ThreadPoolExecutor.CallerRunsPolicy)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun embeddedProxyCanServeSessionRoutesWithoutStartingCoreHttpServer() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:4.0,
                seg-1.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/seg-1.ts") { exchange ->
            val body = "segment-one".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            serveHttp = false,
        )

        proxy.start()
        try {
            val session = proxy.openSession(
                sourceUrl = "http://127.0.0.1:${upstream.address.port}/video.m3u8",
                localBaseUrl = "http://127.0.0.1:4321",
            )
            val response = ByteArrayOutputStream()

            val handled = proxy.handleSessionRequest(
                method = "GET",
                path = java.net.URI(session.localManifestUrl).path,
                output = response,
            )

            assertTrue(handled)
            assertEquals(0, proxy.port)
            assertTrue(session.localManifestUrl.startsWith("http://127.0.0.1:4321/"))
            assertTrue(response.toString(Charsets.UTF_8.name()).startsWith("HTTP/1.1 200"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun proxyStartsAndExposesBaseUrl() {
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            assertTrue(proxy.baseUrl.startsWith("http://127.0.0.1:"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun openSessionReturnsActiveSessionShellInfoBeforePreparation() {
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("https://example.com/video.m3u8")

            assertEquals("https://example.com/video.m3u8", session.sourceUrl)
            assertEquals("PREPARING", session.status.name)
            assertFalse(session.prepared)
            assertTrue(session.localManifestUrl.contains("/session/${session.sessionId}/manifest.m3u8"))
        } finally {
            proxy.close()
        }
    }

    @Test
    fun multiVariantMasterManifestPreservesMultipleLocalVideoVariants() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/master.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=800000
                low/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=1600000
                high/index.m3u8
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/low/index.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:4.0,
                low-1.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/high/index.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:4.0,
                high-1.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/low/low-1.ts") { exchange ->
            val body = "low-segment".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/high/high-1.ts") { exchange ->
            val body = "high-segment".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/master.m3u8")
            val masterResponse = URL(session.localManifestUrl).openConnection() as HttpURLConnection
            masterResponse.connectTimeout = 5_000
            masterResponse.readTimeout = 5_000

            assertEquals(200, masterResponse.responseCode)
            val localMaster = masterResponse.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val localVariantPaths = Regex("""/session/[^"\n]+/video/[^"\n]+\.m3u8""")
                .findAll(localMaster)
                .map { it.value }
                .toList()

            assertEquals(2, localVariantPaths.size)
            assertTrue(localVariantPaths.distinct().size == 2)
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun requestedAssetCanCompleteEvenWhenPrefetchHasNotReachedItYet() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val playlist = buildString {
            append("#EXTM3U\n")
            repeat(5) { index ->
                append("#EXTINF:1.0,\n")
                append("/segment-$index.ts\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = playlist.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        repeat(5) { index ->
            upstream.createContext("/segment-$index.ts") { exchange ->
                Thread.sleep(350L)
                val body = "segment-$index".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-3.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000

            assertEquals(200, response.responseCode)
            assertArrayEquals("segment-3".toByteArray(), response.inputStream.use { it.readBytes() })
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun requestedAssetDoesNotReturn504WhenSegmentNeedsMoreThanLegacyWaitBudget() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val playlist = buildString {
            append("#EXTM3U\n")
            repeat(6) { index ->
                append("#EXTINF:1.0,\n")
                append("/segment-$index.ts\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = playlist.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        repeat(6) { index ->
            upstream.createContext("/segment-$index.ts") { exchange ->
                if (index == 5) {
                    Thread.sleep(1_200L)
                } else {
                    Thread.sleep(50L)
                }
                val body = "segment-$index".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-5.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000

            assertEquals(200, response.responseCode)
            assertArrayEquals("segment-5".toByteArray(), response.inputStream.use { it.readBytes() })
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun firstRequestedAssetDoesNotWaitForEntireStartupWindow() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val playlist = buildString {
            append("#EXTM3U\n")
            repeat(6) { index ->
                append("#EXTINF:1.0,\n")
                append("/segment-$index.ts\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = playlist.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        repeat(6) { index ->
            upstream.createContext("/segment-$index.ts") { exchange ->
                if (index < 4) {
                    Thread.sleep(1_200L)
                } else {
                    Thread.sleep(50L)
                }
                val body = "segment-$index".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 3),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val startedAt = System.nanoTime()
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-0.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 10_000
            response.readTimeout = 10_000

            assertEquals(200, response.responseCode)
            assertArrayEquals("segment-0".toByteArray(), response.inputStream.use { it.readBytes() })
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("first asset should not wait for all startup segments, elapsed=${elapsedMs}ms", elapsedMs < 2_500L)
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun assetRequestReprioritizesPendingPrefetchAroundRequestedSlot() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val playlist = buildString {
            append("#EXTM3U\n")
            repeat(8) { index ->
                append("#EXTINF:1.0,\n")
                append("/segment-$index.ts\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = playlist.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        repeat(8) { index ->
            upstream.createContext("/segment-$index.ts") { exchange ->
                Thread.sleep(250L)
                val body = "segment-$index".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-5.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000
            assertEquals(200, response.responseCode)
            response.inputStream.use { it.readBytes() }

            eventually(emptyList()) {
                val pending = proxy.activeSessionInfo()?.pendingPrefetchAssetIds.orEmpty()
                assertFalse(
                    "pending queue should not keep stale assets before requested slot: $pending",
                    pending.any { assetId ->
                        assetId.removePrefix("video-").toIntOrNull()?.let { it < 5 } == true
                    },
                )
            }
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun requestedAssetDoesNotEmitDetailedDiagnosticsLogs() {
        val logs = CopyOnWriteArrayList<String>()
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXTINF:1.0,
                /segment-1.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-1.ts") { exchange ->
            val head = ByteArray(8) { 'A'.code.toByte() }
            val tail = ByteArray(8) { 'B'.code.toByte() }
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, (head.size + tail.size).toLong())
            exchange.responseBody.use {
                it.write(head)
                it.flush()
                Thread.sleep(1_500L)
                it.write(tail)
                it.flush()
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = logs::add,
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-0.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000
            assertEquals(200, response.responseCode)
            response.inputStream.use { it.readBytes() }

            assertFalse(logs.joinToString("\n"), logs.any { it.startsWith("[diag]") })
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun requestedAssetStartsStreamingBeforeUpstreamSegmentFullyCompletes() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val head = ByteArray(8) { 'A'.code.toByte() }
            val tail = ByteArray(8) { 'B'.code.toByte() }
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, (head.size + tail.size).toLong())
            exchange.responseBody.use {
                it.write(head)
                it.flush()
                Thread.sleep(1_500L)
                it.write(tail)
                it.flush()
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val assetPath = "${java.net.URI(session.localManifestUrl).path.substringBeforeLast("/")}/asset/video-1.ts"
            val startedAt = System.nanoTime()
            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write("GET $assetPath HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                val input = socket.getInputStream()
                this@CoreLocalHlsProxyTest.readHttpHeaders(input)
                val firstBodyByte = input.read()
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

                assertTrue("expected streamed body byte", firstBodyByte >= 0)
                assertTrue("first body byte arrived too late: ${elapsedMs}ms", elapsedMs < 1_000L)
            }
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun concurrentSessionRequestsPreparePlaybackOnlyOnce() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val manifestRequests = AtomicInteger(0)
        upstream.createContext("/video.m3u8") { exchange ->
            manifestRequests.incrementAndGet()
            Thread.sleep(400L)
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val requestPool = Executors.newFixedThreadPool(2)
            val failures = CopyOnWriteArrayList<Throwable>()
            val sessionRoot = session.localManifestUrl.substringBeforeLast("/")
            val urls = listOf(
                session.localManifestUrl,
                "$sessionRoot/video.m3u8",
            )
            urls.forEach { url ->
                requestPool.execute {
                    try {
                        start.await(5, TimeUnit.SECONDS)
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        assertEquals(200, connection.responseCode)
                        connection.inputStream.use { it.readBytes() }
                    } catch (error: Throwable) {
                        failures += error
                    } finally {
                        done.countDown()
                    }
                }
            }

            start.countDown()
            assertTrue("concurrent requests did not finish", done.await(10, TimeUnit.SECONDS))
            requestPool.shutdownNow()
            if (failures.isNotEmpty()) {
                throw AssertionError(failures.joinToString("\n") { "${it::class.java.simpleName}: ${it.message}" })
            }

            assertEquals("session preparation should fetch upstream manifest once", 1, manifestRequests.get())
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun openingNewSessionCancelsInFlightPrefetchDownload() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val segmentStarted = CountDownLatch(1)
        val allowStreaming = CountDownLatch(1)
        val sawAbort = AtomicBoolean(false)
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            segmentStarted.countDown()
            allowStreaming.await(5, TimeUnit.SECONDS)
            val chunk = ByteArray(8 * 1024) { 'A'.code.toByte() }
            val totalChunks = 64
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, (chunk.size * totalChunks).toLong())
            runCatching {
                exchange.responseBody.use { output ->
                    repeat(totalChunks) {
                        output.write(chunk)
                        output.flush()
                        Thread.sleep(50L)
                    }
                }
            }.onFailure {
                sawAbort.set(true)
            }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val firstSession = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val firstManifest = URL(firstSession.localManifestUrl).openConnection() as HttpURLConnection
            firstManifest.connectTimeout = 5_000
            firstManifest.readTimeout = 5_000
            assertEquals(200, firstManifest.responseCode)
            firstManifest.inputStream.use { it.readBytes() }
            assertTrue("prefetch did not start segment download", segmentStarted.await(5, TimeUnit.SECONDS))

            proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            allowStreaming.countDown()

            eventually(emptyList(), timeoutMs = 4_000L) {
                assertTrue("old session download should be aborted after session switch", sawAbort.get())
            }
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun diagnosticsSnapshotStaysLightweightDuringPlayback() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-0.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000
            assertEquals(200, response.responseCode)
            response.inputStream.use { it.readBytes() }

            eventually(emptyList()) {
                val snapshot = proxy.diagnosticsSnapshot()
                assertTrue(snapshot.slotStates.isEmpty())
                assertTrue(snapshot.assetDiagnostics.isEmpty())
                assertEquals(1, snapshot.sessionReadyAssetCount)
                assertEquals(1, snapshot.sessionTotalAssetCount)
                assertTrue(snapshot.sessionReadyBytes > 0L)
            }
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun seekRequestCancelsStalePrefetchSoTargetSegmentIsNotQueuedBehindOldDownloads() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        val slowStartupStarted = CountDownLatch(3)
        upstream.createContext("/video.m3u8") { exchange ->
            val body = buildString {
                append("#EXTM3U\n")
                repeat(12) { index ->
                    append("#EXTINF:1.0,\n")
                    append("/segment-$index.ts\n")
                }
                append("#EXT-X-ENDLIST\n")
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        repeat(12) { index ->
            upstream.createContext("/segment-$index.ts") { exchange ->
                if (index in 0..2) {
                    slowStartupStarted.countDown()
                    Thread.sleep(1_500L)
                } else {
                    Thread.sleep(50L)
                }
                val body = "segment-$index".toByteArray()
                exchange.responseHeaders.add("Content-Type", "video/mp2t")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        upstream.start()

        val dispatcher = Dispatcher().apply {
            maxRequests = 3
            maxRequestsPerHost = 3
        }
        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient.Builder().dispatcher(dispatcher).build(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 3),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val manifest = URL(session.localManifestUrl).openConnection() as HttpURLConnection
            manifest.connectTimeout = 5_000
            manifest.readTimeout = 5_000
            assertEquals(200, manifest.responseCode)
            manifest.inputStream.use { it.readBytes() }
            assertTrue("startup prefetch did not begin", slowStartupStarted.await(5, TimeUnit.SECONDS))

            val startedAt = System.nanoTime()
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-10.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000
            assertEquals(200, response.responseCode)
            assertArrayEquals("segment-10".toByteArray(), response.inputStream.use { it.readBytes() })
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

            assertTrue("seek target should not stay blocked behind stale prefetch, elapsed=${elapsedMs}ms", elapsedMs < 1_000L)
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun clearActiveSessionCacheResetsPreparedSessionDiagnostics() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:4.0,
                segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            proxySettingsStore = InMemoryProxySettingsStore(
                ProxySettingsState(prefetchConcurrency = 1),
            ),
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val response = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-0.ts").openConnection() as HttpURLConnection
            response.connectTimeout = 5_000
            response.readTimeout = 5_000
            assertEquals(200, response.responseCode)
            response.inputStream.use { it.readBytes() }

            eventually(emptyList()) {
                val snapshot = proxy.diagnosticsSnapshot()
                assertEquals(1, snapshot.sessionReadyAssetCount)
                assertEquals(1, snapshot.sessionTotalAssetCount)
                assertTrue(snapshot.sessionReadyBytes > 0L)
            }

            proxy.clearActiveSessionCache()

            val cleared = proxy.diagnosticsSnapshot()
            assertEquals(0, cleared.sessionReadyAssetCount)
            assertEquals(0, cleared.sessionTotalAssetCount)
            assertEquals(0L, cleared.sessionReadyBytes)
            assertEquals(0, cleared.pendingPrefetchCount)
            assertEquals(0, cleared.inFlightCount)
            assertEquals(null, cleared.currentLoadingAssetId)
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun truncatedHttpHeadersReturn400InsteadOfServingSessionRoute() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val requestPath = java.net.URI(session.localManifestUrl).path

            val responseText = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write("GET $requestPath HTTP/1.1\r\nHost: 127.0.0.1\r\n".toByteArray())
                output.flush()
                socket.shutdownOutput()
                socket.getInputStream().use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            }

            assertTrue(responseText.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun malformedHttpHeaderLineReturns400InsteadOfServingSessionRoute() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val requestPath = java.net.URI(session.localManifestUrl).path

            val responseText = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write(
                    "GET $requestPath HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Bad-Header\r\n\r\n".toByteArray(),
                )
                output.flush()
                socket.shutdownOutput()
                socket.getInputStream().use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            }

            assertTrue(responseText.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun malformedRequestLineReturns400InsteadOfServingSessionRoute() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val requestPath = java.net.URI(session.localManifestUrl).path

            val responseText = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write("GET $requestPath\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                output.flush()
                socket.shutdownOutput()
                socket.getInputStream().use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            }

            assertTrue(responseText.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun duplicateContentLengthReturns400InsteadOfServingSessionRoute() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val requestPath = java.net.URI(session.localManifestUrl).path

            val responseText = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write(
                    (
                        "GET $requestPath HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\n" +
                            "Content-Length: nope\r\n" +
                            "Content-Length: 0\r\n\r\n"
                        ).toByteArray(),
                )
                output.flush()
                socket.shutdownOutput()
                socket.getInputStream().use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            }

            assertTrue(responseText.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun invalidContentLengthReturns400InsteadOfServingSessionRoute() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val requestPath = java.net.URI(session.localManifestUrl).path

            val responseText = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 5_000
                val output = socket.getOutputStream()
                output.write(
                    (
                        "GET $requestPath HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\n" +
                            "Content-Length: nope\r\n\r\n"
                        ).toByteArray(),
                )
                output.flush()
                socket.shutdownOutput()
                socket.getInputStream().use { it.readBytes() }.toString(Charsets.ISO_8859_1)
            }

            assertTrue(responseText.startsWith("HTTP/1.1 400"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun headRequestForSessionAssetReturnsHeadersWithoutBody() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/video.m3u8") { exchange ->
            val body = """
                #EXTM3U
                #EXTINF:1.0,
                /segment-0.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.createContext("/segment-0.ts") { exchange ->
            val body = "segment-0".toByteArray()
            exchange.responseHeaders.add("Content-Type", "video/mp2t")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("core-proxy-test").toFile()
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
            sessionAssetRootDir = sessionAssetRootDir,
        )

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            val getResponse = URL("${session.localManifestUrl.substringBeforeLast("/")}/asset/video-0.ts").openConnection() as HttpURLConnection
            getResponse.connectTimeout = 5_000
            getResponse.readTimeout = 5_000
            assertEquals(200, getResponse.responseCode)
            assertArrayEquals("segment-0".toByteArray(), getResponse.inputStream.use { it.readBytes() })

            val socket = Socket("127.0.0.1", proxy.port)
            socket.soTimeout = 5_000
            val output = socket.getOutputStream()
            output.write(
                (
                    "HEAD /session/${session.sessionId}/asset/video-0.ts HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Connection: close\r\n\r\n"
                    ).toByteArray(),
            )
            output.flush()
            socket.shutdownOutput()
            val responseBytes = socket.getInputStream().use { it.readBytes() }
            socket.close()
            val responseText = responseBytes.toString(Charsets.ISO_8859_1)

            assertTrue(responseText.startsWith("HTTP/1.1 200 OK"))
            assertTrue(responseText.contains("Content-Length: 9"))
            assertTrue(responseText.endsWith("\r\n\r\n"))
        } finally {
            proxy.close()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }
    }

    private fun eventually(logs: List<String>, timeoutMs: Long = 1_000L, block: () -> Unit) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var last: AssertionError? = null
        while (System.nanoTime() < deadline) {
            try {
                block()
                return
            } catch (error: AssertionError) {
                last = error
                Thread.sleep(25L)
            }
        }
        throw last ?: AssertionError(logs.joinToString("\n"))
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
