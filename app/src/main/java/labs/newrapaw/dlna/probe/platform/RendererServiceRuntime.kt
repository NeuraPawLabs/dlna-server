@file:androidx.media3.common.util.UnstableApi

package labs.newrapaw.dlna.probe.platform

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import java.io.Closeable
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

class RendererServiceRuntime internal constructor(
    val player: ExoPlayer,
    val proxy: LocalHlsProxy,
    val proxySettingsStore: ProxySettingsStore,
    val logState: RendererLogState,
    private val playbackController: RendererServicePlayerController,
    private val playbackTelemetry: RendererServicePlaybackTelemetry,
    private val playerListener: RendererServicePlayerListener,
    private val ssdp: Closeable?,
    private val networkMonitor: Closeable?,
) : Closeable {
    fun playbackActivity(): RendererServicePlaybackActivity =
        buildRendererServicePlaybackActivity(
            isPlaying = player.isPlaying,
            isLoading = player.isLoading,
            playbackState = player.playbackState,
            keepRendererDiscoverable = true,
        )

    fun prepareForPlaybackSwitch() {
        playbackController.prepareForPlaybackSwitch()
    }

    fun handlePlayRequest(url: String) {
        playbackController.handlePlayRequest(url)
    }

    fun handleStopRequest() {
        playbackController.handleStopRequest()
    }

    fun handlePauseRequest() {
        playbackController.handlePauseRequest()
    }

    fun handleSeekRequest(positionMs: Long) {
        playbackController.handleSeekRequest(positionMs)
    }

    override fun close() {
        playbackTelemetry.stop()
        player.removeListener(playerListener)
        networkMonitor?.close()
        player.release()
        ssdp?.close()
        proxy.close()
    }
}

fun requireRendererServiceRuntime(context: Context): RendererServiceRuntime =
    RendererServiceRuntimeStore.require(context.applicationContext)

fun closeRendererServiceRuntime() {
    RendererServiceRuntimeStore.close()
}

private object RendererServiceRuntimeStore {
    private val lock = Any()
    private var runtime: RendererServiceRuntime? = null

    fun require(context: Context): RendererServiceRuntime = synchronized(lock) {
        runtime ?: buildRendererServiceRuntime(context).also { runtime = it }
    }

    fun close() = synchronized(lock) {
        runtime?.close()
        runtime = null
    }
}
