package labs.newrapaw.dlna.probe.ui

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
    fun handlePlayRequest(url: String) {
        postToUi("play") { playUrl(url) }
    }

    fun handleStopRequest() {
        postToUi("stop") { stopPlayback() }
    }

    fun handlePauseRequest() {
        postToUi("pause") { pausePlayback() }
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
        player.stop()
        exitFullscreenPlayback()
        setStatus("Stopped")
        proxyProvider().updatePlaybackStatus(PlaybackDiagnosticsStatus.STOPPED)
        appendLog("Stopped")
    }

    private fun pausePlayback() {
        player.pause()
        setStatus("Paused")
        proxyProvider().updatePlaybackStatus(PlaybackDiagnosticsStatus.PAUSED)
        appendLog("Paused")
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
}
