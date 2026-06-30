package labs.newrapaw.dlna.probe.ui

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy
import labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsStatus

class MainActivityPlayerListener(
    private val player: ExoPlayer,
    private val proxy: LocalHlsProxy,
    private val appendLog: (String) -> Unit,
    private val setStatus: (String) -> Unit,
    private val enterFullscreenPlayback: () -> Unit,
    private val exitFullscreenPlayback: () -> Unit,
    private val currentRecoveryAttempts: () -> Int,
    private val currentRecoverySeekPositionMs: () -> Long?,
    private val updateRecoveryState: (Int, Long?) -> Unit,
    private val clearRecoveryState: () -> Unit,
) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        val label = when (playbackState) {
            Player.STATE_IDLE -> "Idle"
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> "Ready"
            Player.STATE_ENDED -> "Ended"
            else -> "Unknown"
        }
        setStatus(label)
        proxy.updatePlaybackStatus(
            when (playbackState) {
                Player.STATE_IDLE -> PlaybackDiagnosticsStatus.IDLE
                Player.STATE_BUFFERING -> PlaybackDiagnosticsStatus.BUFFERING
                Player.STATE_READY -> PlaybackDiagnosticsStatus.PLAYING
                Player.STATE_ENDED -> PlaybackDiagnosticsStatus.STOPPED
                else -> PlaybackDiagnosticsStatus.IDLE
            },
        )
        if (playbackState == Player.STATE_READY) {
            val recoveryTarget = currentRecoverySeekPositionMs()
            if (recoveryTarget != null && player.currentPosition >= recoveryTarget + 3_000L) {
                clearRecoveryState()
            }
        }
        if (playbackState == Player.STATE_ENDED) {
            clearRecoveryState()
        }
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> enterFullscreenPlayback()
            Player.STATE_ENDED -> exitFullscreenPlayback()
            else -> Unit
        }
        proxy.updatePlayerTelemetry(
            positionMs = player.currentPosition.takeIf { it >= 0L },
            bufferedPositionMs = player.bufferedPosition.takeIf { it >= 0L },
            isLoading = player.isLoading,
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        val recovery = decidePlayerErrorRecovery(
            errorCode = error.errorCode,
            currentPositionMs = player.currentPosition.takeIf { it >= 0L },
            durationMs = player.duration.takeIf { it >= 0L },
            attemptCount = currentRecoveryAttempts(),
        )
        if (recovery.shouldRecover && recovery.seekPositionMs != null) {
            updateRecoveryState(recovery.nextAttemptCount, recovery.seekPositionMs)
            setStatus("Buffering")
            proxy.updatePlaybackStatus(PlaybackDiagnosticsStatus.BUFFERING)
            proxy.updatePlaybackError(
                "Recovering from ${error.errorCodeName}: ${error.message}",
            )
            appendLog(
                "Player recoverable error: ${error.errorCodeName}: ${error.message}; " +
                    "skip to ${recovery.seekPositionMs}ms " +
                    "(${recovery.nextAttemptCount}/5)",
            )
            player.seekTo(recovery.seekPositionMs)
            player.prepare()
            player.play()
            return
        }
        clearRecoveryState()
        exitFullscreenPlayback()
        setStatus("Error")
        proxy.updatePlaybackStatus(PlaybackDiagnosticsStatus.FAILED)
        proxy.updatePlaybackError("${error.errorCodeName}: ${error.message}")
        proxy.updatePlayerTelemetry(
            positionMs = player.currentPosition.takeIf { it >= 0L },
            bufferedPositionMs = player.bufferedPosition.takeIf { it >= 0L },
            isLoading = player.isLoading,
        )
        appendLog("Player error: ${error.errorCodeName}: ${error.message}")
    }
}
