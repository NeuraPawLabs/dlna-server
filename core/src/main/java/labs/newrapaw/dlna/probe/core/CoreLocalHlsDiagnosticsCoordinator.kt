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
        diagnosticsState.clearPreparedSessionDiagnostics()
    }

    fun updatePrefetchConcurrency(prefetchConcurrency: Int) {
        playbackRuntime.snapshot().activePreparedSession?.prefetchController?.updateConcurrency(prefetchConcurrency)
        diagnosticsState.setUpstreamSettings(proxySettingsStore.load())
        refreshSnapshot()
    }

    fun refreshSnapshot() {
        val runtimeSnapshot = playbackRuntime.snapshot()
        refreshPreparedSessionDiagnostics(
            activePreparedSession = runtimeSnapshot.activePreparedSession,
            diagnosticsState = diagnosticsState,
            proxySettingsState = proxySettingsStore.load(),
            latestPlayerPositionMs = runtimeSnapshot.latestPlayerPositionMs,
            latestBufferedPositionMs = runtimeSnapshot.latestBufferedPositionMs,
            playerIsLoading = diagnosticsState.playerIsLoading() ?: false,
        )
    }
}
