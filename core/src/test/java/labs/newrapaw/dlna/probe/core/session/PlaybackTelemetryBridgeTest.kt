package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTelemetryBridgeTest {
    @Test
    fun telemetryMapsPlayerPositionToPlayAndBufferSlots() {
        val bridge = PlaybackTelemetryBridge(
            slots = listOf(
                TimelineSlot(0, 0, 4_000, "video-0"),
                TimelineSlot(1, 4_000, 8_000, "video-1"),
                TimelineSlot(2, 8_000, 12_000, "video-2"),
            ),
        )

        val snapshot = bridge.snapshot(
            currentPositionMs = 4_100L,
            bufferedPositionMs = 9_500L,
            isLoading = true,
        )

        assertEquals(1, snapshot.playHeadSlotIndex)
        assertEquals(2, snapshot.bufferHeadSlotIndex)
        assertEquals(true, snapshot.isLoading)
    }
}
