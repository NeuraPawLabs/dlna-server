@file:androidx.media3.common.util.UnstableApi

package labs.newrapaw.dlna.probe.platform

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxyPlaybackStateBridge

internal class RendererServicePlaybackTelemetry(
    private val playbackState: LocalHlsProxyPlaybackStateBridge,
    private val player: ExoPlayer,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val telemetryIntervalMs: Long = 500L,
) {
    private val telemetryUpdater = object : Runnable {
        override fun run() {
            playbackState.updatePlayerTelemetry(
                positionMs = player.currentPosition.takeIf { it >= 0L },
                bufferedPositionMs = player.bufferedPosition.takeIf { it >= 0L },
                isLoading = player.isLoading,
            )
            handler.postDelayed(this, telemetryIntervalMs)
        }
    }

    fun start() {
        handler.post(telemetryUpdater)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }
}
