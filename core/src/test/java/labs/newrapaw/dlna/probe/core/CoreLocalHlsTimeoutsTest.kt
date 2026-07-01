package labs.newrapaw.dlna.probe.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer
import labs.newrapaw.dlna.probe.core.session.SessionPrefetchController
import labs.newrapaw.dlna.probe.core.session.SessionTimeline
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsTimeoutsTest {
    @Test
    fun requestHandlerSetsSocketReadTimeoutBeforeReadingRequest() {
        val sessionAssetStore = SessionAssetStore(Files.createTempDirectory("timeout-handler").toFile())
        val diagnosticsState = PlaybackDiagnosticsState()
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = sessionAssetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = failingUpstreamClient(),
            refreshDiagnosticsSnapshot = {},
        )
        val socket = RecordingSocket("GET /missing HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
        val requestHandler = CoreLocalHlsRequestHandler(
            sessionLocalServer = SessionLocalServer(),
            diagnosticsState = diagnosticsState,
            sessionAssetLoader = loader,
            sessionAssetStreamer = CoreLocalHlsSessionAssetStreamer(
                proxySettingsStore = InMemoryProxySettingsStore(),
                sessionAssetStore = sessionAssetStore,
                diagnosticsState = diagnosticsState,
                upstreamClient = failingUpstreamClient(),
                refreshDiagnosticsSnapshot = {},
            ),
            sessionPreparer = CoreLocalHlsSessionPreparer(
                diagnosticsState = diagnosticsState,
                sessionManifestResolver = CoreLocalHlsSessionManifestResolver { "" },
                manifestPlanner = labs.newrapaw.dlna.probe.core.session.ManifestPlanner(),
                preparedSessionBuilder = CoreLocalHlsPreparedSessionBuilder(
                    sessionLocalServer = SessionLocalServer(),
                    sessionPrefetchExecutor = Executors.newSingleThreadExecutor(),
                ),
                proxySettingsStore = InMemoryProxySettingsStore(),
                sessionAssetLoader = loader,
                sessionAssetStore = sessionAssetStore,
                refreshDiagnosticsSnapshot = {},
                safeLog = { _ -> },
            ),
            getActiveSessionShell = { null },
            isClosedSessionId = { false },
            getActivePreparedSession = { null },
            setActivePreparedSession = {},
            updatePlaybackPosition = {},
            refreshPreparedSessionDiagnostics = {},
            shouldSuppressRequestFailureLog = { false },
            safeLog = { _ -> },
        )

        requestHandler.handle(socket)

        assertEquals(CoreLocalHlsRequestHandler.DEFAULT_SOCKET_TIMEOUT_MS, socket.recordedSoTimeout)
    }

    @Test
    fun upstreamRaceTimesOutAndCancelsBothCalls() {
        val executor = Executors.newFixedThreadPool(2)
        val directCancelled = AtomicBoolean(false)
        val proxyCancelled = AtomicBoolean(false)
        val directCall = FakeCall("https://example.com/direct.m3u8", directCancelled)
        val proxyCall = FakeCall("https://example.com/proxy.m3u8", proxyCancelled)
        val raceClient = CoreLocalHlsUpstreamRaceClient(
            upstreamRaceExecutor = executor,
            raceTimeoutMs = 100L,
        )

        try {
            val failure = runCatching {
                raceClient.race(
                    directCall = directCall,
                    proxyCall = proxyCall,
                ) { _, _ ->
                    Thread.sleep(500L)
                    UpstreamFetchResult(
                        source = "direct",
                        bytes = byteArrayOf(1),
                        firstByteMs = 0L,
                        completeMs = 500L,
                    )
                }
            }.exceptionOrNull()

            assertTrue(failure is UpstreamFetchException)
            assertTrue(failure?.message.orEmpty().contains("timeout", ignoreCase = true))
            assertTrue(directCancelled.get())
            assertTrue(proxyCancelled.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun waitForSessionAssetReturnsNullAfterTimeoutWhenDownloadNeverCompletes() {
        val sessionAssetRootDir = Files.createTempDirectory("timeout-wait").toFile()
        val assetStore = SessionAssetStore(sessionAssetRootDir)
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = assetStore,
            diagnosticsState = PlaybackDiagnosticsState(),
            upstreamClient = failingUpstreamClient(),
            refreshDiagnosticsSnapshot = {},
            assetWaitTimeoutMs = 100L,
        )
        val asset = SessionAsset(
            assetId = "video-0",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "https://example.com/segment-0.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val runtime = SessionAssetRuntime(state = labs.newrapaw.dlna.probe.core.session.SessionAssetState.DOWNLOADING)
        val prepared = PreparedSessionPlayback(
            session = PlaybackSession.create(
                sessionId = "session-timeout",
                sourceUrl = "https://example.com/video.m3u8",
                entryManifestUrl = "https://example.com/video.m3u8",
                localRootDir = sessionAssetRootDir.resolve("session-timeout").absolutePath,
            ).copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to runtime),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = prefetchExecutor,
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )

        val startedAt = System.nanoTime()
        val bytes = try {
            loader.waitForSessionAsset(prepared, asset)
        } finally {
            prepared.prefetchController.cancel()
            prefetchExecutor.shutdownNow()
            sessionAssetRootDir.deleteRecursively()
        }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertNull(bytes)
        assertTrue("wait should time out near configured budget, elapsed=${elapsedMs}ms", elapsedMs in 80L..800L)
    }

    @Test
    fun interruptedRetryBackoffDoesNotLeaveAssetMarkedAsDownloading() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/segment.ts") { exchange ->
            exchange.sendResponseHeaders(500, 0)
            exchange.responseBody.close()
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("timeout-interrupt").toFile()
        val assetStore = SessionAssetStore(sessionAssetRootDir)
        val diagnosticsState = PlaybackDiagnosticsState()
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = CoreLocalHlsUpstreamClient(
                client = okhttp3.OkHttpClient(),
                proxySettingsStore = InMemoryProxySettingsStore(),
                diagnosticsState = diagnosticsState,
                upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
                refreshDiagnosticsSnapshot = {},
                log = { _ -> },
            ),
            refreshDiagnosticsSnapshot = {},
        )
        val asset = SessionAsset(
            assetId = "video-0",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "http://127.0.0.1:${upstream.address.port}/segment.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val runtime = SessionAssetRuntime()
        val prepared = PreparedSessionPlayback(
            session = PlaybackSession.create(
                sessionId = "session-interrupt",
                sourceUrl = "https://example.com/video.m3u8",
                entryManifestUrl = "https://example.com/video.m3u8",
                localRootDir = sessionAssetRootDir.resolve("session-interrupt").absolutePath,
            ).copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to runtime),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = Executors.newSingleThreadExecutor(),
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )

        val failure = try {
            Thread.currentThread().interrupt()
            runCatching {
                loader.loadSessionAsset(prepared, asset)
            }.exceptionOrNull()
        } finally {
            Thread.interrupted()
            prepared.prefetchController.cancel()
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }

        assertTrue(failure is InterruptedException)
        assertFalse(runtime.state == labs.newrapaw.dlna.probe.core.session.SessionAssetState.DOWNLOADING)
        assertEquals(null, diagnosticsState.snapshot().currentLoadingAssetId)
    }

    @Test
    fun interruptedWaitForInFlightAssetReturnsPromptlyAndPreservesInterrupt() {
        val sessionAssetRootDir = Files.createTempDirectory("timeout-wait-interrupt").toFile()
        val assetStore = SessionAssetStore(sessionAssetRootDir)
        val diagnosticsState = PlaybackDiagnosticsState()
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = failingUpstreamClient(),
            refreshDiagnosticsSnapshot = {},
            assetWaitTimeoutMs = 1_000L,
        )
        val asset = SessionAsset(
            assetId = "video-0",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "https://example.com/segment-0.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val runtime = SessionAssetRuntime(state = labs.newrapaw.dlna.probe.core.session.SessionAssetState.DOWNLOADING)
        val prepared = PreparedSessionPlayback(
            session = PlaybackSession.create(
                sessionId = "session-wait-interrupt",
                sourceUrl = "https://example.com/video.m3u8",
                entryManifestUrl = "https://example.com/video.m3u8",
                localRootDir = sessionAssetRootDir.resolve("session-wait-interrupt").absolutePath,
            ).copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to runtime),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = prefetchExecutor,
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )

        val startedAt = System.nanoTime()
        val bytes: ByteArray?
        val interruptedAfterCall: Boolean
        try {
            Thread.currentThread().interrupt()
            bytes = loader.waitForSessionAsset(prepared, asset)
            interruptedAfterCall = Thread.currentThread().isInterrupted
        } finally {
            Thread.interrupted()
            prepared.prefetchController.cancel()
            prefetchExecutor.shutdownNow()
            sessionAssetRootDir.deleteRecursively()
        }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertNull(bytes)
        assertTrue("interrupt should short-circuit wait, elapsed=${elapsedMs}ms", elapsedMs < 300L)
        assertTrue(interruptedAfterCall)
    }

    @Test
    fun clientDisconnectDuringStreamingDoesNotMarkAssetPermanentlyFailed() {
        val upstream = HttpServer.create(InetSocketAddress(0), 0).apply {
            executor = Executors.newCachedThreadPool()
        }
        upstream.createContext("/segment.ts") { exchange ->
            val bytes = "segment-payload".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()

        val sessionAssetRootDir = Files.createTempDirectory("timeout-stream-disconnect").toFile()
        val assetStore = SessionAssetStore(sessionAssetRootDir)
        val diagnosticsState = PlaybackDiagnosticsState()
        val client = CoreLocalHlsUpstreamClient(
            client = okhttp3.OkHttpClient(),
            proxySettingsStore = InMemoryProxySettingsStore(),
            diagnosticsState = diagnosticsState,
            upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
            refreshDiagnosticsSnapshot = {},
            log = { _ -> },
        )
        val streamer = CoreLocalHlsSessionAssetStreamer(
            proxySettingsStore = InMemoryProxySettingsStore(),
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = client,
            refreshDiagnosticsSnapshot = {},
        )
        val loader = CoreLocalHlsSessionAssetLoader(
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = client,
            refreshDiagnosticsSnapshot = {},
        )
        val asset = SessionAsset(
            assetId = "video-0",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "http://127.0.0.1:${upstream.address.port}/segment.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val runtime = SessionAssetRuntime()
        val prepared = PreparedSessionPlayback(
            session = PlaybackSession.create(
                sessionId = "session-stream-disconnect",
                sourceUrl = "https://example.com/video.m3u8",
                entryManifestUrl = "https://example.com/video.m3u8",
                localRootDir = sessionAssetRootDir.resolve("session-stream-disconnect").absolutePath,
            ).copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to runtime),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = Executors.newSingleThreadExecutor(),
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )

        val firstResult = try {
            streamer.tryStreamSessionAsset(
                output = FailAfterHeaderOutputStream(ByteArrayOutputStream()),
                prepared = prepared,
                asset = asset,
            )
        } finally {
            prepared.prefetchController.cancel()
        }

        val retryBytes = try {
            loader.waitForSessionAsset(prepared, asset)
        } finally {
            upstream.stop(0)
            sessionAssetRootDir.deleteRecursively()
        }

        assertTrue(firstResult)
        assertFalse(runtime.state == labs.newrapaw.dlna.probe.core.session.SessionAssetState.FAILED)
        assertEquals("segment-payload", retryBytes?.toString(Charsets.UTF_8))
        assertEquals(null, diagnosticsState.snapshot().currentLoadingAssetId)
        assertEquals(0, diagnosticsState.snapshot().lastTwentyFailureCount)
        assertEquals(0, diagnosticsState.snapshot().fallbackCount)
        assertEquals(null, diagnosticsState.snapshot().lastFailedSegment)
        assertEquals(null, diagnosticsState.snapshot().lastError)
    }

    @Test
    fun cancelledSessionBeforeStreamingDoesNotLeaveAssetMarkedAsDownloading() {
        val sessionAssetRootDir = Files.createTempDirectory("timeout-stream-cancelled").toFile()
        val assetStore = SessionAssetStore(sessionAssetRootDir)
        val diagnosticsState = PlaybackDiagnosticsState()
        val client = CoreLocalHlsUpstreamClient(
            client = okhttp3.OkHttpClient(),
            proxySettingsStore = InMemoryProxySettingsStore(),
            diagnosticsState = diagnosticsState,
            upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
            refreshDiagnosticsSnapshot = {},
            log = { _ -> },
        )
        val streamer = CoreLocalHlsSessionAssetStreamer(
            proxySettingsStore = InMemoryProxySettingsStore(),
            sessionAssetStore = assetStore,
            diagnosticsState = diagnosticsState,
            upstreamClient = client,
            refreshDiagnosticsSnapshot = {},
        )
        val asset = SessionAsset(
            assetId = "video-0",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            trackId = null,
            url = "https://example.com/segment.ts",
            durationMs = 1_000L,
            sequence = 0,
            blocking = false,
            requiredForStartup = false,
            localPath = null,
        )
        val runtime = SessionAssetRuntime()
        val prepared = PreparedSessionPlayback(
            session = PlaybackSession.create(
                sessionId = "session-stream-cancelled",
                sourceUrl = "https://example.com/video.m3u8",
                entryManifestUrl = "https://example.com/video.m3u8",
                localRootDir = sessionAssetRootDir.resolve("session-stream-cancelled").absolutePath,
            ).copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), listOf(asset)),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = mapOf(asset.assetId to asset),
            assetRuntime = mutableMapOf(asset.assetId to runtime),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker().apply { cancel() },
            prefetchController = SessionPrefetchController(
                queue = java.util.ArrayDeque(),
                executor = Executors.newSingleThreadExecutor(),
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )
        val output = ByteArrayOutputStream()

        val failure = try {
            runCatching {
                streamer.tryStreamSessionAsset(
                    output = output,
                    prepared = prepared,
                    asset = asset,
                )
            }.exceptionOrNull()
        } finally {
            prepared.prefetchController.cancel()
            sessionAssetRootDir.deleteRecursively()
        }

        assertEquals(null, failure)
        assertTrue(output.toString(Charsets.UTF_8.name()).startsWith("HTTP/1.1 410"))
        assertFalse(runtime.state == labs.newrapaw.dlna.probe.core.session.SessionAssetState.DOWNLOADING)
        assertEquals(null, diagnosticsState.snapshot().currentLoadingAssetId)
    }

    private fun failingUpstreamClient(): CoreLocalHlsUpstreamClient =
        CoreLocalHlsUpstreamClient(
            client = okhttp3.OkHttpClient(),
            proxySettingsStore = InMemoryProxySettingsStore(),
            diagnosticsState = PlaybackDiagnosticsState(),
            upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(Executors.newSingleThreadExecutor()),
            refreshDiagnosticsSnapshot = {},
            log = { _ -> },
        )
}

private class RecordingSocket(
    request: String,
) : Socket() {
    private val input = ByteArrayInputStream(request.toByteArray(Charsets.UTF_8))
    private val output = ByteArrayOutputStream()
    var recordedSoTimeout: Int = 0
        private set

    override fun setSoTimeout(timeout: Int) {
        recordedSoTimeout = timeout
    }

    override fun getInputStream() = input

    override fun getOutputStream() = output

    override fun close() = Unit
}

private class FailAfterHeaderOutputStream(
    delegate: ByteArrayOutputStream,
) : FilterOutputStream(delegate) {
    private var headerTerminatorSeen = false

    override fun write(b: Int) {
        if (headerTerminatorSeen) {
            throw IOException("client disconnected")
        }
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (headerTerminatorSeen) {
            throw IOException("client disconnected")
        }
        super.write(b, off, len)
        val written = String(b, off, len, Charsets.ISO_8859_1)
        if (written.contains("\r\n\r\n")) {
            headerTerminatorSeen = true
        }
    }
}

private class FakeCall(
    private val requestUrl: String,
    private val cancelled: AtomicBoolean,
) : Call {
    override fun request(): Request = Request.Builder().url(requestUrl).build()

    override fun execute(): Response = throw UnsupportedOperationException("Not used in test")

    override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException("Not used in test")

    override fun cancel() {
        cancelled.set(true)
    }

    override fun isExecuted(): Boolean = false

    override fun isCanceled(): Boolean = cancelled.get()

    override fun timeout(): Timeout = Timeout.NONE

    override fun clone(): Call = FakeCall(requestUrl, cancelled)
}
