package labs.newrapaw.dlna.probe.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import java.io.Closeable
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy

class MainActivityRuntime(
    val player: ExoPlayer,
    val proxy: LocalHlsProxy,
    val proxySettingsStore: ProxySettingsStore,
    val shell: MainActivityShell,
    val playbackCoordinator: MainActivityPlaybackCoordinator,
    private val playbackTelemetry: MainActivityPlaybackTelemetry,
    private val ssdp: Closeable?,
) : Closeable {
    override fun close() {
        ssdp?.close()
        playbackTelemetry.stop()
        player.release()
        proxy.close()
    }
}

fun buildMainActivityRuntime(
    activity: AppCompatActivity,
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
    val player = buildMainActivityPlayer(activity)
    lateinit var services: MainActivityServices
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
    services = buildMainActivityServices(
        activity = activity,
        logState = logState,
        playbackCoordinator = playbackCoordinator,
    )
    val localIpAddress = resolveLocalIpAddress()
    val playbackTelemetry = MainActivityPlaybackTelemetry(
        proxyProvider = { services.proxy },
        playerProvider = { player },
    )
    val publicControlUrl = buildPublicControlUrl(localIpAddress, services.proxy::publicBaseUrl)
    val shell = buildMainActivityShell(
        context = activity,
        player = player,
        publicControlUrl = publicControlUrl,
        selectMenuItem = selectMenuItem,
        onMenuFocusChange = onMenuFocusChange,
    )
    player.addListener(
        MainActivityPlayerListener(
            player = player,
            proxy = services.proxy,
            appendLog = logState::append,
            setStatus = setStatus,
            enterFullscreenPlayback = enterFullscreenPlayback,
            exitFullscreenPlayback = exitFullscreenPlayback,
            currentRecoveryAttempts = currentRecoveryAttempts,
            currentRecoverySeekPositionMs = currentRecoverySeekPositionMs,
            updateRecoveryState = updateRecoveryState,
            clearRecoveryState = clearRecoveryState,
        ),
    )
    playbackTelemetry.start()
    return MainActivityRuntime(
        player = player,
        proxy = services.proxy,
        proxySettingsStore = services.proxySettingsStore,
        shell = shell,
        playbackCoordinator = playbackCoordinator,
        playbackTelemetry = playbackTelemetry,
        ssdp = services.ssdp,
    )
}
