package labs.newrapaw.dlna.probe

import androidx.media3.common.Player
import labs.newrapaw.dlna.probe.ui.mainActivityPlaybackStatusLabelFor
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityPlayerListenerStateTest {
    @Test
    fun readyButNotPlayingMapsToPausedLabel() {
        assertEquals(
            "Paused",
            mainActivityPlaybackStatusLabelFor(
                playbackState = Player.STATE_READY,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun readyAndPlayingMapsToPlayingLabel() {
        assertEquals(
            "Playing",
            mainActivityPlaybackStatusLabelFor(
                playbackState = Player.STATE_READY,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun bufferingMapsToBufferingLabel() {
        assertEquals(
            "Buffering",
            mainActivityPlaybackStatusLabelFor(
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
            ),
        )
    }
}
