package labs.newrapaw.dlna.probe.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

fun buildMainActivityPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    120_000,
                    1_000,
                    2_000,
                )
                .build(),
        )
        .build()

class MainActivityPlaybackTelemetry(
    private val proxyProvider: () -> LocalHlsProxy,
    private val playerProvider: () -> ExoPlayer,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val telemetryIntervalMs: Long = 500L,
) {
    private val telemetryUpdater = object : Runnable {
        override fun run() {
            val player = playerProvider()
            proxyProvider().updatePlayerTelemetry(
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
