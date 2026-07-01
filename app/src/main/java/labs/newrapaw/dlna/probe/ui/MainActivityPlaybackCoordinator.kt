package labs.newrapaw.dlna.probe.ui

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import labs.newrapaw.dlna.probe.platform.awaitCountDownOrThrow
import labs.newrapaw.dlna.probe.platform.RendererCommandStateUpdate
import labs.newrapaw.dlna.probe.platform.rendererPauseCommandState
import labs.newrapaw.dlna.probe.platform.rendererPlayCommandState
import labs.newrapaw.dlna.probe.platform.rendererStopCommandState
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy
import labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsStatus

class MainActivityPlaybackCoordinator(
    private val runOnUiThread: ((() -> Unit) -> Unit),
    private val player: ExoPlayer,
    private val proxyProvider: () -> LocalHlsProxy,
    private val appendLog: (String) -> Unit,
    private val setStatus: (String) -> Unit,
    private val enterFullscreenPlayback: () -> Unit,
    private val exitFullscreenPlayback: () -> Unit,
    private val resetRecoveryState: () -> Unit,
) {
    fun prepareForPlaybackSwitch() {
        postToUiAndWait("prepare playback switch") {
            resetRecoveryState()
            player.stop()
            player.clearMediaItems()
        }
    }

    fun handlePlayRequest(url: String) {
        postToUi("play") { playUrl(url) }
    }

    fun handleStopRequest() {
        postToUi("stop") { stopPlayback() }
    }

    fun handlePauseRequest() {
        postToUi("pause") { pausePlayback() }
    }

    fun handleResumeRequest() {
        postToUi("resume") { resumePlayback() }
    }

    fun handleSeekRequest(positionMs: Long) {
        postToUi("seek") { seekTo(positionMs) }
    }

    private fun playUrl(source: String) {
        if (source.isEmpty()) {
            appendLog("Enter a test m3u8 URL first")
            return
        }

        runCatching {
            appendLog("Play: $source")
            resetRecoveryState()
            enterFullscreenPlayback()
            setStatus("Buffering")
            applyCommandState(rendererPlayCommandState())
            player.setMediaItem(MediaItem.fromUri(source))
            player.prepare()
            player.play()
        }.onFailure {
            exitFullscreenPlayback()
            setStatus("Play failed")
            appendLog("Play failed: ${it::class.java.simpleName}: ${it.message}")
        }
    }

    private fun stopPlayback() {
        resetRecoveryState()
        applyCommandState(rendererStopCommandState())
        player.stop()
        player.clearMediaItems()
        exitFullscreenPlayback()
        setStatus("Stopped")
        proxyProvider().clearActivePlaybackSession()
        appendLog("Stopped")
    }

    private fun pausePlayback() {
        applyCommandState(rendererPauseCommandState())
        player.pause()
        setStatus("Paused")
        appendLog("Paused")
    }

    private fun resumePlayback() {
        applyCommandState(rendererPlayCommandState())
        player.play()
        setStatus("Buffering")
        appendLog("Resumed")
    }

    private fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        proxyProvider().updateDlnaPosition(positionMs)
        appendLog("Seek: ${positionMs}ms")
    }

    private fun postToUi(operation: String, block: () -> Unit) {
        runCatching {
            runOnUiThread {
                runCatching {
                    block()
                }.onFailure {
                    setStatus("Error")
                    appendLog("UI $operation failed: ${it::class.java.simpleName}: ${it.message}")
                }
            }
        }.onFailure {
            appendLog("UI $operation post failed: ${it::class.java.simpleName}: ${it.message}")
        }
    }

    private fun postToUiAndWait(operation: String, block: () -> Unit) {
        val failure = AtomicReference<Throwable?>(null)
        val completion = CountDownLatch(1)
        runCatching {
            runOnUiThread {
                runCatching {
                    block()
                }.onFailure(failure::set)
                completion.countDown()
            }
            awaitCountDownOrThrow(
                completion = completion,
                operation = operation,
            )
            failure.get()?.let { throw it }
        }.onFailure {
            setStatus("Error")
            appendLog("UI $operation failed: ${it::class.java.simpleName}: ${it.message}")
            throw IllegalStateException("UI $operation failed", it)
        }
    }

    private fun applyCommandState(update: RendererCommandStateUpdate) {
        proxyProvider().updatePlaybackStatus(update.diagnosticsStatus)
        proxyProvider().updateDlnaTransportState(
            transportState = update.dlnaTransportState,
            positionMs = update.resetPositionMs,
        )
        proxyProvider().updatePlayerTelemetry(
            positionMs = update.resetPositionMs,
            bufferedPositionMs = update.resetPositionMs,
            isLoading = update.isLoading,
        )
    }
}
