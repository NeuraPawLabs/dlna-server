@file:androidx.media3.common.util.UnstableApi

package labs.newrapaw.dlna.probe.platform

import android.content.Context
import java.io.Closeable
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy
import labs.newrapaw.dlna.probe.proxy.SharedPreferencesProxySettingsStore
import okhttp3.OkHttpClient

internal fun buildRendererServiceRuntime(context: Context): RendererServiceRuntime {
    val logState = RendererLogState()
    val appHttpClient = OkHttpClient()
    val updater = ApkUpdater(context, appHttpClient, logState::append)
    val proxySettingsStore = SharedPreferencesProxySettingsStore(
        context.getSharedPreferences("newrapaw_dlna_settings", Context.MODE_PRIVATE),
    )
    val player = buildRendererServicePlayer(context)
    val playbackSnapshotReader = RendererServicePlaybackSnapshotReader(player)
    val recoveryState = RendererServicePlayerRecoveryState()
    lateinit var proxy: LocalHlsProxy
    val dlnaConfig = buildRendererServiceDlnaConfigProvider(
        context = context,
        publicBaseUrl = { hostAddress -> proxy.publicBaseUrl(hostAddress) },
    )
    val playbackController = RendererServicePlayerController(
        player = player,
        proxyProvider = { proxy },
        playbackStateProvider = { proxy.playbackState },
        appendLog = logState::append,
        recoveryState = recoveryState,
    )
    proxy = LocalHlsProxy(
        client = appHttpClient,
        log = logState::append,
        getLogs = logState::snapshot,
        proxySettingsStore = proxySettingsStore,
        dlnaConfig = dlnaConfig,
        onPlayRequested = playbackController::handlePlayRequest,
        beforePlaybackSwitch = playbackController::prepareForPlaybackSwitch,
        onStopRequested = playbackController::handleStopRequest,
        onPauseRequested = playbackController::handlePauseRequest,
        onSeekRequested = playbackController::handleSeekRequest,
        onUpdateRequested = { apkUrl -> updater.downloadAndLaunchInstaller(apkUrl) },
    )
    val playerListener = RendererServicePlayerListener(
        player = player,
        proxy = proxy,
        playbackState = proxy.playbackState,
        appendLog = logState::append,
        recoveryState = recoveryState,
    )
    val playbackTelemetry = RendererServicePlaybackTelemetry(
        playbackState = proxy.playbackState,
        player = player,
    )
    val proxyStarted = runCatching {
        proxy.start()
        true
    }.onFailure {
        logState.append("Proxy start failed: ${it.message}")
        runCatching { proxy.close() }
        runCatching { player.release() }
        throw it
    }.getOrDefault(false)
    var ssdp: Closeable? = null
    var networkMonitor: Closeable? = null
    val startedRuntime = runCatching {
        ssdp = if (proxyStarted) {
            startRendererServiceSsdp(
                context = context,
                dlnaConfig = dlnaConfig,
                appendLog = logState::append,
            )
        } else {
            null
        }
        networkMonitor = if (proxyStarted) {
            startRendererServiceNetworkMonitor(
                context = context,
                currentPositionMs = playbackSnapshotReader::currentPositionMs,
                isPlaying = playbackSnapshotReader::isPlaying,
                hasActiveSession = { proxy.playbackState.activeSessionInfo() != null },
                pauseForNetworkLoss = playbackController::handlePauseForNetworkLoss,
                rebuildSessionAfterNetworkRecovery = { positionMs, resumePlayback ->
                    val recoveredManifestUrl = proxy.recoverActivePlaybackSession() ?: return@startRendererServiceNetworkMonitor
                    playbackController.handleRecoveredSession(
                        recoveredManifestUrl = recoveredManifestUrl,
                        positionMs = positionMs,
                        resumePlayback = resumePlayback,
                    )
                },
                appendLog = logState::append,
            )
        } else {
            null
        }
        player.addListener(playerListener)
        playbackTelemetry.start()
        RendererServiceRuntime(
            player = player,
            proxy = proxy,
            proxySettingsStore = proxySettingsStore,
            logState = logState,
            playbackController = playbackController,
            playbackTelemetry = playbackTelemetry,
            playerListener = playerListener,
            ssdp = ssdp,
            networkMonitor = networkMonitor,
        )
    }.onFailure {
        runCatching { playbackTelemetry.stop() }
        runCatching { player.removeListener(playerListener) }
        runCatching { networkMonitor?.close() }
        runCatching { ssdp?.close() }
        runCatching { proxy.close() }
        runCatching { player.release() }
    }.getOrThrow()
    return startedRuntime
}
