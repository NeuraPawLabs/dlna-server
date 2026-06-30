package labs.newrapaw.dlna.probe.core

internal class PlaybackDiagnosticsSessionTracker {
    fun updateStartupGate(
        snapshot: PlaybackDiagnosticsSnapshot,
        phase: String,
        ready: Boolean,
        detail: String?,
    ): PlaybackDiagnosticsSnapshot =
        snapshot.copy(
            startupGatePhase = phase,
            startupGateReady = ready,
            startupGateDetail = detail,
        )

    fun updateSlotDiagnostics(
        snapshot: PlaybackDiagnosticsSnapshot,
        slotStates: List<SlotDiagnosticsItem>,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
        currentPlaybackSlotReady: Boolean?,
        continuousReadySlotCount: Int,
        continuousReadySlotDurationMs: Long,
    ): PlaybackDiagnosticsSnapshot {
        val currentSlot = slotStates.firstOrNull { it.slotIndex == currentPlaybackSlotIndex }
        val stallReason = when {
            currentPlaybackSlotReady == false && currentSlot != null -> currentSlotStallReason(currentSlot)
            else -> snapshot.currentStallReason
        }
        return snapshot.copy(
            slotStates = slotStates.sortedBy { it.slotIndex },
            currentPlaybackSlotIndex = currentPlaybackSlotIndex,
            bufferedSlotIndex = bufferedSlotIndex,
            currentPlaybackSlotReady = currentPlaybackSlotReady,
            continuousReadySlotCount = continuousReadySlotCount,
            continuousReadySlotDurationMs = continuousReadySlotDurationMs,
            currentStallReason = stallReason,
        )
    }

    fun updateAssetDiagnostics(
        snapshot: PlaybackDiagnosticsSnapshot,
        assetDiagnostics: List<AssetDiagnosticsItem>,
    ): PlaybackDiagnosticsSnapshot {
        val readyAssets = assetDiagnostics.count { it.localReady }
        val readyBytes = assetDiagnostics.mapNotNull { it.sizeBytes }.sum()
        return snapshot.copy(
            assetDiagnostics = assetDiagnostics.sortedWith(compareBy<AssetDiagnosticsItem> { it.kind.name }.thenBy { it.assetId }),
            sessionReadyAssetCount = readyAssets,
            sessionTotalAssetCount = assetDiagnostics.size,
            sessionReadyBytes = readyBytes,
        )
    }

    fun updateAssetSummary(
        snapshot: PlaybackDiagnosticsSnapshot,
        readyAssetCount: Int,
        totalAssetCount: Int,
        readyBytes: Long,
    ): PlaybackDiagnosticsSnapshot =
        snapshot.copy(
            assetDiagnostics = emptyList(),
            sessionReadyAssetCount = readyAssetCount,
            sessionTotalAssetCount = totalAssetCount,
            sessionReadyBytes = readyBytes,
        )

    fun clearSlotDiagnostics(
        snapshot: PlaybackDiagnosticsSnapshot,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
    ): PlaybackDiagnosticsSnapshot =
        snapshot.copy(
            slotStates = emptyList(),
            currentPlaybackSlotIndex = currentPlaybackSlotIndex,
            bufferedSlotIndex = bufferedSlotIndex,
            currentPlaybackSlotReady = null,
            continuousReadySlotCount = 0,
            continuousReadySlotDurationMs = 0L,
            currentStallReason = null,
        )

    fun updateCurrentLoadingAsset(
        snapshot: PlaybackDiagnosticsSnapshot,
        assetId: String?,
        kind: String?,
        trackId: String?,
        source: String?,
    ): PlaybackDiagnosticsSnapshot =
        snapshot.copy(
            currentLoadingAssetId = assetId,
            currentLoadingAssetKind = kind,
            currentLoadingTrackId = trackId,
            currentLoadingSource = source,
        )
}
