package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsStateTest {
    @Test
    fun snapshotIncludesPlayerTelemetryAndSessionAssetTotals() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 3),
        )

        state.updatePlayerTelemetry(
            positionMs = 120_000L,
            bufferedPositionMs = 144_000L,
            isLoading = true,
        )
        state.updateAssetDiagnostics(
            listOf(
                labs.newrapaw.dlna.probe.core.AssetDiagnosticsItem(
                    assetId = "video-1",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    state = SessionAssetState.READY,
                    localReady = true,
                    sizeBytes = 2048,
                    lastElapsedMs = 120,
                    lastSource = "session-local",
                    retryCount = 1,
                    failureReason = null,
                ),
                labs.newrapaw.dlna.probe.core.AssetDiagnosticsItem(
                    assetId = "audio-1",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "audio-main",
                    state = SessionAssetState.DOWNLOADING,
                    localReady = false,
                    sizeBytes = null,
                    lastElapsedMs = null,
                    lastSource = null,
                    retryCount = 0,
                    failureReason = null,
                ),
            ),
        )

        val snapshot = state.snapshot()

        assertEquals(120_000L, snapshot.playerPositionMs)
        assertEquals(144_000L, snapshot.playerBufferedPositionMs)
        assertEquals(true, snapshot.playerIsLoading)
        assertEquals(1, snapshot.sessionReadyAssetCount)
        assertEquals(2, snapshot.sessionTotalAssetCount)
        assertEquals(2048L, snapshot.sessionReadyBytes)
    }

    @Test
    fun snapshotIncludesWindowStatsSeverityAndInsights() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(
                proxies = listOf(labs.newrapaw.dlna.probe.core.ProxyConfig("p1", labs.newrapaw.dlna.probe.core.ProxyType.HTTP, "192.168.1.2", 7890)),
                selectedProxyId = "p1",
                upstreamMode = labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY,
                prefetchConcurrency = 4,
            ),
        )

        state.onSegmentResult("seg-4.ts", "direct", 100, success = true)
        state.onSegmentResult("seg-5.ts", "proxy", 400, success = true)
        state.onSegmentResult("seg-6.ts", "proxy", 500, success = true)
        state.onSegmentResult("seg-7.ts", "direct", 120, success = true)
        state.onSegmentResult("seg-8.ts", "proxy", 980, success = false, fallbackReason = "SocketTimeoutException: timeout")
        state.onSegmentResult("seg-9.ts", "proxy", 440, success = true)
        state.updatePrefetchStats(
            prefetchConcurrency = 4,
            pendingPrefetchCount = 0,
            inFlightCount = 1,
        )
        state.updateSlotDiagnostics(
            slotStates = listOf(
                labs.newrapaw.dlna.probe.core.SlotDiagnosticsItem(
                    slotIndex = 41,
                    startMs = 164_000L,
                    endMs = 168_000L,
                    state = labs.newrapaw.dlna.probe.core.SlotDiagnosticsState.PLAYING,
                    videoReady = true,
                    audioReady = true,
                    subtitleReady = true,
                    blockedAssetKinds = emptyList(),
                    degradedAssetKinds = emptyList(),
                ),
            ),
            currentPlaybackSlotIndex = 41,
            bufferedSlotIndex = 41,
            currentPlaybackSlotReady = true,
            continuousReadySlotCount = 1,
            continuousReadySlotDurationMs = 4_000L,
        )

        val snapshot = state.snapshot()

        assertEquals(4, snapshot.prefetchConcurrency)
        assertEquals(488L, snapshot.lastFiveAverageElapsedMs)
        assertEquals(1, snapshot.lastFiveFailureCount)
        assertEquals(423L, snapshot.lastTwentyAverageElapsedMs)
        assertEquals(1, snapshot.lastTwentyFailureCount)
        assertEquals("SocketTimeoutException: timeout", snapshot.recentSegmentSamples[4].reason)
        assertEquals(labs.newrapaw.dlna.probe.core.DiagnosticsSeverity.CRITICAL, snapshot.severity)
        assertEquals(0, snapshot.consecutiveFailures)
        assertTrue(snapshot.lastUpdatedAtMs != null)
        assertTrue(snapshot.insights.any { it.code == "slot_window_low" })
        assertTrue(snapshot.insights.any { it.code == "prefetch_queue_empty" })
        assertTrue(snapshot.insights.any { it.code == "proxy_slower_than_direct" })
        assertTrue(snapshot.insights.any { it.code == "timeout_detected" })
        assertEquals("slot_window_low", snapshot.primaryBottleneck?.code)
    }

    @Test
    fun startupGateBecomesPrimaryBottleneckBeforeSessionIsReady() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 4),
        )

        state.updateStartupGate(
            phase = "启动预热",
            ready = false,
            detail = "仍缺少 2 个启动资源",
        )

        val snapshot = state.snapshot()

        assertTrue(snapshot.insights.any { it.code == "startup_gate_blocked" })
        assertEquals("startup_gate_blocked", snapshot.primaryBottleneck?.code)
        assertEquals("启动门控尚未满足", snapshot.primaryBottleneck?.message)
        assertEquals("仍缺少 2 个启动资源", snapshot.startupGateDetail)
    }

    @Test
    fun blockedCurrentSlotBecomesPrimaryStallReason() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 4),
        )

        state.updateSlotDiagnostics(
            slotStates = listOf(
                labs.newrapaw.dlna.probe.core.SlotDiagnosticsItem(
                    slotIndex = 41,
                    startMs = 164_000L,
                    endMs = 168_000L,
                    state = labs.newrapaw.dlna.probe.core.SlotDiagnosticsState.BLOCKED,
                    videoReady = true,
                    audioReady = false,
                    subtitleReady = false,
                    blockedAssetKinds = listOf(SessionAssetKind.AUDIO_SEGMENT, SessionAssetKind.KEY),
                    degradedAssetKinds = emptyList(),
                ),
            ),
            currentPlaybackSlotIndex = 41,
            bufferedSlotIndex = 41,
            currentPlaybackSlotReady = false,
            continuousReadySlotCount = 0,
            continuousReadySlotDurationMs = 0L,
        )

        val snapshot = state.snapshot()

        assertEquals("current_slot_blocked", snapshot.primaryBottleneck?.code)
        assertEquals("当前播放槽位存在硬依赖阻塞", snapshot.primaryBottleneck?.message)
        assertTrue(snapshot.currentStallReason?.contains("音频") == true)
        assertTrue(snapshot.currentStallReason?.contains("密钥") == true)
    }

    @Test
    fun sessionAssetTimeoutAndFailureBecomeDedicatedDiagnosticsSignals() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 4),
        )

        state.setLastError("Session asset wait timed out: video-41")

        val timeoutSnapshot = state.snapshot()
        assertTrue(timeoutSnapshot.insights.any { it.code == "session_asset_timeout" })
        assertEquals("session_asset_timeout", timeoutSnapshot.primaryBottleneck?.code)

        state.setLastError("Session asset failed: audio-main-41")

        val failedSnapshot = state.snapshot()
        assertTrue(failedSnapshot.insights.any { it.code == "session_asset_failed" })
        assertEquals("session_asset_failed", failedSnapshot.primaryBottleneck?.code)
    }

    @Test
    fun healthySlotWindowSuppressesExtraBottleneck() {
        val state = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsState(sampleLimit = 20)
        state.resetForPlayback(
            sourceUrl = "https://origin.example/video.m3u8",
            localProxyUrl = "http://127.0.0.1:43000/session/session-1/manifest.m3u8",
            settings = labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 4),
        )

        state.updatePrefetchStats(
            prefetchConcurrency = 4,
            pendingPrefetchCount = 2,
            inFlightCount = 0,
        )
        state.updateSlotDiagnostics(
            slotStates = listOf(
                labs.newrapaw.dlna.probe.core.SlotDiagnosticsItem(
                    slotIndex = 41,
                    startMs = 164_000L,
                    endMs = 168_000L,
                    state = labs.newrapaw.dlna.probe.core.SlotDiagnosticsState.PLAYING,
                    videoReady = true,
                    audioReady = true,
                    subtitleReady = true,
                    blockedAssetKinds = emptyList(),
                    degradedAssetKinds = emptyList(),
                ),
                labs.newrapaw.dlna.probe.core.SlotDiagnosticsItem(
                    slotIndex = 42,
                    startMs = 168_000L,
                    endMs = 172_000L,
                    state = labs.newrapaw.dlna.probe.core.SlotDiagnosticsState.READY,
                    videoReady = true,
                    audioReady = true,
                    subtitleReady = true,
                    blockedAssetKinds = emptyList(),
                    degradedAssetKinds = emptyList(),
                ),
            ),
            currentPlaybackSlotIndex = 41,
            bufferedSlotIndex = 42,
            currentPlaybackSlotReady = true,
            continuousReadySlotCount = 2,
            continuousReadySlotDurationMs = 8_000L,
        )

        val snapshot = state.snapshot()

        assertFalse(snapshot.insights.any { it.code == "prefetch_queue_empty" })
        assertEquals(null, snapshot.primaryBottleneck)
    }
}
