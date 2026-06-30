package labs.newrapaw.dlna.probe.core

import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val upstreamRaceExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sessionPrefetchExecutor: ExecutorService = Executors.newCachedThreadPool()
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
        createSessionId = { "session-${System.currentTimeMillis()}" },
        cleanupSession = { playbackRuntime.cleanupSession(it.sessionId) },
    )
    val requestHandler = CoreLocalHlsRequestHandler(
        sessionLocalServer = sessionLocalServer,
        diagnosticsState = diagnosticsState,
        sessionAssetLoader = sessionAssetLoader,
        sessionAssetStreamer = sessionAssetStreamer,
        sessionPreparer = sessionPreparer,
        getActiveSessionShell = playbackRuntime::activeSessionShell,
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
    val proxyServer = CoreLocalHlsProxyServer(
        executor = executor,
        handleSocket = requestHandler::handle,
        safeLog = safeLog,
    )

    fun start() {
        sessionAssetStore.clearAllSessions()
        if (serveHttp) {
            proxyServer.start()
        }
    }

    override fun close() {
        proxyServer.close()
        playbackRuntime.close()
        upstreamRaceExecutor.shutdownNow()
        sessionPrefetchExecutor.shutdownNow()
        executor.shutdownNow()
    }
}
