package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.io.File
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import labs.newrapaw.dlna.probe.admin.AdminHttpRoutes
import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.dlna.DlnaRendererController
import okhttp3.OkHttpClient

class LocalHlsProxy(
    client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    getLogs: () -> List<String> = { emptyList() },
    proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
    dlnaConfig: () -> DlnaDeviceConfig? = { null },
    private val onPlayRequested: (String) -> Unit = {},
    onStopRequested: () -> Unit = {},
    onPauseRequested: () -> Unit = {},
    onUpdateRequested: (String) -> Unit = {},
) : Closeable {
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val coreProxy = CoreLocalHlsProxy(
        client = client,
        log = log,
        proxySettingsStore = proxySettingsStore,
        sessionAssetRootDir = sessionAssetRootDir,
        serveHttp = false,
    )
    private val renderer = DlnaRendererController(
        log = log,
        onPlayRequested = ::dispatchPlaybackRequest,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
    )
    private val adminRoutes = AdminHttpRoutes(
        proxySettingsStore = proxySettingsStore,
        getLogs = getLogs,
        diagnosticsSnapshot = coreProxy::diagnosticsSnapshot,
        activeSessionInfo = ::activeSessionInfo,
        requestPlayback = ::dispatchPlaybackRequest,
        onStopRequested = onStopRequested,
        updatePlaybackStatus = coreProxy::updatePlaybackStatus,
        onUpdateRequested = onUpdateRequested,
        clearActiveSessionCache = coreProxy::clearActiveSessionCache,
        updatePrefetchConcurrency = coreProxy::updatePrefetchConcurrency,
        localPlaybackUrl = { baseUrl },
        safeLog = ::safeLog,
    )
    private val dlnaRoutes = LocalHlsProxyDlnaRoutes(
        dlnaConfig = dlnaConfig,
        renderer = renderer,
        safeLog = ::safeLog,
    )
    private val sessionRelay = LocalHlsProxySessionRelay(
        handleSessionRequest = coreProxy::handleSessionRequest,
    )
    private val requestHandler = LocalHlsProxyRequestHandler(
        adminRoutes = adminRoutes,
        dlnaRoutes = dlnaRoutes,
        sessionRelay = sessionRelay,
        shouldSuppressRequestFailureLog = ::shouldSuppressRequestFailureLog,
        safeLog = ::safeLog,
    )
    private val proxyServer = LocalHlsProxyServer(
        executor = executor,
        handleSocket = requestHandler::handle,
        safeLog = ::safeLog,
    )

    val port: Int
        get() = proxyServer.port

    val baseUrl: String
        get() = proxyServer.baseUrl

    fun publicBaseUrl(hostAddress: String): String = proxyServer.publicBaseUrl(hostAddress)

    fun activeSessionInfo(): ActiveSessionInfo? = coreProxy.activeSessionInfo(baseUrl)

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        coreProxy.updatePlaybackStatus(status)
    }

    fun updatePlaybackError(message: String?) {
        coreProxy.updatePlaybackError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        coreProxy.updatePlayerTelemetry(
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs,
            isLoading = isLoading,
        )
    }

    fun start() {
        coreProxy.start()
        proxyServer.start()
    }

    override fun close() {
        proxyServer.close()
        coreProxy.close()
        executor.shutdownNow()
    }

    private fun dispatchPlaybackRequest(sourceUrl: String) {
        safeLog("Remote play request: $sourceUrl")
        val session = coreProxy.openSession(sourceUrl, localBaseUrl = baseUrl)
        onPlayRequested(session.localManifestUrl)
    }

    private fun safeLog(message: String) {
        runCatching { log(message) }
    }

    private fun shouldSuppressRequestFailureLog(error: Throwable): Boolean {
        val socketError = error as? SocketException ?: return false
        val message = socketError.message.orEmpty()
        return message.contains("Broken pipe", ignoreCase = true)
    }
}
