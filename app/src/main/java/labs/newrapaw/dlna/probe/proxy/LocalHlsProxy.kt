package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.io.File
import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.core.shouldSuppressRequestFailureLog
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import okhttp3.OkHttpClient

class LocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    getLogs: () -> List<String> = { emptyList() },
    proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
    dlnaConfig: () -> DlnaDeviceConfig? = { null },
    private val onPlayRequested: (String) -> Unit = {},
    private val beforePlaybackSwitch: () -> Unit = {},
    onStopRequested: () -> Unit = {},
    onPauseRequested: () -> Unit = {},
    onSeekRequested: (Long) -> Unit = {},
    onUpdateRequested: (String) -> Unit = {},
) : Closeable {
    private val coreProxy = CoreLocalHlsProxy(
        client = client,
        log = log,
        proxySettingsStore = proxySettingsStore,
        sessionAssetRootDir = sessionAssetRootDir,
        serveHttp = false,
    )
    private val playbackRouter = LocalHlsProxyPlaybackRouter(
        coreProxy = coreProxy,
        localBaseUrl = { baseUrl },
        safeLog = ::safeLog,
        beforePlaybackSwitch = beforePlaybackSwitch,
        onPlayRequested = onPlayRequested,
    )
    private val dlnaRuntime = LocalHlsProxyDlnaRuntime(
        client = client,
        dlnaConfig = dlnaConfig,
        log = log,
        safeLog = ::safeLog,
        onPlayRequested = playbackRouter::dispatch,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
        onSeekRequested = onSeekRequested,
    )
    private val adminRuntime = LocalHlsProxyAdminRuntime(
        proxySettingsStore = proxySettingsStore,
        getLogs = getLogs,
        diagnosticsSnapshot = coreProxy::diagnosticsSnapshot,
        activeSessionInfo = ::activeSessionInfo,
        requestPlayback = playbackRouter::dispatch,
        onStopRequested = onStopRequested,
        updatePlaybackStatus = coreProxy::updatePlaybackStatus,
        onUpdateRequested = onUpdateRequested,
        clearActiveSessionCache = coreProxy::clearActiveSessionCache,
        updatePrefetchConcurrency = coreProxy::updatePrefetchConcurrency,
        localPlaybackUrl = { baseUrl },
        safeLog = ::safeLog,
    )
    private val servingRuntime = LocalHlsProxyServingRuntime(
        coreProxy = coreProxy,
        adminRoutes = adminRuntime.routes,
        dlnaRoutes = dlnaRuntime.routes,
        shouldSuppressRequestFailureLog = ::shouldSuppressRequestFailureLog,
        safeLog = ::safeLog,
    )
    internal val playbackState = LocalHlsProxyPlaybackStateBridge(
        coreProxy = coreProxy,
        baseUrl = { baseUrl },
    )

    val port: Int
        get() = servingRuntime.port

    val baseUrl: String
        get() = servingRuntime.baseUrl

    fun publicBaseUrl(hostAddress: String): String = servingRuntime.publicBaseUrl(hostAddress)

    fun activeSessionInfo(): ActiveSessionInfo? = playbackState.activeSessionInfo()

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        playbackState.updatePlaybackStatus(status)
    }

    fun clearActivePlaybackSession() {
        playbackState.clearActivePlaybackSession()
    }

    fun updatePlaybackError(message: String?) {
        playbackState.updatePlaybackError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        playbackState.updatePlayerTelemetry(
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs,
            isLoading = isLoading,
        )
    }

    fun updateDlnaTransportState(
        transportState: String,
        transportStatus: String = "OK",
        positionMs: Long? = null,
        durationMs: Long? = null,
    ) {
        dlnaRuntime.syncPlayerState(
            transportState = transportState,
            transportStatus = transportStatus,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun updateDlnaPosition(positionMs: Long) {
        dlnaRuntime.syncPlayerPosition(positionMs)
    }

    fun start() = servingRuntime.start()

    override fun close() {
        dlnaRuntime.close()
        servingRuntime.close()
    }

    fun recoverActivePlaybackSession(): String? = playbackRouter.recoverActivePlaybackSession(baseUrl)

    private fun safeLog(message: String) {
        runCatching { log(message) }
    }
}
