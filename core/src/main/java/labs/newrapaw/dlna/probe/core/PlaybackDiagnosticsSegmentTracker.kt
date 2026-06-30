package labs.newrapaw.dlna.probe.core

import java.util.ArrayDeque

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
    private val recentSamples = ArrayDeque<SegmentSample>()

    fun reset() {
        recentSamples.clear()
    }

    fun recordResult(
        snapshot: PlaybackDiagnosticsSnapshot,
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String?,
    ): PlaybackDiagnosticsSegmentStats {
        if (recentSamples.size >= sampleLimit) recentSamples.removeFirst()
        recentSamples.addLast(
            SegmentSample(
                url = url,
                source = source,
                elapsedMs = elapsedMs,
                success = success,
                reason = fallbackReason,
            ),
        )

        val nextSamples = recentSamples.toList()
        val lastFive = nextSamples.takeLast(5)
        return PlaybackDiagnosticsSegmentStats(
            recentSamples = nextSamples,
            consecutiveFailures = if (success) 0 else snapshot.consecutiveFailures + 1,
            directWinCount = snapshot.directWinCount + if (success && source == "direct") 1 else 0,
            proxyWinCount = snapshot.proxyWinCount + if (success && source == "proxy") 1 else 0,
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
            timeoutCount = snapshot.timeoutCount + if (!success && fallbackReason?.contains("timeout", ignoreCase = true) == true) 1 else 0,
            fallbackCount = snapshot.fallbackCount + if (!fallbackReason.isNullOrBlank()) 1 else 0,
        )
    }

    fun snapshotSamples(): List<SegmentSample> = recentSamples.toList()
}
