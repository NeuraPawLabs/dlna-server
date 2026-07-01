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

internal class PlaybackDiagnosticsSnapshotUpdater(
    private val snapshotRuntime: PlaybackDiagnosticsSnapshotRuntime,
    private val segmentTracker: PlaybackDiagnosticsSegmentTracker,
    private val nowMs: () -> Long,
) {
    fun resetForPlayback(
        sourceUrl: String,
        localProxyUrl: String,
        settings: ProxySettingsState,
    ) {
        segmentTracker.reset()
        val currentTimeMs = nowMs()
        snapshotRuntime.reset(
            PlaybackDiagnosticsSnapshot.empty().copy(
                playbackStatus = PlaybackDiagnosticsStatus.BUFFERING,
                sessionStartedAtMs = currentTimeMs,
                sourceUrl = sourceUrl,
                localProxyUrl = localProxyUrl,
                lastUpdatedAtMs = currentTimeMs,
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setPlaybackStatus(status: PlaybackDiagnosticsStatus) {
        val snapshot = snapshotRuntime.current()
        val clearedRecoverableError = if (status.clearsRecoverablePlaybackError()) {
            snapshot.lastError?.takeUnless(::isRecoverablePlaybackErrorMessage)
        } else {
            snapshot.lastError
        }
        snapshotRuntime.touch(
            snapshot.copy(
                playbackStatus = status,
                lastError = clearedRecoverableError,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setSessionStatus(status: String?) {
        snapshotRuntime.touch(snapshotRuntime.current().copy(sessionStatus = status), allowDerivedThrottle = false)
    }

    fun setLastError(message: String?) {
        val snapshot = snapshotRuntime.current()
        val nextStatus = when {
            message.isNullOrBlank() -> snapshot.playbackStatus
            isRecoverablePlaybackErrorMessage(message) -> snapshot.playbackStatus
            else -> PlaybackDiagnosticsStatus.FAILED
        }
        snapshotRuntime.touch(
            snapshot.copy(
                lastError = message,
                playbackStatus = nextStatus,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setUpstreamSettings(settings: ProxySettingsState) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updatePrefetchStats(
        prefetchConcurrency: Int,
        pendingPrefetchCount: Int,
        inFlightCount: Int,
    ) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                prefetchConcurrency = prefetchConcurrency,
                pendingPrefetchCount = pendingPrefetchCount,
                inFlightCount = inFlightCount,
            ),
            allowDerivedThrottle = true,
        )
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                playerPositionMs = positionMs,
                playerBufferedPositionMs = bufferedPositionMs,
                playerIsLoading = isLoading,
            ),
            allowDerivedThrottle = true,
        )
    }
}

internal class PlaybackDiagnosticsSegmentSnapshotUpdater(
    private val snapshotRuntime: PlaybackDiagnosticsSnapshotRuntime,
    private val segmentTracker: PlaybackDiagnosticsSegmentTracker,
) {
    fun onSegmentRequested(url: String) {
        snapshotRuntime.touch(snapshotRuntime.current().copy(lastRequestedSegment = url), allowDerivedThrottle = true)
    }

    fun onSegmentResult(
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String?,
    ) {
        val snapshot = snapshotRuntime.current()
        if (!success && isIgnoredSegmentFailureReason(fallbackReason)) {
            return
        }
        val segmentStats = segmentTracker.recordResult(
            snapshot = snapshot,
            url = url,
            source = source,
            elapsedMs = elapsedMs,
            success = success,
            fallbackReason = fallbackReason,
        )
        snapshotRuntime.touch(
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
            allowDerivedThrottle = true,
        )
    }
}

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

private fun isRecoverablePlaybackErrorMessage(message: String): Boolean =
    message.startsWith("Recovering from ") ||
        message.startsWith("Rebuilding session after ") ||
        message.startsWith("Session gone while loading asset: ")

private fun PlaybackDiagnosticsStatus.clearsRecoverablePlaybackError(): Boolean =
    this == PlaybackDiagnosticsStatus.PLAYING ||
        this == PlaybackDiagnosticsStatus.PAUSED ||
        this == PlaybackDiagnosticsStatus.STOPPED ||
        this == PlaybackDiagnosticsStatus.IDLE

private fun isIgnoredSegmentFailureReason(reason: String?): Boolean {
    val normalized = reason.orEmpty()
    return normalized.startsWith("CancellationException:") ||
        normalized.startsWith("SessionCancelledException:") ||
        normalized.contains("session already cancelled", ignoreCase = true) ||
        normalized.contains("session cancelled while loading asset", ignoreCase = true)
}
