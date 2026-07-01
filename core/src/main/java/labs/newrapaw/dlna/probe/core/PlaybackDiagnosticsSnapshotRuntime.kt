package labs.newrapaw.dlna.probe.core

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
