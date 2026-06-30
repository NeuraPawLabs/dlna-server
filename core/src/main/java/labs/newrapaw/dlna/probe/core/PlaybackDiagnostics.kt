package labs.newrapaw.dlna.probe.core

class PlaybackDiagnosticsState(
    private val sampleLimit: Int = 20,
) {
    private val lock = Any()
    private val segmentTracker = PlaybackDiagnosticsSegmentTracker(sampleLimit)
    private val sessionTracker = PlaybackDiagnosticsSessionTracker()
    private var snapshot = PlaybackDiagnosticsSnapshot.empty()
    private val staleThresholdMs = 5_000L

    fun resetForPlayback(
        sourceUrl: String,
        localProxyUrl: String,
        settings: ProxySettingsState,
    ) = synchronized(lock) {
        segmentTracker.reset()
        snapshot = PlaybackDiagnosticsSnapshot.empty().copy(
            playbackStatus = PlaybackDiagnosticsStatus.BUFFERING,
            sessionStartedAtMs = System.currentTimeMillis(),
            sourceUrl = sourceUrl,
            localProxyUrl = localProxyUrl,
            lastUpdatedAtMs = System.currentTimeMillis(),
            upstreamMode = settings.upstreamMode,
            activeProxy = settings.selectedProxy()?.displayUrl(),
            prefetchConcurrency = settings.prefetchConcurrency,
        )
    }

    fun setPlaybackStatus(status: PlaybackDiagnosticsStatus) = synchronized(lock) {
        touch(snapshot.copy(playbackStatus = status))
    }

    fun setSessionStatus(status: String?) = synchronized(lock) {
        touch(snapshot.copy(sessionStatus = status))
    }

    fun setLastError(message: String?) = synchronized(lock) {
        touch(
            snapshot.copy(
                lastError = message,
                playbackStatus = if (message.isNullOrBlank()) snapshot.playbackStatus else PlaybackDiagnosticsStatus.FAILED,
            ),
        )
    }

    fun setUpstreamSettings(settings: ProxySettingsState) = synchronized(lock) {
        touch(
            snapshot.copy(
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
        )
    }

    fun onSegmentRequested(url: String) = synchronized(lock) {
        touch(snapshot.copy(lastRequestedSegment = url))
    }

    fun onSegmentResult(
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String? = null,
    ) = synchronized(lock) {
        val segmentStats = segmentTracker.recordResult(
            snapshot = snapshot,
            url = url,
            source = source,
            elapsedMs = elapsedMs,
            success = success,
            fallbackReason = fallbackReason,
        )
        touch(
            snapshot.copy(
                lastSucceededSegment = if (success) url else snapshot.lastSucceededSegment,
                lastFailedSegment = if (success) snapshot.lastFailedSegment else url,
                consecutiveFailures = segmentStats.consecutiveFailures,
                recentSegmentSamples = segmentStats.recentSamples,
                directWinCount = segmentStats.directWinCount,
                proxyWinCount = segmentStats.proxyWinCount,
                directAverageElapsedMs = segmentStats.directAverageElapsedMs,
                proxyAverageElapsedMs = segmentStats.proxyAverageElapsedMs,
                lastFiveAverageElapsedMs = segmentStats.lastFiveAverageElapsedMs,
                lastFiveFailureCount = segmentStats.lastFiveFailureCount,
                lastTwentyAverageElapsedMs = segmentStats.lastTwentyAverageElapsedMs,
                lastTwentyFailureCount = segmentStats.lastTwentyFailureCount,
                timeoutCount = segmentStats.timeoutCount,
                fallbackCount = segmentStats.fallbackCount,
                lastFallbackReason = fallbackReason ?: snapshot.lastFallbackReason,
                lastError = if (success) snapshot.lastError else fallbackReason ?: "segment fetch failed",
            ),
        )
    }

    fun updatePrefetchStats(
        prefetchConcurrency: Int,
        pendingPrefetchCount: Int,
        inFlightCount: Int,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                prefetchConcurrency = prefetchConcurrency,
                pendingPrefetchCount = pendingPrefetchCount,
                inFlightCount = inFlightCount,
            ),
        )
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                playerPositionMs = positionMs,
                playerBufferedPositionMs = bufferedPositionMs,
                playerIsLoading = isLoading,
            ),
        )
    }

    fun updateStartupGate(
        phase: String,
        ready: Boolean,
        detail: String?,
    ) = synchronized(lock) {
        touch(
            sessionTracker.updateStartupGate(
                snapshot = snapshot,
                phase = phase,
                ready = ready,
                detail = detail,
            ),
        )
    }

    fun updateSlotDiagnostics(
        slotStates: List<SlotDiagnosticsItem>,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
        currentPlaybackSlotReady: Boolean?,
        continuousReadySlotCount: Int,
        continuousReadySlotDurationMs: Long,
    ) = synchronized(lock) {
        touch(
            sessionTracker.updateSlotDiagnostics(
                snapshot = snapshot,
                slotStates = slotStates,
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
                currentPlaybackSlotReady = currentPlaybackSlotReady,
                continuousReadySlotCount = continuousReadySlotCount,
                continuousReadySlotDurationMs = continuousReadySlotDurationMs,
            ),
        )
    }

    fun updateAssetDiagnostics(assetDiagnostics: List<AssetDiagnosticsItem>) = synchronized(lock) {
        touch(
            sessionTracker.updateAssetDiagnostics(
                snapshot = snapshot,
                assetDiagnostics = assetDiagnostics,
            ),
        )
    }

    fun updateAssetSummary(
        readyAssetCount: Int,
        totalAssetCount: Int,
        readyBytes: Long,
    ) = synchronized(lock) {
        touch(
            sessionTracker.updateAssetSummary(
                snapshot = snapshot,
                readyAssetCount = readyAssetCount,
                totalAssetCount = totalAssetCount,
                readyBytes = readyBytes,
            ),
        )
    }

    fun clearSlotDiagnostics(
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
    ) = synchronized(lock) {
        touch(
            sessionTracker.clearSlotDiagnostics(
                snapshot = snapshot,
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
            ),
        )
    }

    fun updateCurrentLoadingAsset(
        assetId: String?,
        kind: String?,
        trackId: String?,
        source: String?,
    ) = synchronized(lock) {
        touch(
            sessionTracker.updateCurrentLoadingAsset(
                snapshot = snapshot,
                assetId = assetId,
                kind = kind,
                trackId = trackId,
                source = source,
            ),
        )
    }

    fun snapshot(): PlaybackDiagnosticsSnapshot = synchronized(lock) {
        val stale = snapshot.lastUpdatedAtMs?.let { System.currentTimeMillis() - it > staleThresholdMs } ?: false
        snapshot.copy(
            recentSegmentSamples = segmentTracker.snapshotSamples(),
            isStale = stale,
        )
    }

    private fun touch(nextSnapshot: PlaybackDiagnosticsSnapshot) {
        val withTimestamp = nextSnapshot.copy(lastUpdatedAtMs = System.currentTimeMillis())
        val withRules = deriveDiagnosticsSnapshot(withTimestamp)
        snapshot = withRules
    }
}
