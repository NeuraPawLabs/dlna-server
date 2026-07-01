package labs.newrapaw.dlna.probe.proxy

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus

internal class LocalHlsProxyPlaybackStateBridge(
    private val coreProxy: CoreLocalHlsProxy,
    private val baseUrl: () -> String,
) {
    fun activeSessionInfo(): ActiveSessionInfo? = coreProxy.activeSessionInfo(baseUrl())

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        coreProxy.updatePlaybackStatus(status)
    }

    fun clearActivePlaybackSession() {
        coreProxy.clearActiveSessionCache()
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
}
