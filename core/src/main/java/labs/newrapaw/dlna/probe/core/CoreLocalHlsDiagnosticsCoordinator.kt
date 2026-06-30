package labs.newrapaw.dlna.probe.core

internal class CoreLocalHlsDiagnosticsCoordinator(
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val proxySettingsStore: ProxySettingsStore,
    private val playbackRuntime: CoreLocalHlsPlaybackRuntime,
) {
    fun snapshot(): PlaybackDiagnosticsSnapshot = diagnosticsState.snapshot()

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        diagnosticsState.setPlaybackStatus(status)
    }

    fun updatePlaybackError(message: String?) {
        diagnosticsState.setLastError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        playbackRuntime.updatePlayerTelemetry(positionMs, bufferedPositionMs)
        diagnosticsState.updatePlayerTelemetry(
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs,
            isLoading = isLoading,
        )
    }

    fun clearActiveSessionCache() {
        playbackRuntime.clearActiveSessionCache()
        refreshSnapshot()
    }

    fun updatePrefetchConcurrency(prefetchConcurrency: Int) {
        playbackRuntime.activePreparedSession()?.prefetchController?.updateConcurrency(prefetchConcurrency)
        diagnosticsState.setUpstreamSettings(proxySettingsStore.load())
        refreshSnapshot()
    }

    fun refreshSnapshot() {
        refreshPreparedSessionDiagnostics(
            activePreparedSession = playbackRuntime.activePreparedSession(),
            diagnosticsState = diagnosticsState,
            proxySettingsState = proxySettingsStore.load(),
            latestPlayerPositionMs = playbackRuntime.latestPlayerPositionMs(),
            latestBufferedPositionMs = playbackRuntime.latestBufferedPositionMs(),
            playerIsLoading = diagnosticsState.snapshot().playerIsLoading ?: false,
        )
    }
}
