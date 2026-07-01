package labs.newrapaw.dlna.probe.ui

import androidx.media3.exoplayer.ExoPlayer
import java.io.Closeable
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.platform.RendererServiceRuntime
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

internal data class MainActivityServices(
    val player: ExoPlayer,
    val proxy: LocalHlsProxy,
    val proxySettingsStore: ProxySettingsStore,
) : Closeable {
    override fun close() = Unit
}

internal fun buildMainActivityServices(
    serviceRuntime: RendererServiceRuntime,
): MainActivityServices {
    return MainActivityServices(
        player = serviceRuntime.player,
        proxy = serviceRuntime.proxy,
        proxySettingsStore = serviceRuntime.proxySettingsStore,
    )
}
