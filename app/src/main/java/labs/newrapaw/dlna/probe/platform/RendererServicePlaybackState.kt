package labs.newrapaw.dlna.probe.platform

import androidx.media3.common.Player
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus

fun playbackDiagnosticsStatusFor(
    playbackState: Int,
    isPlaying: Boolean,
): PlaybackDiagnosticsStatus =
    when {
        isPlaying -> PlaybackDiagnosticsStatus.PLAYING
        playbackState == Player.STATE_BUFFERING -> PlaybackDiagnosticsStatus.BUFFERING
        playbackState == Player.STATE_READY -> PlaybackDiagnosticsStatus.PAUSED
        playbackState == Player.STATE_ENDED -> PlaybackDiagnosticsStatus.STOPPED
        else -> PlaybackDiagnosticsStatus.IDLE
    }

data class RendererCommandStateUpdate(
    val diagnosticsStatus: PlaybackDiagnosticsStatus,
    val dlnaTransportState: String,
    val resetPositionMs: Long?,
    val isLoading: Boolean,
)

fun rendererPauseCommandState(): RendererCommandStateUpdate =
    RendererCommandStateUpdate(
        diagnosticsStatus = PlaybackDiagnosticsStatus.PAUSED,
        dlnaTransportState = "PAUSED_PLAYBACK",
        resetPositionMs = null,
        isLoading = false,
    )

fun rendererPlayCommandState(): RendererCommandStateUpdate =
    RendererCommandStateUpdate(
        diagnosticsStatus = PlaybackDiagnosticsStatus.BUFFERING,
        dlnaTransportState = "TRANSITIONING",
        resetPositionMs = null,
        isLoading = true,
    )

fun rendererStopCommandState(): RendererCommandStateUpdate =
    RendererCommandStateUpdate(
        diagnosticsStatus = PlaybackDiagnosticsStatus.STOPPED,
        dlnaTransportState = "STOPPED",
        resetPositionMs = 0L,
        isLoading = false,
    )
