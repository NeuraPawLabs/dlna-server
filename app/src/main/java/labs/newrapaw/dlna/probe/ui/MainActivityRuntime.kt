package labs.newrapaw.dlna.probe.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.Closeable
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.platform.RendererServiceRuntime
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

class MainActivityRuntime(
    val player: ExoPlayer,
    val proxy: LocalHlsProxy,
    val proxySettingsStore: ProxySettingsStore,
    val shell: MainActivityShell,
    val playbackCoordinator: MainActivityPlaybackCoordinator,
    private val playerListener: Player.Listener,
    private val services: Closeable,
) : Closeable {
    override fun close() {
        services.close()
        player.removeListener(playerListener)
        shell.playerView.player = null
    }
}

fun buildMainActivityRuntime(
    activity: AppCompatActivity,
    serviceRuntime: RendererServiceRuntime,
    logState: MainActivityLogState,
    setStatus: (String) -> Unit,
    enterFullscreenPlayback: () -> Unit,
    exitFullscreenPlayback: () -> Unit,
    selectMenuItem: (TvMenuItem) -> Unit,
    onMenuFocusChange: (TvMenuItem, android.widget.TextView, Boolean) -> Unit,
    currentRecoveryAttempts: () -> Int,
    currentRecoverySeekPositionMs: () -> Long?,
    updateRecoveryState: (Int, Long?) -> Unit,
    clearRecoveryState: () -> Unit,
): MainActivityRuntime {
    val services = buildMainActivityServices(serviceRuntime)
    val player = services.player
    val playbackCoordinator = MainActivityPlaybackCoordinator(
        runOnUiThread = { block -> activity.runOnUiThread(block) },
        player = player,
        proxyProvider = { services.proxy },
        appendLog = logState::append,
        setStatus = setStatus,
        enterFullscreenPlayback = enterFullscreenPlayback,
        exitFullscreenPlayback = exitFullscreenPlayback,
        resetRecoveryState = clearRecoveryState,
    )
    val localIpAddress = resolveLocalIpAddress()
    val publicControlUrl = buildPublicControlUrl(localIpAddress, services.proxy::publicBaseUrl)
    val shell = buildMainActivityShell(
        context = activity,
        player = player,
        publicControlUrl = publicControlUrl,
        selectMenuItem = selectMenuItem,
        onMenuFocusChange = onMenuFocusChange,
    )
    val updatePlaybackKeepScreenOnState: (Boolean) -> Unit = { keepScreenOn ->
        updatePlaybackKeepScreenOn(
            window = activity.window,
            playerView = shell.playerView,
            keepScreenOn = keepScreenOn,
        )
    }
    updatePlaybackKeepScreenOnState(false)
    val playerListener = MainActivityPlayerListener(
        player = player,
        setStatus = setStatus,
        enterFullscreenPlayback = enterFullscreenPlayback,
        exitFullscreenPlayback = exitFullscreenPlayback,
        updatePlaybackKeepScreenOn = updatePlaybackKeepScreenOnState,
    )
    player.addListener(playerListener)
    return MainActivityRuntime(
        player = player,
        proxy = services.proxy,
        proxySettingsStore = services.proxySettingsStore,
        shell = shell,
        playbackCoordinator = playbackCoordinator,
        playerListener = playerListener,
        services = services,
    )
}
