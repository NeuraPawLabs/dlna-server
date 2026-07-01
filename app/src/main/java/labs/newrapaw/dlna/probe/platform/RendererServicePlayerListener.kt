@file:androidx.media3.common.util.UnstableApi

package labs.newrapaw.dlna.probe.platform

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

class RendererServicePlayerListener(
    private val player: ExoPlayer,
    private val proxy: LocalHlsProxy,
    private val appendLog: (String) -> Unit,
    private val recoveryState: RendererServicePlayerRecoveryState,
) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        proxy.updatePlaybackStatus(
            playbackDiagnosticsStatusFor(
                playbackState = playbackState,
                isPlaying = player.isPlaying,
            ),
        )
        if (playbackState == Player.STATE_READY) {
            val recoveryTarget = recoveryState.lastSeekPositionMs()
            if (recoveryTarget != null && player.currentPosition >= recoveryTarget + 3_000L) {
                recoveryState.clear()
            }
        }
        if (playbackState == Player.STATE_ENDED) {
            recoveryState.clear()
        }
        proxy.updateDlnaTransportState(
            transportState = dlnaTransportStateFor(playbackState, player.isPlaying),
            positionMs = player.currentPosition.takeIf { it >= 0L },
            durationMs = player.duration.takeIf { it >= 0L },
        )
        proxy.updatePlayerTelemetry(
            positionMs = player.currentPosition.takeIf { it >= 0L },
            bufferedPositionMs = player.bufferedPosition.takeIf { it >= 0L },
            isLoading = player.isLoading,
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        proxy.updatePlaybackStatus(
            playbackDiagnosticsStatusFor(
                playbackState = player.playbackState,
                isPlaying = isPlaying,
            ),
        )
        proxy.updateDlnaTransportState(
            transportState = dlnaTransportStateFor(player.playbackState, isPlaying),
            positionMs = player.currentPosition.takeIf { it >= 0L },
            durationMs = player.duration.takeIf { it >= 0L },
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        val recovery = RendererPlaybackRecoveryDecider.decide(
            errorCode = error.errorCode,
            currentPositionMs = player.currentPosition.takeIf { it >= 0L },
            durationMs = player.duration.takeIf { it >= 0L },
            attemptCount = recoveryState.attemptCount(),
        )
        if (recovery.shouldRecover && recovery.seekPositionMs != null) {
            recoveryState.update(recovery.nextAttemptCount, recovery.seekPositionMs)
            proxy.updatePlaybackStatus(PlaybackDiagnosticsStatus.BUFFERING)
            when (recovery.action) {
                RendererPlaybackRecoveryAction.SEEK -> {
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
                RendererPlaybackRecoveryAction.REBUILD_SESSION -> {
                    val rebuiltUrl = runCatching { proxy.recoverActivePlaybackSession() }
                        .onFailure {
                            appendLog(
                                "Player session rebuild failed: ${it::class.java.simpleName}: ${it.message}",
                            )
                        }
                        .getOrNull()
                    if (!rebuiltUrl.isNullOrBlank()) {
                        proxy.updatePlaybackError(
                            "Rebuilding session after ${error.errorCodeName}: ${error.message}",
                        )
                        appendLog(
                            "Player recoverable error: ${error.errorCodeName}: ${error.message}; " +
                                "rebuild session at ${recovery.seekPositionMs}ms " +
                                "(${recovery.nextAttemptCount}/5)",
                        )
                        player.stop()
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(rebuiltUrl))
                        player.prepare()
                        player.seekTo(recovery.seekPositionMs)
                        player.play()
                        return
                    }
                }
                RendererPlaybackRecoveryAction.FAIL -> Unit
            }
        }
        recoveryState.clear()
        proxy.clearActivePlaybackSession()
        proxy.updatePlaybackStatus(PlaybackDiagnosticsStatus.FAILED)
        proxy.updatePlaybackError("${error.errorCodeName}: ${error.message}")
        proxy.updateDlnaTransportState(
            transportState = "STOPPED",
            transportStatus = "ERROR_OCCURRED",
            positionMs = player.currentPosition.takeIf { it >= 0L },
            durationMs = player.duration.takeIf { it >= 0L },
        )
        proxy.updatePlayerTelemetry(
            positionMs = player.currentPosition.takeIf { it >= 0L },
            bufferedPositionMs = player.bufferedPosition.takeIf { it >= 0L },
            isLoading = player.isLoading,
        )
        appendLog("Player error: ${error.errorCodeName}: ${error.message}")
    }

    private fun dlnaTransportStateFor(
        playbackState: Int,
        isPlaying: Boolean,
    ): String =
        when {
            isPlaying -> "PLAYING"
            playbackState == Player.STATE_BUFFERING -> "TRANSITIONING"
            playbackState == Player.STATE_READY -> "PAUSED_PLAYBACK"
            playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED -> "STOPPED"
            else -> "STOPPED"
        }
}
