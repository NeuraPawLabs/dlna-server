package labs.newrapaw.dlna.probe.ui

import androidx.appcompat.app.AppCompatActivity
import java.io.Closeable
import okhttp3.OkHttpClient
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.platform.ApkUpdater
import labs.newrapaw.dlna.probe.proxy.LocalHlsProxy
import labs.newrapaw.dlna.probe.proxy.SharedPreferencesProxySettingsStore

internal data class MainActivityServices(
    val proxy: LocalHlsProxy,
    val proxySettingsStore: ProxySettingsStore,
    val ssdp: Closeable?,
)

internal fun buildMainActivityServices(
    activity: AppCompatActivity,
    logState: MainActivityLogState,
    playbackCoordinator: MainActivityPlaybackCoordinator,
): MainActivityServices {
    val appHttpClient = OkHttpClient()
    val updater = ApkUpdater(activity, appHttpClient, logState::append)
    val proxySettingsStore = SharedPreferencesProxySettingsStore(
        activity.getSharedPreferences("newrapaw_dlna_settings", AppCompatActivity.MODE_PRIVATE),
    )
    lateinit var proxy: LocalHlsProxy
    val dlnaConfig = buildMainActivityDlnaConfigProvider(
        activity = activity,
        publicBaseUrl = { hostAddress -> proxy.publicBaseUrl(hostAddress) },
    )
    proxy = LocalHlsProxy(
        client = appHttpClient,
        log = logState::append,
        getLogs = logState::snapshot,
        proxySettingsStore = proxySettingsStore,
        dlnaConfig = dlnaConfig,
        onPlayRequested = playbackCoordinator::handlePlayRequest,
        onStopRequested = playbackCoordinator::handleStopRequest,
        onPauseRequested = playbackCoordinator::handlePauseRequest,
        onUpdateRequested = { apkUrl -> updater.downloadAndLaunchInstaller(apkUrl) },
    )
    runCatching { proxy.start() }
        .onFailure { logState.append("Proxy start failed: ${it.message}") }
    val ssdp = startMainActivitySsdp(
        activity = activity,
        dlnaConfig = dlnaConfig,
        appendLog = logState::append,
    )
    return MainActivityServices(
        proxy = proxy,
        proxySettingsStore = proxySettingsStore,
        ssdp = ssdp,
    )
}
