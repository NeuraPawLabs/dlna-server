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
