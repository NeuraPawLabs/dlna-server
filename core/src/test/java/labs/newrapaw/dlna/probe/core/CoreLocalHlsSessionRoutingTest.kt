package labs.newrapaw.dlna.probe.core

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsSessionRoutingTest {
    @Test
    fun cancelledCurrentSessionAssetRequestsReturn410Gone() {
        val sessionAssetRootDir = java.nio.file.Files.createTempDirectory("routing-cancelled-session").toFile()
        val diagnosticsState = PlaybackDiagnosticsState()
        val sessionLocalServer = labs.newrapaw.dlna.probe.core.session.SessionLocalServer()
        val assetStore = labs.newrapaw.dlna.probe.core.session.SessionAssetStore(sessionAssetRootDir)
        val proxyConfig = ProxyConfig(
            id = "http-proxy-127.0.0.1-8888",
            type = ProxyType.HTTP,
            host = "127.0.0.1",
            port = 8888,
        )
        val raceSettingsStore = InMemoryProxySettingsStore(
            ProxySettingsState(
                proxies = listOf(proxyConfig),
                selectedProxyId = proxyConfig.id,
                upstreamMode = UpstreamMode.RACE_DIRECT_AND_PROXY,
            ),
        )
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = CoreLocalHlsUpstreamClient(
                client = okhttp3.OkHttpClient(),
                proxySettingsStore = raceSettingsStore,
                diagnosticsState = diagnosticsState,
                upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
                refreshDiagnosticsSnapshot = {},
                log = { _ -> },
            ),
            refreshDiagnosticsSnapshot = {},
        )
        val session = labs.newrapaw.dlna.probe.core.session.PlaybackSession.create(
            sessionId = "session-cancelled",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = sessionAssetRootDir.resolve("session-cancelled").absolutePath,
        )
        val asset = labs.newrapaw.dlna.probe.core.session.SessionAsset(
            assetId = "video-0",
            kind = labs.newrapaw.dlna.probe.core.session.SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "https://example.com/segment.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val prepared = PreparedSessionPlayback(
            session = session.copy(
                status = labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus.READY,
                timeline = labs.newrapaw.dlna.probe.core.session.SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to SessionAssetRuntime()),
            telemetryBridge = labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge(emptyList()),
            callTracker = labs.newrapaw.dlna.probe.core.session.SessionCallTracker().apply { cancel() },
            prefetchController = labs.newrapaw.dlna.probe.core.session.SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = Executors.newSingleThreadExecutor(),
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )
        val requestHandler = CoreLocalHlsRequestHandler(
            sessionLocalServer = sessionLocalServer,
            diagnosticsState = diagnosticsState,
            sessionAssetLoader = loader,
            sessionAssetStreamer = CoreLocalHlsSessionAssetStreamer(
                proxySettingsStore = raceSettingsStore,
                sessionAssetStore = assetStore,
                diagnosticsState = diagnosticsState,
                upstreamClient = loaderUpstreamClient(diagnosticsState, raceSettingsStore),
                refreshDiagnosticsSnapshot = {},
            ),
            sessionPreparer = CoreLocalHlsSessionPreparer(
                diagnosticsState = diagnosticsState,
                sessionManifestResolver = CoreLocalHlsSessionManifestResolver { "" },
                manifestPlanner = labs.newrapaw.dlna.probe.core.session.ManifestPlanner(),
                preparedSessionBuilder = CoreLocalHlsPreparedSessionBuilder(
                    sessionLocalServer = sessionLocalServer,
                    sessionPrefetchExecutor = Executors.newSingleThreadExecutor(),
                ),
                proxySettingsStore = raceSettingsStore,
                sessionAssetLoader = loader,
                sessionAssetStore = assetStore,
                refreshDiagnosticsSnapshot = {},
                safeLog = { _ -> },
            ),
            getActiveSessionShell = { session },
            isClosedSessionId = { false },
            getActivePreparedSession = { prepared },
            setActivePreparedSession = {},
            updatePlaybackPosition = {},
            refreshPreparedSessionDiagnostics = {},
            shouldSuppressRequestFailureLog = { false },
            safeLog = { _ -> },
        )

        try {
            val response = ByteArrayOutputStream()
            requestHandler.handleSessionRequest(
                method = "GET",
                path = sessionLocalServer.assetPath(session.sessionId, asset),
                output = response,
            )

            val body = response.toString(Charsets.UTF_8.name())
            assertTrue(body.startsWith("HTTP/1.1 410"))
            assertTrue(body.contains("Session Gone"))
        } finally {
            prepared.prefetchController.cancel()
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun closedSessionRequestsReturn410Gone() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newSingleThreadExecutor()
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

        val proxy = CoreLocalHlsProxy(log = {})

        proxy.start()
        try {
            val firstSession = proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")
            proxy.openSession("http://127.0.0.1:${upstream.address.port}/video.m3u8")

            val response = ByteArrayOutputStream()
            proxy.handleSessionRequest(
                method = "GET",
                path = URL(firstSession.localManifestUrl).path,
                output = response,
            )

            val body = response.toString(Charsets.UTF_8.name())
            assertTrue(body.startsWith("HTTP/1.1 410"))
            assertTrue(body.contains("Gone"))
            assertTrue(body.contains("Cache-Control: no-store"))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }

    @Test
    fun multiVariantVideoPlaylistRouteServesRequestedVariantPlaylist() {
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

        val proxy = CoreLocalHlsProxy(log = {})

        proxy.start()
        try {
            val session = proxy.openSession("http://127.0.0.1:${upstream.address.port}/master.m3u8")
            val masterConnection = URL(session.localManifestUrl).openConnection() as HttpURLConnection
            masterConnection.connectTimeout = 5_000
            masterConnection.readTimeout = 5_000
            assertEquals(200, masterConnection.responseCode)
            val localMaster = masterConnection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val variantPath = Regex("""/session/[^"\n]+/video/[^"\n]+\.m3u8""")
                .findAll(localMaster)
                .first()
                .value
            val primaryResponse = ByteArrayOutputStream()
            proxy.handleSessionRequest(
                method = "GET",
                path = URL(session.localManifestUrl).path.replace("manifest.m3u8", "video.m3u8"),
                output = primaryResponse,
            )

            val response = ByteArrayOutputStream()
            proxy.handleSessionRequest(
                method = "GET",
                path = variantPath,
                output = response,
            )

            val body = response.toString(Charsets.UTF_8.name())
            assertTrue(body.startsWith("HTTP/1.1 200"))
            assertTrue(body.contains("application/vnd.apple.mpegurl"))
            assertTrue(body.contains("/session/${session.sessionId}/asset/"))
            assertTrue(body != primaryResponse.toString(Charsets.UTF_8.name()))
        } finally {
            proxy.close()
            upstream.stop(0)
        }
    }
}

private fun loaderUpstreamClient(
    diagnosticsState: PlaybackDiagnosticsState,
    proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
): CoreLocalHlsUpstreamClient =
    CoreLocalHlsUpstreamClient(
        client = okhttp3.OkHttpClient(),
        proxySettingsStore = proxySettingsStore,
        diagnosticsState = diagnosticsState,
        upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
        refreshDiagnosticsSnapshot = {},
        log = { _ -> },
    )
