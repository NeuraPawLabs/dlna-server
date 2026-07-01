package labs.newrapaw.dlna.probe.core

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

private fun isIgnoredSegmentFailureReason(reason: String?): Boolean {
    val normalized = reason.orEmpty()
    return normalized.startsWith("CancellationException:") ||
        normalized.startsWith("SessionCancelledException:") ||
        normalized.contains("session already cancelled", ignoreCase = true) ||
        normalized.contains("session cancelled while loading asset", ignoreCase = true)
}
