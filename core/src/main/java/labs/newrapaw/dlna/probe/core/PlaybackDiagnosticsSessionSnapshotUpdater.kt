package labs.newrapaw.dlna.probe.core

internal class PlaybackDiagnosticsSessionSnapshotUpdater(
    private val snapshotRuntime: PlaybackDiagnosticsSnapshotRuntime,
    private val sessionTracker: PlaybackDiagnosticsSessionTracker,
) {
    fun updateStartupGate(
        phase: String,
        ready: Boolean,
        detail: String?,
    ) {
        snapshotRuntime.touch(
            sessionTracker.updateStartupGate(
                snapshot = snapshotRuntime.current(),
                phase = phase,
                ready = ready,
                detail = detail,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updateSlotDiagnostics(
        slotStates: List<SlotDiagnosticsItem>,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
        currentPlaybackSlotReady: Boolean?,
        continuousReadySlotCount: Int,
        continuousReadySlotDurationMs: Long,
    ) {
        snapshotRuntime.touch(
            sessionTracker.updateSlotDiagnostics(
                snapshot = snapshotRuntime.current(),
                slotStates = slotStates,
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
                currentPlaybackSlotReady = currentPlaybackSlotReady,
                continuousReadySlotCount = continuousReadySlotCount,
                continuousReadySlotDurationMs = continuousReadySlotDurationMs,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updateAssetDiagnostics(assetDiagnostics: List<AssetDiagnosticsItem>) {
        snapshotRuntime.touch(
            sessionTracker.updateAssetDiagnostics(
                snapshot = snapshotRuntime.current(),
                assetDiagnostics = assetDiagnostics,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updateAssetSummary(
        readyAssetCount: Int,
        totalAssetCount: Int,
        readyBytes: Long,
    ) {
        snapshotRuntime.touch(
            sessionTracker.updateAssetSummary(
                snapshot = snapshotRuntime.current(),
                readyAssetCount = readyAssetCount,
                totalAssetCount = totalAssetCount,
                readyBytes = readyBytes,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun clearSlotDiagnostics(
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
    ) {
        snapshotRuntime.touch(
            sessionTracker.clearSlotDiagnostics(
                snapshot = snapshotRuntime.current(),
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updateCurrentLoadingAsset(
        assetId: String?,
        kind: String?,
        trackId: String?,
        source: String?,
    ) {
        snapshotRuntime.touch(
            sessionTracker.updateCurrentLoadingAsset(
                snapshot = snapshotRuntime.current(),
                assetId = assetId,
                kind = kind,
                trackId = trackId,
                source = source,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun clearPreparedSessionDiagnostics() {
        snapshotRuntime.touch(
            sessionTracker.clearPreparedSessionDiagnostics(snapshotRuntime.current()),
            allowDerivedThrottle = false,
        )
    }
}
