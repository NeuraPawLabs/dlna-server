package labs.newrapaw.dlna.probe.platform

import androidx.media3.common.Player

enum class RendererServiceRestartMode {
    STICKY,
    NOT_STICKY,
}

enum class RendererServiceLifetimeAction {
    KEEP_RUNNING,
    STOP_SERVICE,
}

data class RendererServicePlaybackActivity(
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val playbackState: Int,
    val keepRendererDiscoverable: Boolean,
) {
    fun hasActivePlayback(): Boolean =
        isPlaying ||
            isLoading ||
            playbackState == Player.STATE_READY ||
            playbackState == Player.STATE_BUFFERING

    fun shouldKeepServiceRunning(): Boolean =
        keepRendererDiscoverable || hasActivePlayback()

    fun onLastClientUnbound(): RendererServiceLifetimeAction =
        if (shouldKeepServiceRunning()) {
            RendererServiceLifetimeAction.KEEP_RUNNING
        } else {
            RendererServiceLifetimeAction.STOP_SERVICE
        }
}

fun buildRendererServicePlaybackActivity(
    isPlaying: Boolean,
    isLoading: Boolean,
    playbackState: Int,
    keepRendererDiscoverable: Boolean = true,
): RendererServicePlaybackActivity =
    RendererServicePlaybackActivity(
        isPlaying = isPlaying,
        isLoading = isLoading,
        playbackState = playbackState,
        keepRendererDiscoverable = keepRendererDiscoverable,
    )

fun chooseRendererServiceRestartMode(
    playbackActivity: RendererServicePlaybackActivity,
): RendererServiceRestartMode =
    if (playbackActivity.shouldKeepServiceRunning()) {
        RendererServiceRestartMode.STICKY
    } else {
        RendererServiceRestartMode.NOT_STICKY
    }
