package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.RendererCommandStateUpdate
import labs.newrapaw.dlna.probe.platform.rendererPlayCommandState
import labs.newrapaw.dlna.probe.platform.rendererPauseCommandState
import labs.newrapaw.dlna.probe.platform.rendererStopCommandState
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RendererServiceCommandStateTest {
    @Test
    fun playCommandImmediatelyPublishesBufferingState() {
        val update = rendererPlayCommandState()

        assertEquals(PlaybackDiagnosticsStatus.BUFFERING, update.diagnosticsStatus)
        assertEquals("TRANSITIONING", update.dlnaTransportState)
        assertNull(update.resetPositionMs)
        assertEquals(true, update.isLoading)
    }

    @Test
    fun pauseCommandImmediatelyPublishesPausedState() {
        assertEquals(
            RendererCommandStateUpdate(
                diagnosticsStatus = PlaybackDiagnosticsStatus.PAUSED,
                dlnaTransportState = "PAUSED_PLAYBACK",
                resetPositionMs = null,
                isLoading = false,
            ),
            rendererPauseCommandState(),
        )
    }

    @Test
    fun stopCommandImmediatelyPublishesStoppedStateAndResetsPosition() {
        val update = rendererStopCommandState()

        assertEquals(PlaybackDiagnosticsStatus.STOPPED, update.diagnosticsStatus)
        assertEquals("STOPPED", update.dlnaTransportState)
        assertEquals(0L, update.resetPositionMs)
        assertFalse(update.isLoading)
    }
}
