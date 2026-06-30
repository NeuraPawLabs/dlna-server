package labs.newrapaw.dlna.probe.core

import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.net.SocketException
import okhttp3.OkHttpClient

class CoreLocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    private val proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    private val sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
    private val serveHttp: Boolean = true,
) : Closeable {
    private val components = CoreLocalHlsProxyComponents(
        client = client,
        proxySettingsStore = proxySettingsStore,
        sessionAssetRootDir = sessionAssetRootDir,
        serveHttp = serveHttp,
        safeLog = ::safeLog,
        shouldSuppressRequestFailureLog = ::shouldSuppressRequestFailureLog,
    )
    private val playbackRuntime = components.playbackRuntime
    private val diagnosticsCoordinator = components.diagnosticsCoordinator
    private val sessionOpener = components.sessionOpener
    private val proxyServer = components.proxyServer

    val port: Int
        get() = proxyServer.port

    val baseUrl: String
        get() = proxyServer.baseUrl

    fun publicBaseUrl(hostAddress: String): String = proxyServer.publicBaseUrl(hostAddress)

    fun activeSessionInfo(localBaseUrl: String = baseUrl): ActiveSessionInfo? = playbackRuntime.activeSessionInfo(localBaseUrl)

    fun diagnosticsSnapshot(): PlaybackDiagnosticsSnapshot = diagnosticsCoordinator.snapshot()

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        diagnosticsCoordinator.updatePlaybackStatus(status)
    }

    fun updatePlaybackError(message: String?) {
        diagnosticsCoordinator.updatePlaybackError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        diagnosticsCoordinator.updatePlayerTelemetry(positionMs, bufferedPositionMs, isLoading)
    }

    fun clearActiveSessionCache() {
        diagnosticsCoordinator.clearActiveSessionCache()
    }

    fun updatePrefetchConcurrency(prefetchConcurrency: Int) {
        diagnosticsCoordinator.updatePrefetchConcurrency(prefetchConcurrency)
    }

    fun start() {
        components.start()
    }

    fun openSession(
        sourceUrl: String,
        localBaseUrl: String = baseUrl,
    ): ActiveSessionInfo = sessionOpener.openSession(sourceUrl, localBaseUrl)

    fun handleSessionRequest(
        method: String,
        path: String,
        output: OutputStream,
    ): Boolean = components.requestHandler.handleSessionRequest(method, path, output)

    override fun close() {
        components.close()
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
