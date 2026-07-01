package labs.newrapaw.dlna.probe.core

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackDiagnosticsStateTest {
    @Test
    fun snapshotReusesCachedObjectWhenStateIsUnchanged() {
        val state = PlaybackDiagnosticsState(sampleLimit = 5)
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )
        state.onSegmentResult(
            url = "https://example.com/segment-1.ts",
            source = "direct",
            elapsedMs = 120L,
            success = true,
        )

        val first = state.snapshot()
        val second = state.snapshot()

        assertSame(first, second)
    }

    @Test
    fun snapshotRefreshesAfterStateMutation() {
        val state = PlaybackDiagnosticsState(sampleLimit = 5)
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )

        val first = state.snapshot()
        state.setPlaybackStatus(PlaybackDiagnosticsStatus.PLAYING)
        val second = state.snapshot()

        assertNotSame(first, second)
        assertEquals(PlaybackDiagnosticsStatus.PLAYING, second.playbackStatus)
    }

    @Test
    fun timeoutAndFallbackCountsStayBoundedToRecentSampleWindow() {
        val state = PlaybackDiagnosticsState(sampleLimit = 3)
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )

        repeat(4) { index ->
            state.onSegmentResult(
                url = "https://example.com/segment-$index.ts",
                source = "proxy",
                elapsedMs = 5_000L,
                success = false,
                fallbackReason = "timeout after 5000ms",
            )
        }

        val snapshot = state.snapshot()

        assertEquals(3, snapshot.timeoutCount)
        assertEquals(3, snapshot.fallbackCount)
        assertEquals(3, snapshot.recentSegmentSamples.size)
        assertEquals(3, snapshot.lastTwentyFailureCount)
    }

    @Test
    fun upstreamWinCountsStayBoundedToRecentSampleWindow() {
        val state = PlaybackDiagnosticsState(sampleLimit = 3)
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )

        state.onSegmentResult(
            url = "https://example.com/segment-0.ts",
            source = "direct",
            elapsedMs = 100L,
            success = true,
        )
        state.onSegmentResult(
            url = "https://example.com/segment-1.ts",
            source = "direct",
            elapsedMs = 110L,
            success = true,
        )
        state.onSegmentResult(
            url = "https://example.com/segment-2.ts",
            source = "proxy",
            elapsedMs = 120L,
            success = true,
        )
        state.onSegmentResult(
            url = "https://example.com/segment-3.ts",
            source = "direct",
            elapsedMs = 130L,
            success = true,
        )

        val snapshot = state.snapshot()

        assertEquals(3, snapshot.recentSegmentSamples.size)
        assertEquals(2, snapshot.directWinCount)
        assertEquals(1, snapshot.proxyWinCount)
    }

    @Test
    fun cancelledSegmentFailuresDoNotPolluteFailureCountersOrSamples() {
        val state = PlaybackDiagnosticsState(sampleLimit = 5)
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )
        state.onSegmentResult(
            url = "https://example.com/segment-ok.ts",
            source = "direct",
            elapsedMs = 120L,
            success = true,
        )

        state.onSegmentResult(
            url = "https://example.com/segment-cancelled.ts",
            source = "direct",
            elapsedMs = 0L,
            success = false,
            fallbackReason = "CancellationException: session already cancelled",
        )

        val snapshot = state.snapshot()

        assertEquals(0, snapshot.consecutiveFailures)
        assertEquals(0, snapshot.fallbackCount)
        assertEquals(0, snapshot.timeoutCount)
        assertEquals(0, snapshot.lastFiveFailureCount)
        assertEquals(0, snapshot.lastTwentyFailureCount)
        assertEquals(1, snapshot.recentSegmentSamples.size)
        assertEquals("https://example.com/segment-ok.ts", snapshot.lastSucceededSegment)
        assertEquals(null, snapshot.lastFailedSegment)
        assertEquals(null, snapshot.lastError)
    }

    @Test
    fun readingPlayerLoadingFlagDoesNotForceDerivedSnapshotRecompute() {
        val deriveCalls = AtomicInteger(0)
        val state = PlaybackDiagnosticsState(
            sampleLimit = 5,
            deriveSnapshot = { snapshot ->
                deriveCalls.incrementAndGet()
                deriveDiagnosticsSnapshot(snapshot)
            },
        )
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )
        state.updatePlayerTelemetry(
            positionMs = 1_000L,
            bufferedPositionMs = 2_000L,
            isLoading = true,
        )

        assertEquals(0, deriveCalls.get())
        assertEquals(true, state.playerIsLoading())
        assertEquals(0, deriveCalls.get())

        state.snapshot()
        assertEquals(1, deriveCalls.get())
    }

    @Test
    fun snapshotThrottlesDerivedRecomputeButStillReturnsLatestRawState() {
        var nowMs = 10_000L
        val deriveCalls = AtomicInteger(0)
        val state = PlaybackDiagnosticsState(
            sampleLimit = 5,
            deriveSnapshot = { snapshot ->
                deriveCalls.incrementAndGet()
                deriveDiagnosticsSnapshot(snapshot)
            },
            nowMs = { nowMs },
            deriveSnapshotThrottleMs = 250L,
        )
        state.resetForPlayback(
            sourceUrl = "https://example.com/video.m3u8",
            localProxyUrl = "http://127.0.0.1:4321/session/test/manifest.m3u8",
            settings = ProxySettingsState(),
        )

        val baseline = state.snapshot()
        assertEquals(1, deriveCalls.get())
        assertEquals(DiagnosticsSeverity.OK, baseline.severity)

        state.onSegmentResult(
            url = "https://example.com/segment-failed.ts",
            source = "direct",
            elapsedMs = 500L,
            success = false,
            fallbackReason = "timeout after 500ms",
        )

        val throttled = state.snapshot()
        val throttledAgain = state.snapshot()

        assertEquals(1, deriveCalls.get())
        assertEquals(1, throttled.consecutiveFailures)
        assertEquals(1, throttled.timeoutCount)
        assertEquals(DiagnosticsSeverity.OK, throttled.severity)
        assertSame(throttled, throttledAgain)

        nowMs += 251L

        val refreshed = state.snapshot()

        assertEquals(2, deriveCalls.get())
        assertEquals(DiagnosticsSeverity.CRITICAL, refreshed.severity)
    }
}
