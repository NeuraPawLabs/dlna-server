package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityArchitectureTest {
    @Test
    fun mainActivityDelegatesEnvironmentAndIdentityHelpers() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val environmentSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityEnvironment.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityEnvironment.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityEnvironment.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun localIpAddress(): String"))
        assertFalse(activitySource.contains("private fun publicControlUrl(): String"))
        assertFalse(activitySource.contains("private fun dlnaDeviceConfig(): DlnaDeviceConfig?"))
        assertFalse(activitySource.contains("private fun deviceUuid(): String"))
        assertTrue(activitySource.contains("resolveLocalIpAddress("))
        assertTrue(activitySource.contains("buildPublicControlUrl("))
        assertTrue(environmentSource.contains("buildDlnaDeviceConfig("))
        assertTrue(environmentSource.contains("stableRendererUuid("))
    }

    @Test
    fun mainActivityDelegatesSharedDlnaConfigProvider() {
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")
        val serviceRuntimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")
        val dlnaConfigSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceDlnaConfig.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceDlnaConfig.kt").firstOrNull(Files::exists)

        assertTrue("RendererServiceDlnaConfig.kt should exist", helperPath != null)
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(serviceRuntimeSource.contains("buildRendererServiceDlnaConfigProvider("))
        assertTrue(serviceRuntimeSource.contains("dlnaConfig = dlnaConfig"))
        assertTrue(serviceRuntimeSource.contains("startRendererServiceSsdp("))
        assertFalse(runtimeSource.contains("Settings.Secure.getString("))
        assertFalse(servicesSource.contains("Settings.Secure.getString("))
        assertFalse(runtimeSource.contains("buildRendererServiceDlnaDeviceConfig("))
        assertFalse(runtimeSource.contains("stableRendererServiceUuid("))
        assertTrue(dlnaConfigSource.contains("publicBaseUrl: (String) -> String"))
        assertFalse(dlnaConfigSource.contains("labs.newrapaw.dlna.probe.ui."))
    }

    @Test
    fun dlnaConfigProvidersUseSharedInstallationIdHelperInsteadOfHardwareId() {
        val activityDlnaSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityDlnaConfig.kt")
        val serviceDlnaSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceDlnaConfig.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererInstallationId.kt").firstOrNull(Files::exists)
        val helperSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererInstallationId.kt")

        assertTrue("RendererInstallationId.kt should exist", helperPath != null)
        assertFalse(activityDlnaSource.contains("Settings.Secure.ANDROID_ID"))
        assertFalse(activityDlnaSource.contains("Settings.Secure.getString("))
        assertFalse(serviceDlnaSource.contains("Settings.Secure.ANDROID_ID"))
        assertFalse(serviceDlnaSource.contains("Settings.Secure.getString("))
        assertTrue(activityDlnaSource.contains("rendererInstallationId("))
        assertTrue(serviceDlnaSource.contains("rendererInstallationId("))
        assertTrue(helperSource.contains("getSharedPreferences("))
    }

    @Test
    fun foregroundServiceOwnsSharedRendererRuntime() {
        val serviceSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererForegroundService.kt")
        val runtimeHelperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt").firstOrNull(Files::exists)
        val lifetimeHelperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceLifetime.kt").firstOrNull(Files::exists)

        assertTrue("RendererServiceRuntime.kt should exist", runtimeHelperPath != null)
        assertTrue("RendererServiceLifetime.kt should exist", lifetimeHelperPath != null)
        assertTrue(serviceSource.contains("requireRendererServiceRuntime("))
        assertTrue(serviceSource.contains("closeRendererServiceRuntime()"))
        assertTrue(serviceSource.contains("private lateinit var runtime: RendererServiceRuntime"))
        assertTrue(serviceSource.contains("runCatching { requireRendererServiceRuntime(applicationContext) }"))
        assertTrue(serviceSource.contains("if (!::runtime.isInitialized) return null"))
        assertTrue(serviceSource.contains("fun runtime(): RendererServiceRuntime = this@RendererForegroundService.runtime"))
        assertTrue(serviceSource.contains("if (!::runtime.isInitialized)"))
        assertTrue(serviceSource.contains("chooseRendererServiceRestartMode(runtime.playbackActivity())"))
        assertTrue(serviceSource.contains("START_NOT_STICKY"))
        assertTrue(serviceSource.contains("override fun onUnbind"))
        assertTrue(serviceSource.contains("stopSelf()"))
    }

    @Test
    fun rendererServiceRuntimeOwnsPlaybackCore() {
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")
        val playbackHelperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt").firstOrNull(Files::exists)
        val telemetryHelperPath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackTelemetry.kt").firstOrNull(Files::exists)
        val listenerHelperPath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt").firstOrNull(Files::exists)
        val networkHelperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceNetworkMonitor.kt").firstOrNull(Files::exists)

        assertTrue("RendererServicePlayback.kt should exist", playbackHelperPath != null)
        assertTrue("RendererServicePlaybackTelemetry.kt should exist", telemetryHelperPath != null)
        assertTrue("RendererServicePlayerListener.kt should exist", listenerHelperPath != null)
        assertTrue("RendererServiceNetworkMonitor.kt should exist", networkHelperPath != null)
        assertTrue(runtimeSource.contains("buildRendererServicePlayer("))
        assertTrue(runtimeSource.contains("RendererServicePlaybackTelemetry("))
        assertTrue(runtimeSource.contains("RendererServicePlayerListener("))
        assertTrue(runtimeSource.contains("startRendererServiceNetworkMonitor("))
        assertTrue(runtimeSource.contains("player.release()"))
    }

    @Test
    fun mainActivityDelegatesPlatformSetupHelpers() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt").firstOrNull(Files::exists)
        val platformSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt")

        assertTrue("MainActivityPlatform.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun keepScreenOn()"))
        assertFalse(activitySource.contains("private fun startRendererForegroundService()"))
        assertFalse(activitySource.contains("requireRendererServiceRuntime("))
        assertTrue(activitySource.contains("connectRendererForegroundService("))
        assertTrue(activitySource.contains("disconnectRendererForegroundService("))
        assertTrue(platformSource.contains("bindService("))
        assertTrue(platformSource.contains("ServiceConnection"))
        assertTrue(platformSource.contains("override fun onNullBinding"))
        assertTrue(runtimeSource.contains("updatePlaybackKeepScreenOn ="))
    }

    @Test
    fun mainActivityDelegatesPlaybackCommands() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt").firstOrNull(Files::exists)
        val coordinatorSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")
        val serviceRuntimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")

        assertTrue("MainActivityPlaybackCoordinator.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun playUrl("))
        assertFalse(activitySource.contains("private fun stopPlayback("))
        assertFalse(activitySource.contains("private fun pausePlayback("))
        assertFalse(activitySource.contains("private fun postToUi("))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("playbackCoordinator = MainActivityPlaybackCoordinator("))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertFalse(servicesSource.contains("registerPlaybackController("))
        assertTrue(serviceRuntimeSource.contains("onPlayRequested = playbackController::handlePlayRequest"))
        assertTrue(serviceRuntimeSource.contains("beforePlaybackSwitch = playbackController::prepareForPlaybackSwitch"))
        assertTrue(serviceRuntimeSource.contains("onStopRequested = playbackController::handleStopRequest"))
        assertTrue(serviceRuntimeSource.contains("onPauseRequested = playbackController::handlePauseRequest"))
        assertTrue(serviceRuntimeSource.contains("onSeekRequested = playbackController::handleSeekRequest"))
        assertTrue(coordinatorSource.contains("fun prepareForPlaybackSwitch("))
        assertTrue(coordinatorSource.contains("fun handleSeekRequest("))
    }

    @Test
    fun mainActivityDelegatesChromeAndHomeContentRendering() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityChrome.kt").firstOrNull(Files::exists)
        val shellSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")

        assertTrue("MainActivityChrome.kt should exist", helperPath != null)
        assertTrue(activitySource.contains("applyFullscreenChrome("))
        assertTrue(activitySource.contains("restoreWindowedChrome("))
        assertTrue(shellSource.contains("renderHomeContent("))
        assertFalse(activitySource.contains("chromeViews.forEach { it.visibility = View.GONE }"))
        assertFalse(activitySource.contains("chromeViews.forEach { it.visibility = View.VISIBLE }"))
        assertFalse(activitySource.contains("instructionView.text = \"请在同一网络下的投屏设备中选择本设备\""))
        assertFalse(activitySource.contains("instructionView.text = proxySettingsStore.load().selectedProxy()?.displayUrl() ?: \"当前为直连\""))
    }

    @Test
    fun mainActivityDelegatesShellAndMenuUi() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityShell.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun buildContentView()"))
        assertFalse(activitySource.contains("private fun menuItem("))
        assertFalse(activitySource.contains("private fun updateMenuItemStyle("))
        assertFalse(activitySource.contains("private fun updateHomeContent()"))
        assertFalse(activitySource.contains("private enum class TvMenuItem"))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("buildMainActivityShell("))
        assertTrue(activitySource.contains("updateTvMenuItemStyle("))
        assertTrue(activitySource.contains("updateMainActivityHomeContent("))
    }

    @Test
    fun mainActivityDelegatesBackNavigationAndExitDialogs() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityNavigation.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityNavigation.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun installBackHandler()"))
        assertFalse(activitySource.contains("private fun handleBackPressed()"))
        assertFalse(activitySource.contains("private fun showPlaybackExitConfirmation()"))
        assertFalse(activitySource.contains("private fun showExitConfirmation()"))
        assertTrue(activitySource.contains("installMainActivityBackHandler("))
    }

    @Test
    fun mainActivityDelegatesSsdpStartup() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt").firstOrNull(Files::exists)
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")

        assertTrue("MainActivityServices.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun startSsdp()"))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(servicesSource.contains("serviceRuntime: RendererServiceRuntime"))
        assertFalse(servicesSource.contains("requireRendererServiceRuntime("))
        assertFalse(servicesSource.contains("registerPlaybackController("))
        assertFalse(servicesSource.contains("startMainActivitySsdp("))
    }

    @Test
    fun mainActivityDelegatesLogStateStorage() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val serviceRuntimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")
        val helperSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityLogState.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun appendLog("))
        assertFalse(activitySource.contains("private fun addLogEntry("))
        assertFalse(activitySource.contains("private fun logSnapshot("))
        assertFalse(activitySource.contains("UiLogBuffer(maxEntries = 1000)"))
        assertTrue(activitySource.contains("private val logState = MainActivityLogState()"))
        assertTrue(activitySource.contains("logState.attach(serviceRuntime.logState)"))
        assertFalse(activitySource.contains("if (::serviceRuntime.isInitialized) {\n            logState.append(message)\n        }"))
        assertTrue(activitySource.contains("appendServiceLog"))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(serviceRuntimeSource.contains("getLogs = logState::snapshot"))
        assertTrue(helperSource.contains("fun attach(serviceLogState: RendererLogState)"))
    }

    @Test
    fun mainActivityDelegatesPlayerRuntimeSetup() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt").firstOrNull(Files::exists)
        val servicePlaybackSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")
        val telemetryPath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackTelemetry.kt").firstOrNull(Files::exists)
        val telemetrySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackTelemetry.kt")

        assertTrue("RendererServicePlayback.kt should exist", helperPath != null)
        assertTrue("RendererServicePlaybackTelemetry.kt should exist", telemetryPath != null)
        assertFalse(activitySource.contains("private val telemetryHandler = Handler("))
        assertFalse(activitySource.contains("private val telemetryIntervalMs = 500L"))
        assertFalse(activitySource.contains("private val telemetryUpdater = object : Runnable"))
        assertFalse(activitySource.contains("ExoPlayer.Builder(this)"))
        assertFalse(activitySource.contains("DefaultLoadControl.Builder()"))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("val player = services.player"))
        assertFalse(runtimeSource.contains("buildMainActivityPlayer("))
        assertFalse(runtimeSource.contains("MainActivityPlaybackTelemetry("))
        assertTrue(servicePlaybackSource.contains("fun buildRendererServicePlayer("))
        assertFalse(servicePlaybackSource.contains("class RendererServicePlaybackTelemetry("))
        assertTrue(telemetrySource.contains("class RendererServicePlaybackTelemetry("))
    }

    @Test
    fun rendererServicePlaybackDelegatesRecoveryStateMachine() {
        val servicePlaybackSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")
        val recoveryPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackRecovery.kt").firstOrNull(Files::exists)
        val recoverySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackRecovery.kt")

        assertTrue("RendererServicePlaybackRecovery.kt should exist", recoveryPath != null)
        assertFalse(servicePlaybackSource.contains("class RendererServicePlayerRecoveryState"))
        assertFalse(servicePlaybackSource.contains("enum class RendererPlaybackRecoveryAction"))
        assertFalse(servicePlaybackSource.contains("data class RendererPlaybackRecoveryDecision"))
        assertFalse(servicePlaybackSource.contains("fun decideRendererPlaybackRecovery("))
        assertTrue(recoverySource.contains("class RendererServicePlayerRecoveryState"))
        assertTrue(recoverySource.contains("enum class RendererPlaybackRecoveryAction"))
        assertTrue(recoverySource.contains("data class RendererPlaybackRecoveryDecision"))
        assertTrue(recoverySource.contains("fun decideRendererPlaybackRecovery("))
    }

    @Test
    fun rendererServicePlaybackDelegatesTelemetryAndListenerHelpers() {
        val servicePlaybackSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")
        val telemetryPath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackTelemetry.kt").firstOrNull(Files::exists)
        val telemetrySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackTelemetry.kt")
        val listenerPath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt").firstOrNull(Files::exists)
        val listenerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt")

        assertTrue("RendererServicePlaybackTelemetry.kt should exist", telemetryPath != null)
        assertTrue("RendererServicePlayerListener.kt should exist", listenerPath != null)
        assertFalse(servicePlaybackSource.contains("class RendererServicePlaybackTelemetry("))
        assertFalse(servicePlaybackSource.contains("class RendererServicePlayerListener("))
        assertTrue(telemetrySource.contains("class RendererServicePlaybackTelemetry("))
        assertTrue(listenerSource.contains("class RendererServicePlayerListener("))
    }

    @Test
    fun rendererServicePlaybackDelegatesCommandStateHelpers() {
        val servicePlaybackSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")
        val statePath =
            sourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackState.kt").firstOrNull(Files::exists)
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackState.kt")

        assertTrue("RendererServicePlaybackState.kt should exist", statePath != null)
        assertFalse(servicePlaybackSource.contains("fun playbackDiagnosticsStatusFor("))
        assertFalse(servicePlaybackSource.contains("data class RendererCommandStateUpdate("))
        assertFalse(servicePlaybackSource.contains("fun rendererPauseCommandState()"))
        assertFalse(servicePlaybackSource.contains("fun rendererPlayCommandState()"))
        assertFalse(servicePlaybackSource.contains("fun rendererStopCommandState()"))
        assertTrue(stateSource.contains("fun playbackDiagnosticsStatusFor("))
        assertTrue(stateSource.contains("data class RendererCommandStateUpdate("))
        assertTrue(stateSource.contains("fun rendererPauseCommandState()"))
        assertTrue(stateSource.contains("fun rendererPlayCommandState()"))
        assertTrue(stateSource.contains("fun rendererStopCommandState()"))
    }

    @Test
    fun mainActivityDelegatesPlayerListenerBehavior() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlayerListener.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityPlayerListener.kt should exist", helperPath != null)
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("player.addListener("))
        assertTrue(runtimeSource.contains("MainActivityPlayerListener("))
        assertTrue(runtimeSource.contains("player.removeListener("))
        assertFalse(activitySource.contains("player.addListener(object : Player.Listener"))
        assertFalse(activitySource.contains("override fun onPlaybackStateChanged"))
        assertFalse(activitySource.contains("override fun onPlayerError"))
    }

    @Test
    fun mainActivityDelegatesRuntimeAssembly() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimePath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityRuntime.kt should exist", runtimePath != null)
        assertFalse(activitySource.contains("ApkUpdater(this, OkHttpClient(), logState::append)"))
        assertFalse(activitySource.contains("SharedPreferencesProxySettingsStore("))
        assertFalse(activitySource.contains("LocalHlsProxy("))
        assertFalse(activitySource.contains("MainActivityPlaybackCoordinator("))
        assertFalse(activitySource.contains("MainActivityPlaybackTelemetry("))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
    }

    @Test
    fun mainActivityRuntimeDelegatesServiceAssembly() {
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityServices.kt should exist", helperPath != null)
        assertTrue(runtimeSource.contains("services = buildMainActivityServices("))
        assertFalse(runtimeSource.contains("ApkUpdater(activity"))
        assertFalse(runtimeSource.contains("SharedPreferencesProxySettingsStore("))
        assertFalse(runtimeSource.contains("LocalHlsProxy("))
        assertFalse(runtimeSource.contains("startMainActivitySsdp("))
        assertFalse(servicesSource.contains("ApkUpdater(activity"))
        assertFalse(servicesSource.contains("SharedPreferencesProxySettingsStore("))
        assertFalse(servicesSource.contains("LocalHlsProxy("))
        assertFalse(servicesSource.contains("startMainActivitySsdp("))
        assertTrue(servicesSource.contains("serviceRuntime: RendererServiceRuntime"))
        assertFalse(servicesSource.contains("requireRendererServiceRuntime("))
        assertFalse(servicesSource.contains("registerPlaybackController("))
    }

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()
}
