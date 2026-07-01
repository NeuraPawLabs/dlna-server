package labs.newrapaw.dlna.probe.ui

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivityPlayerListener(
    private val player: ExoPlayer,
    private val setStatus: (String) -> Unit,
    private val enterFullscreenPlayback: () -> Unit,
    private val exitFullscreenPlayback: () -> Unit,
    private val updatePlaybackKeepScreenOn: (Boolean) -> Unit,
) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        setStatus(
            mainActivityPlaybackStatusLabelFor(
                playbackState = playbackState,
                isPlaying = player.isPlaying,
            ),
        )
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> enterFullscreenPlayback()
            Player.STATE_ENDED -> exitFullscreenPlayback()
            else -> Unit
        }
        updatePlaybackKeepScreenOn(playbackState == Player.STATE_BUFFERING || player.isPlaying)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        setStatus(
            mainActivityPlaybackStatusLabelFor(
                playbackState = player.playbackState,
                isPlaying = isPlaying,
            ),
        )
        updatePlaybackKeepScreenOn(isPlaying || player.playbackState == Player.STATE_BUFFERING)
    }

    override fun onPlayerError(error: PlaybackException) {
        updatePlaybackKeepScreenOn(false)
        exitFullscreenPlayback()
        setStatus("Error")
    }
}

fun mainActivityPlaybackStatusLabelFor(
    playbackState: Int,
    isPlaying: Boolean,
): String =
    when {
        isPlaying -> "Playing"
        playbackState == Player.STATE_BUFFERING -> "Buffering"
        playbackState == Player.STATE_READY -> "Paused"
        playbackState == Player.STATE_ENDED -> "Ended"
        playbackState == Player.STATE_IDLE -> "Idle"
        else -> "Unknown"
    }
