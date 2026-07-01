package labs.newrapaw.dlna.probe.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

@UnstableApi
fun buildRendererServicePlayer(context: Context): ExoPlayer =
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

class RendererServicePlayerController(
    private val player: ExoPlayer,
    private val proxyProvider: () -> LocalHlsProxy,
    private val appendLog: (String) -> Unit,
    private val recoveryState: RendererServicePlayerRecoveryState,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun prepareForPlaybackSwitch() {
        runOnPlayerThreadAndWait("prepare playback switch") {
            recoveryState.clear()
            player.stop()
            player.clearMediaItems()
        }
    }

    fun handlePlayRequest(url: String) {
        runOnPlayerThread("play") {
            appendLog("Play: $url")
            recoveryState.clear()
            applyCommandState(rendererPlayCommandState())
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }

    fun handleStopRequest() {
        runOnPlayerThread("stop") {
            recoveryState.clear()
            applyCommandState(rendererStopCommandState())
            player.stop()
            player.clearMediaItems()
            proxy().clearActivePlaybackSession()
            appendLog("Stopped")
        }
    }

    fun handlePauseRequest() {
        runOnPlayerThread("pause") {
            applyCommandState(rendererPauseCommandState())
            player.pause()
            appendLog("Paused")
        }
    }

    fun handlePauseForNetworkLoss() {
        runOnPlayerThread("network pause") {
            applyCommandState(rendererPauseCommandState())
            player.pause()
            appendLog("Paused for network loss")
        }
    }

    fun handleSeekRequest(positionMs: Long) {
        runOnPlayerThread("seek") {
            player.seekTo(positionMs)
            proxy().updateDlnaPosition(positionMs)
            appendLog("Seek: ${positionMs}ms")
        }
    }

    fun handleRecoveredSession(
        recoveredManifestUrl: String,
        positionMs: Long?,
        resumePlayback: Boolean,
    ) {
        runOnPlayerThread("network recovery") {
            recoveryState.clear()
            proxy().updatePlaybackStatus(PlaybackDiagnosticsStatus.BUFFERING)
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(recoveredManifestUrl))
            player.prepare()
            positionMs?.let(player::seekTo)
            if (resumePlayback) {
                player.play()
            }
            appendLog(
                "Recovered session after network change: $recoveredManifestUrl" +
                    (positionMs?.let { " @ ${it}ms" } ?: ""),
            )
        }
    }

    private fun runOnPlayerThread(
        operation: String,
        block: () -> Unit,
    ) {
        if (Looper.myLooper() == handler.looper) {
            runCatching(block).onFailure {
                appendLog("Player $operation failed: ${it::class.java.simpleName}: ${it.message}")
            }
            return
        }
        handler.post {
            runCatching(block).onFailure {
                appendLog("Player $operation failed: ${it::class.java.simpleName}: ${it.message}")
            }
        }
    }

    private fun runOnPlayerThreadAndWait(
        operation: String,
        block: () -> Unit,
    ) {
        if (Looper.myLooper() == handler.looper) {
            runCatching(block).onFailure {
                appendLog("Player $operation failed: ${it::class.java.simpleName}: ${it.message}")
                throw IllegalStateException("Player $operation failed", it)
            }
            return
        }
        val failure = AtomicReference<Throwable?>(null)
        val completion = CountDownLatch(1)
        handler.post {
            runCatching(block).onFailure(failure::set)
            completion.countDown()
        }
        BlockingDispatch.awaitCountDownOrThrow(
            completion = completion,
            operation = operation,
        )
        failure.get()?.let {
            appendLog("Player $operation failed: ${it::class.java.simpleName}: ${it.message}")
            throw IllegalStateException("Player $operation failed", it)
        }
    }

    private fun applyCommandState(update: RendererCommandStateUpdate) {
        proxy().updatePlaybackStatus(update.diagnosticsStatus)
        proxy().updateDlnaTransportState(
            transportState = update.dlnaTransportState,
            positionMs = update.resetPositionMs,
        )
        proxy().updatePlayerTelemetry(
            positionMs = update.resetPositionMs,
            bufferedPositionMs = update.resetPositionMs,
            isLoading = update.isLoading,
        )
    }

    private fun proxy(): LocalHlsProxy = proxyProvider()
}
