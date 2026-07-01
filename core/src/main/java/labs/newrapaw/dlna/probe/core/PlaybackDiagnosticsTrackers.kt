package labs.newrapaw.dlna.probe.core

internal data class PlaybackDiagnosticsSegmentStats(
    val recentSamples: List<SegmentSample>,
    val consecutiveFailures: Int,
    val directWinCount: Int,
    val proxyWinCount: Int,
    val directAverageElapsedMs: Long?,
    val proxyAverageElapsedMs: Long?,
    val lastFiveAverageElapsedMs: Long?,
    val lastFiveFailureCount: Int,
    val lastTwentyAverageElapsedMs: Long?,
    val lastTwentyFailureCount: Int,
    val timeoutCount: Int,
    val fallbackCount: Int,
)

internal class PlaybackDiagnosticsSegmentTracker(
    private val sampleLimit: Int,
) {
    private var window = PlaybackDiagnosticsSegmentWindow.empty(sampleLimit)

    fun reset() {
        window = PlaybackDiagnosticsSegmentWindow.empty(sampleLimit)
    }

    fun recordResult(
        snapshot: PlaybackDiagnosticsSnapshot,
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String?,
    ): PlaybackDiagnosticsSegmentStats {
        window = window.record(
            SegmentSample(
                url = url,
                source = source,
                elapsedMs = elapsedMs,
                success = success,
                reason = fallbackReason,
            ),
        )
        val nextSamples = window.recentSamples
        val lastFive = nextSamples.takeLast(5)
        return PlaybackDiagnosticsSegmentStats(
            recentSamples = nextSamples,
            consecutiveFailures = if (success) 0 else snapshot.consecutiveFailures + 1,
            directWinCount = nextSamples.count { it.success && it.source == "direct" },
            proxyWinCount = nextSamples.count { it.success && it.source == "proxy" },
            directAverageElapsedMs = nextSamples.filter { it.success && it.source == "direct" }
                .takeIf { it.isNotEmpty() }
                ?.map { it.elapsedMs }
                ?.average()
                ?.toLong(),
            proxyAverageElapsedMs = nextSamples.filter { it.success && it.source == "proxy" }
                .takeIf { it.isNotEmpty() }
                ?.map { it.elapsedMs }
                ?.average()
                ?.toLong(),
            lastFiveAverageElapsedMs = lastFive.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong(),
            lastFiveFailureCount = lastFive.count { !it.success },
            lastTwentyAverageElapsedMs = nextSamples.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong(),
            lastTwentyFailureCount = nextSamples.count { !it.success },
            timeoutCount = nextSamples.count { !it.success && it.reason?.contains("timeout", ignoreCase = true) == true },
            fallbackCount = nextSamples.count { !it.reason.isNullOrBlank() },
        )
    }
}

internal data class PlaybackDiagnosticsSegmentWindow(
    val recentSamples: List<SegmentSample>,
    private val sampleLimit: Int,
) {
    fun record(sample: SegmentSample): PlaybackDiagnosticsSegmentWindow =
        copy(recentSamples = (recentSamples + sample).takeLast(sampleLimit))

    companion object {
        fun empty(sampleLimit: Int): PlaybackDiagnosticsSegmentWindow =
            PlaybackDiagnosticsSegmentWindow(
                recentSamples = emptyList(),
                sampleLimit = sampleLimit,
            )
    }
}

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

    fun clearPreparedSessionDiagnostics(
        snapshot: PlaybackDiagnosticsSnapshot,
    ): PlaybackDiagnosticsSnapshot =
        snapshot.copy(
            sessionStatus = null,
            pendingPrefetchCount = 0,
            inFlightCount = 0,
            currentLoadingAssetId = null,
            currentLoadingAssetKind = null,
            currentLoadingTrackId = null,
            currentLoadingSource = null,
            slotStates = emptyList(),
            assetDiagnostics = emptyList(),
            currentPlaybackSlotIndex = null,
            currentPlaybackSlotReady = null,
            bufferedSlotIndex = null,
            startupGatePhase = null,
            startupGateReady = null,
            startupGateDetail = null,
            currentStallReason = null,
            continuousReadySlotCount = 0,
            continuousReadySlotDurationMs = 0L,
            sessionReadyAssetCount = 0,
            sessionTotalAssetCount = 0,
            sessionReadyBytes = 0L,
        )
}

internal class PlaybackDiagnosticsSnapshotRuntime(
    private val deriveSnapshot: (PlaybackDiagnosticsSnapshot) -> PlaybackDiagnosticsSnapshot,
    private val nowMs: () -> Long,
    private val deriveSnapshotThrottleMs: Long,
) {
    private var state = PlaybackDiagnosticsSnapshotRuntimeState()
    private val staleThresholdMs = 5_000L

    fun current(): PlaybackDiagnosticsSnapshot = state.snapshot

    fun playerIsLoading(): Boolean? = state.snapshot.playerIsLoading

    fun reset(
        nextSnapshot: PlaybackDiagnosticsSnapshot,
        allowDerivedThrottle: Boolean,
    ) {
        setRawSnapshot(nextSnapshot, allowDerivedThrottle)
    }

    fun touch(
        nextSnapshot: PlaybackDiagnosticsSnapshot,
        allowDerivedThrottle: Boolean,
    ) {
        setRawSnapshot(
            nextSnapshot.copy(lastUpdatedAtMs = nowMs(), isStale = false),
            allowDerivedThrottle = allowDerivedThrottle,
        )
    }

    fun snapshot(): PlaybackDiagnosticsSnapshot {
        val currentTimeMs = nowMs()
        val stale = state.snapshot.lastUpdatedAtMs?.let { currentTimeMs - it > staleThresholdMs } ?: false
        val cached = state.cachedSnapshot
        if (!state.snapshotDirty && cached != null && cached.isStale == stale) {
            return cached
        }

        val nextSnapshot = when {
            state.snapshotDirty && cached != null && shouldThrottleDerivedSnapshot(currentTimeMs) ->
                cachedSnapshotForCurrentState(
                    cached = cached,
                    stale = stale,
                )
            state.snapshotDirty -> deriveSnapshot(state.snapshot).copy(isStale = stale)
            cached != null -> cached.copy(isStale = stale)
            else -> deriveSnapshot(state.snapshot).copy(isStale = stale)
        }
        state = state.copy(
            cachedSnapshot = nextSnapshot,
            cachedSnapshotVersion = state.snapshotVersion,
            snapshotDirty = if (state.snapshotDirty && !shouldThrottleDerivedSnapshot(currentTimeMs)) {
                false
            } else {
                state.snapshotDirty
            },
            lastDerivedAtMs = if (state.snapshotDirty && !shouldThrottleDerivedSnapshot(currentTimeMs)) {
                currentTimeMs
            } else {
                state.lastDerivedAtMs
            },
        )
        return nextSnapshot
    }

    private fun setRawSnapshot(
        nextSnapshot: PlaybackDiagnosticsSnapshot,
        allowDerivedThrottle: Boolean,
    ) {
        state = state.copy(
            snapshot = nextSnapshot,
            snapshotDirty = true,
            snapshotVersion = state.snapshotVersion + 1,
            canThrottleDerivedSnapshot = allowDerivedThrottle,
        )
    }

    private fun shouldThrottleDerivedSnapshot(currentTimeMs: Long): Boolean {
        if (!state.canThrottleDerivedSnapshot) return false
        val lastDerived = state.lastDerivedAtMs ?: return false
        return currentTimeMs - lastDerived < deriveSnapshotThrottleMs
    }

    private fun cachedSnapshotForCurrentState(
        cached: PlaybackDiagnosticsSnapshot,
        stale: Boolean,
    ): PlaybackDiagnosticsSnapshot {
        if (state.cachedSnapshotVersion == state.snapshotVersion && cached.isStale == stale) {
            return cached
        }
        return state.snapshot.copy(
            severity = cached.severity,
            insights = cached.insights,
            primaryBottleneck = cached.primaryBottleneck,
            isStale = stale,
        )
    }
}
