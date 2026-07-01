package labs.newrapaw.dlna.probe.core

class PlaybackDiagnosticsState(
    private val sampleLimit: Int = 20,
    private val deriveSnapshot: (PlaybackDiagnosticsSnapshot) -> PlaybackDiagnosticsSnapshot = ::deriveDiagnosticsSnapshot,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val deriveSnapshotThrottleMs: Long = DEFAULT_DERIVE_SNAPSHOT_THROTTLE_MS,
) {
    private val lock = Any()
    private val segmentTracker = PlaybackDiagnosticsSegmentTracker(sampleLimit)
    private val sessionTracker = PlaybackDiagnosticsSessionTracker()
    private val snapshotRuntime = PlaybackDiagnosticsSnapshotRuntime(
        deriveSnapshot = deriveSnapshot,
        nowMs = nowMs,
        deriveSnapshotThrottleMs = deriveSnapshotThrottleMs,
    )
    private val snapshotUpdater = PlaybackDiagnosticsSnapshotUpdater(
        snapshotRuntime = snapshotRuntime,
        segmentTracker = segmentTracker,
        nowMs = nowMs,
    )
    private val segmentSnapshotUpdater = PlaybackDiagnosticsSegmentSnapshotUpdater(
        snapshotRuntime = snapshotRuntime,
        segmentTracker = segmentTracker,
    )
    private val sessionSnapshotUpdater = PlaybackDiagnosticsSessionSnapshotUpdater(
        snapshotRuntime = snapshotRuntime,
        sessionTracker = sessionTracker,
    )

    fun resetForPlayback(
        sourceUrl: String,
        localProxyUrl: String,
        settings: ProxySettingsState,
    ) = synchronized(lock) { snapshotUpdater.resetForPlayback(sourceUrl, localProxyUrl, settings) }

    fun setPlaybackStatus(status: PlaybackDiagnosticsStatus) = synchronized(lock) { snapshotUpdater.setPlaybackStatus(status) }

    fun setSessionStatus(status: String?) = synchronized(lock) { snapshotUpdater.setSessionStatus(status) }

    fun setLastError(message: String?) = synchronized(lock) { snapshotUpdater.setLastError(message) }

    fun setUpstreamSettings(settings: ProxySettingsState) = synchronized(lock) { snapshotUpdater.setUpstreamSettings(settings) }

    fun onSegmentRequested(url: String) = synchronized(lock) { segmentSnapshotUpdater.onSegmentRequested(url) }

    fun onSegmentResult(
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String? = null,
    ) = synchronized(lock) { segmentSnapshotUpdater.onSegmentResult(url, source, elapsedMs, success, fallbackReason) }

    fun updatePrefetchStats(
        prefetchConcurrency: Int,
        pendingPrefetchCount: Int,
        inFlightCount: Int,
    ) = synchronized(lock) { snapshotUpdater.updatePrefetchStats(prefetchConcurrency, pendingPrefetchCount, inFlightCount) }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) = synchronized(lock) { snapshotUpdater.updatePlayerTelemetry(positionMs, bufferedPositionMs, isLoading) }

    fun playerIsLoading(): Boolean? = synchronized(lock) { snapshotRuntime.playerIsLoading() }

    fun updateStartupGate(
        phase: String,
        ready: Boolean,
        detail: String?,
    ) = synchronized(lock) { sessionSnapshotUpdater.updateStartupGate(phase, ready, detail) }

    fun updateSlotDiagnostics(
        slotStates: List<SlotDiagnosticsItem>,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
        currentPlaybackSlotReady: Boolean?,
        continuousReadySlotCount: Int,
        continuousReadySlotDurationMs: Long,
    ) = synchronized(lock) {
        sessionSnapshotUpdater.updateSlotDiagnostics(
            slotStates,
            currentPlaybackSlotIndex,
            bufferedSlotIndex,
            currentPlaybackSlotReady,
            continuousReadySlotCount,
            continuousReadySlotDurationMs,
        )
    }

    fun updateAssetDiagnostics(assetDiagnostics: List<AssetDiagnosticsItem>) = synchronized(lock) {
        sessionSnapshotUpdater.updateAssetDiagnostics(assetDiagnostics)
    }

    fun updateAssetSummary(
        readyAssetCount: Int,
        totalAssetCount: Int,
        readyBytes: Long,
    ) = synchronized(lock) { sessionSnapshotUpdater.updateAssetSummary(readyAssetCount, totalAssetCount, readyBytes) }

    fun clearSlotDiagnostics(
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
    ) = synchronized(lock) { sessionSnapshotUpdater.clearSlotDiagnostics(currentPlaybackSlotIndex, bufferedSlotIndex) }

    fun updateCurrentLoadingAsset(
        assetId: String?,
        kind: String?,
        trackId: String?,
        source: String?,
    ) = synchronized(lock) { sessionSnapshotUpdater.updateCurrentLoadingAsset(assetId, kind, trackId, source) }

    fun clearPreparedSessionDiagnostics() = synchronized(lock) { sessionSnapshotUpdater.clearPreparedSessionDiagnostics() }

    fun snapshot(): PlaybackDiagnosticsSnapshot = synchronized(lock) { snapshotRuntime.snapshot() }

    private companion object {
        const val DEFAULT_DERIVE_SNAPSHOT_THROTTLE_MS = 250L
    }
}
