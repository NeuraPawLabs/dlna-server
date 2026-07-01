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
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxyPlaybackStateBridge

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

internal class RendererServicePlaybackSnapshotReader(
    private val player: ExoPlayer,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun currentPositionMs(): Long? =
        readCurrentPosition().takeIf { it >= 0L }

    fun isPlaying(): Boolean =
        if (Looper.myLooper() == handler.looper) {
            player.isPlaying
        } else {
            readIsPlaying()
        }

    private fun readCurrentPosition(): Long {
        if (Looper.myLooper() == handler.looper) {
            return player.currentPosition
        }
        val result = AtomicReference<Long?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val completion = CountDownLatch(1)
        handler.post {
            runCatching { player.currentPosition }
                .onSuccess(result::set)
                .onFailure(failure::set)
            completion.countDown()
        }
        awaitCountDownOrThrow(completion, "read current position")
        failure.get()?.let {
            throw IllegalStateException("Player read current position failed", it)
        }
        return requireNotNull(result.get())
    }

    private fun readIsPlaying(): Boolean {
        val result = AtomicReference<Boolean?>(null)
        val failure = AtomicReference<Throwable?>(null)
        val completion = CountDownLatch(1)
        handler.post {
            runCatching { player.isPlaying }
                .onSuccess(result::set)
                .onFailure(failure::set)
            completion.countDown()
        }
        awaitCountDownOrThrow(completion, "read is playing")
        failure.get()?.let {
            throw IllegalStateException("Player read is playing failed", it)
        }
        return requireNotNull(result.get())
    }
}

internal class RendererServicePlayerController(
    private val player: ExoPlayer,
    private val proxyProvider: () -> LocalHlsProxy,
    private val playbackStateProvider: () -> LocalHlsProxyPlaybackStateBridge,
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
            playbackState().clearActivePlaybackSession()
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
            playbackState().updatePlaybackStatus(PlaybackDiagnosticsStatus.BUFFERING)
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
        playbackState().updatePlaybackStatus(update.diagnosticsStatus)
        proxy().updateDlnaTransportState(
            transportState = update.dlnaTransportState,
            positionMs = update.resetPositionMs,
        )
        playbackState().updatePlayerTelemetry(
            positionMs = update.resetPositionMs,
            bufferedPositionMs = update.resetPositionMs,
            isLoading = update.isLoading,
        )
    }

    private fun proxy(): LocalHlsProxy = proxyProvider()

    private fun playbackState(): LocalHlsProxyPlaybackStateBridge = playbackStateProvider()
}
