package labs.newrapaw.dlna.probe.core

import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import labs.newrapaw.dlna.probe.core.session.ManifestPlanner
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionManager
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsProxyComponents(
    client: OkHttpClient,
    proxySettingsStore: ProxySettingsStore,
    sessionAssetRootDir: File,
    private val serveHttp: Boolean,
    safeLog: (String) -> Unit,
    shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
) : Closeable {
    private val sessionIdCounter = AtomicLong()
    private val executor: ExecutorService? = if (serveHttp) {
        boundedExecutor(
            maxThreads = CORE_REQUEST_MAX_THREADS,
            queueCapacity = CORE_REQUEST_QUEUE_CAPACITY,
        )
    } else {
        null
    }
    private val upstreamRaceExecutor: ExecutorService = boundedExecutor(
        maxThreads = CORE_UPSTREAM_RACE_MAX_THREADS,
        queueCapacity = CORE_UPSTREAM_RACE_QUEUE_CAPACITY,
    )
    private val sessionPrefetchExecutor: ExecutorService = boundedExecutor(
        maxThreads = CORE_SESSION_PREFETCH_MAX_THREADS,
        queueCapacity = CORE_SESSION_PREFETCH_QUEUE_CAPACITY,
    )
    private val sessionLocalServer = SessionLocalServer()
    private val manifestPlanner = ManifestPlanner()
    val sessionAssetStore = SessionAssetStore(sessionAssetRootDir)
    val playbackRuntime = CoreLocalHlsPlaybackRuntime(
        sessionAssetStore = sessionAssetStore,
        sessionLocalServer = sessionLocalServer,
    )
    val diagnosticsState = PlaybackDiagnosticsState()
    val diagnosticsCoordinator = CoreLocalHlsDiagnosticsCoordinator(
        diagnosticsState = diagnosticsState,
        proxySettingsStore = proxySettingsStore,
        playbackRuntime = playbackRuntime,
    )
    val upstreamRaceClient = CoreLocalHlsUpstreamRaceClient(
        upstreamRaceExecutor = upstreamRaceExecutor,
    )
    val upstreamClient = CoreLocalHlsUpstreamClient(
        client = client,
        proxySettingsStore = proxySettingsStore,
        diagnosticsState = diagnosticsState,
        upstreamRaceClient = upstreamRaceClient,
        refreshDiagnosticsSnapshot = diagnosticsCoordinator::refreshSnapshot,
        log = safeLog,
    )
    val sessionManifestResolver = CoreLocalHlsSessionManifestResolver(
        fetchManifest = { url -> upstreamClient.fetchUpstreamBytes(url).toString(Charsets.UTF_8) },
    )
    val sessionAssetLoader = CoreLocalHlsSessionAssetLoader(
        sessionAssetStore = sessionAssetStore,
        diagnosticsState = diagnosticsState,
        upstreamClient = upstreamClient,
        refreshDiagnosticsSnapshot = diagnosticsCoordinator::refreshSnapshot,
    )
    val sessionAssetStreamer = CoreLocalHlsSessionAssetStreamer(
        proxySettingsStore = proxySettingsStore,
        sessionAssetStore = sessionAssetStore,
        diagnosticsState = diagnosticsState,
        upstreamClient = upstreamClient,
        refreshDiagnosticsSnapshot = diagnosticsCoordinator::refreshSnapshot,
    )
    val preparedSessionBuilder = CoreLocalHlsPreparedSessionBuilder(
        sessionLocalServer = sessionLocalServer,
        sessionPrefetchExecutor = sessionPrefetchExecutor,
    )
    val sessionPreparer = CoreLocalHlsSessionPreparer(
        diagnosticsState = diagnosticsState,
        sessionManifestResolver = sessionManifestResolver,
        manifestPlanner = manifestPlanner,
        preparedSessionBuilder = preparedSessionBuilder,
        proxySettingsStore = proxySettingsStore,
        sessionAssetLoader = sessionAssetLoader,
        sessionAssetStore = sessionAssetStore,
        refreshDiagnosticsSnapshot = diagnosticsCoordinator::refreshSnapshot,
        safeLog = safeLog,
    )
    private val sessionManager = PlaybackSessionManager(
        createSessionId = { "session-${System.currentTimeMillis()}-${sessionIdCounter.incrementAndGet()}" },
        cleanupSession = { playbackRuntime.cleanupSession(it.sessionId) },
    )
    val requestHandler = CoreLocalHlsRequestHandler(
        sessionLocalServer = sessionLocalServer,
        diagnosticsState = diagnosticsState,
        sessionAssetLoader = sessionAssetLoader,
        sessionAssetStreamer = sessionAssetStreamer,
        sessionPreparer = sessionPreparer,
        getActiveSessionShell = playbackRuntime::activeSessionShell,
        isClosedSessionId = sessionManager::isClosedSessionId,
        getActivePreparedSession = playbackRuntime::activePreparedSession,
        setActivePreparedSession = playbackRuntime::setActivePreparedSession,
        updatePlaybackPosition = playbackRuntime::updatePlaybackPosition,
        refreshPreparedSessionDiagnostics = diagnosticsCoordinator::refreshSnapshot,
        shouldSuppressRequestFailureLog = shouldSuppressRequestFailureLog,
        safeLog = safeLog,
    )
    val sessionOpener = CoreLocalHlsSessionOpener(
        sessionManager = sessionManager,
        playbackRuntime = playbackRuntime,
        diagnosticsState = diagnosticsState,
        proxySettingsStore = proxySettingsStore,
        sessionLocalServer = sessionLocalServer,
    )
    val proxyServer = executor?.let { requestExecutor ->
        CoreLocalHlsProxyServer(
            executor = requestExecutor,
            handleSocket = requestHandler::handle,
            safeLog = safeLog,
        )
    }

    fun start() {
        sessionAssetStore.clearAllSessions()
        proxyServer?.start()
    }

    override fun close() {
        proxyServer?.close()
        playbackRuntime.close()
        upstreamRaceExecutor.let(::shutdownGracefully)
        sessionPrefetchExecutor.let(::shutdownGracefully)
        executor?.let(::shutdownGracefully)
    }

    private companion object {
        const val CORE_REQUEST_MAX_THREADS = 8
        const val CORE_REQUEST_QUEUE_CAPACITY = 64
        const val CORE_UPSTREAM_RACE_MAX_THREADS = 2
        const val CORE_UPSTREAM_RACE_QUEUE_CAPACITY = 16
        const val CORE_SESSION_PREFETCH_MAX_THREADS = 6
        const val CORE_SESSION_PREFETCH_QUEUE_CAPACITY = 64

        fun boundedExecutor(
            maxThreads: Int,
            queueCapacity: Int,
        ): ThreadPoolExecutor =
            ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(queueCapacity),
                ThreadPoolExecutor.AbortPolicy(),
            ).apply {
                allowCoreThreadTimeOut(true)
            }

        fun shutdownGracefully(executor: ExecutorService) {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
}
