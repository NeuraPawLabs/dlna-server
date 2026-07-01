package labs.newrapaw.dlna.probe.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaRendererStateTest {
    @Test
    fun snapshotReflectsAtomicTransportAndMediaUpdates() {
        val state = DlnaRendererState()

        state.updateMedia(
            currentUri = "https://example.com/video.m3u8",
            currentUriMetadata = "<meta />",
            transportState = "STOPPED",
            transportStatus = "OK",
            relativeTimePosition = "00:00:00",
        )
        state.updateTransport(
            transportState = "PLAYING",
            transportStatus = "OK",
            relativeTimePosition = "00:01:05",
        )

        val snapshot = state.snapshot()

        assertEquals("https://example.com/video.m3u8", snapshot.currentUri)
        assertEquals("<meta />", snapshot.currentUriMetadata)
        assertEquals("PLAYING", snapshot.transportState)
        assertEquals("OK", snapshot.transportStatus)
        assertEquals("00:01:05", snapshot.relativeTimePosition)
    }

    @Test
    fun playbackAndRenderingControlsShareSynchronizedStateSnapshot() {
        val state = DlnaRendererState()

        state.updateRendering(volume = 75, muted = true)

        val snapshot = state.snapshot()

        assertEquals(75, snapshot.volume)
        assertTrue(snapshot.muted)
    }
}
