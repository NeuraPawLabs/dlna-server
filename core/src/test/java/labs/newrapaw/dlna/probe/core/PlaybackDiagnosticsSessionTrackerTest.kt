package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsSessionTrackerTest {
    private val tracker = PlaybackDiagnosticsSessionTracker()

    @Test
    fun updateSlotDiagnosticsSortsSlotsAndDerivesCurrentStallReason() {
        val updated = tracker.updateSlotDiagnostics(
            snapshot = PlaybackDiagnosticsSnapshot.empty(),
            slotStates = listOf(
                SlotDiagnosticsItem(
                    slotIndex = 3,
                    startMs = 12_000L,
                    endMs = 16_000L,
                    state = SlotDiagnosticsState.BLOCKED,
                    videoReady = true,
                    audioReady = true,
                    subtitleReady = false,
                    blockedAssetKinds = listOf(SessionAssetKind.KEY),
                    degradedAssetKinds = emptyList(),
                ),
                SlotDiagnosticsItem(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    state = SlotDiagnosticsState.READY,
                    videoReady = true,
                    audioReady = true,
                    subtitleReady = true,
                    blockedAssetKinds = emptyList(),
                    degradedAssetKinds = emptyList(),
                ),
            ),
            currentPlaybackSlotIndex = 3,
            bufferedSlotIndex = 4,
            currentPlaybackSlotReady = false,
            continuousReadySlotCount = 1,
            continuousReadySlotDurationMs = 4_000L,
        )

        assertEquals(listOf(1, 3), updated.slotStates.map { it.slotIndex })
        assertEquals("当前槽位 3 缺少硬依赖：密钥", updated.currentStallReason)
        assertEquals(4, updated.bufferedSlotIndex)
        assertEquals(1, updated.continuousReadySlotCount)
    }

    @Test
    fun updateAssetDiagnosticsSortsAssetsAndRefreshesAssetSummary() {
        val updated = tracker.updateAssetDiagnostics(
            snapshot = PlaybackDiagnosticsSnapshot.empty(),
            assetDiagnostics = listOf(
                AssetDiagnosticsItem(
                    assetId = "video-2",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video",
                    state = SessionAssetState.FAILED,
                    localReady = false,
                    sizeBytes = null,
                    lastElapsedMs = 220L,
                    lastSource = "proxy",
                    retryCount = 2,
                    failureReason = "timeout",
                ),
                AssetDiagnosticsItem(
                    assetId = "audio-1",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "audio",
                    state = SessionAssetState.READY,
                    localReady = true,
                    sizeBytes = 120L,
                    lastElapsedMs = 80L,
                    lastSource = "direct",
                    retryCount = 0,
                    failureReason = null,
                ),
                AssetDiagnosticsItem(
                    assetId = "audio-0",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "audio",
                    state = SessionAssetState.READY,
                    localReady = true,
                    sizeBytes = 80L,
                    lastElapsedMs = 70L,
                    lastSource = "direct",
                    retryCount = 0,
                    failureReason = null,
                ),
            ),
        )

        assertEquals(listOf("audio-0", "audio-1", "video-2"), updated.assetDiagnostics.map { it.assetId })
        assertEquals(2, updated.sessionReadyAssetCount)
        assertEquals(3, updated.sessionTotalAssetCount)
        assertEquals(200L, updated.sessionReadyBytes)
    }
}
