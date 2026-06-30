package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsJsonTest {
    @Test
    fun serializerIncludesNestedDiagnosticsFieldsAndEscapesStrings() {
        val json = buildPlaybackDiagnosticsJson(
            PlaybackDiagnosticsSnapshot.empty().copy(
                sourceUrl = "https://origin.example/master.m3u8?title=\"paw\"",
                lastError = "SocketTimeoutException: line1\nline2",
                recentSegmentSamples = listOf(
                    SegmentSample(
                        url = "seg-1.ts",
                        source = "proxy",
                        elapsedMs = 320,
                        success = false,
                        reason = "timeout",
                    ),
                ),
                slotStates = listOf(
                    SlotDiagnosticsItem(
                        slotIndex = 7,
                        startMs = 28_000L,
                        endMs = 32_000L,
                        state = SlotDiagnosticsState.BLOCKED,
                        videoReady = false,
                        audioReady = true,
                        subtitleReady = false,
                        blockedAssetKinds = listOf(SessionAssetKind.KEY),
                        degradedAssetKinds = emptyList(),
                    ),
                ),
                assetDiagnostics = listOf(
                    AssetDiagnosticsItem(
                        assetId = "video-7",
                        kind = SessionAssetKind.VIDEO_SEGMENT,
                        trackId = "video-main",
                        state = SessionAssetState.FAILED,
                        localReady = false,
                        sizeBytes = 1024,
                        lastElapsedMs = 500,
                        lastSource = "proxy",
                        retryCount = 2,
                        failureReason = "line1\nline2",
                    ),
                ),
                insights = listOf(
                    DiagnosticsInsight(
                        code = "segment_failures",
                        message = "存在连续失败分片",
                        detail = "line1\nline2",
                    ),
                ),
                primaryBottleneck = DiagnosticsInsight(
                    code = "current_slot_blocked",
                    message = "当前播放槽位存在硬依赖阻塞",
                    detail = "缺少密钥",
                ),
                playbackStatus = PlaybackDiagnosticsStatus.FAILED,
                severity = DiagnosticsSeverity.CRITICAL,
                timeoutCount = 1,
                fallbackCount = 2,
                lastFallbackReason = "timeout",
            ),
        )

        assertTrue(json.contains("\"sourceUrl\":\"https://origin.example/master.m3u8?title=\\\"paw\\\"\""))
        assertTrue(json.contains("\"lastError\":\"SocketTimeoutException: line1\\nline2\""))
        assertTrue(json.contains("\"recentSegmentSamples\":[{"))
        assertTrue(json.contains("\"slotStates\":[{"))
        assertTrue(json.contains("\"blockedAssetKinds\":[\"KEY\"]"))
        assertTrue(json.contains("\"assetDiagnostics\":[{"))
        assertTrue(json.contains("\"failureReason\":\"line1\\nline2\""))
        assertTrue(json.contains("\"insights\":[{"))
        assertTrue(json.contains("\"primaryBottleneck\":{\"code\":\"current_slot_blocked\""))
        assertTrue(json.contains("\"timeoutCount\":1"))
        assertTrue(json.contains("\"lastFallbackReason\":\"timeout\""))
    }
}
