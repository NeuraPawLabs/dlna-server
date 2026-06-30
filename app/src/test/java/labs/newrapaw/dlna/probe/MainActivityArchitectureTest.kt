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
        val ssdpSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivitySsdp.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityDlnaConfig.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityDlnaConfig.kt should exist", helperPath != null)
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(servicesSource.contains("buildMainActivityDlnaConfigProvider("))
        assertTrue(servicesSource.contains("dlnaConfig = dlnaConfig"))
        assertTrue(servicesSource.contains("startMainActivitySsdp("))
        assertFalse(runtimeSource.contains("Settings.Secure.getString("))
        assertFalse(servicesSource.contains("Settings.Secure.getString("))
        assertFalse(runtimeSource.contains("buildDlnaDeviceConfig("))
        assertFalse(runtimeSource.contains("stableRendererUuid("))
        assertTrue(ssdpSource.contains("dlnaConfig: () -> DlnaDeviceConfig?"))
        assertFalse(ssdpSource.contains("Settings.Secure.getString("))
        assertFalse(ssdpSource.contains("buildDlnaDeviceConfig("))
        assertFalse(ssdpSource.contains("stableRendererUuid("))
        assertFalse(ssdpSource.contains("resolveLocalIpAddress("))
    }

    @Test
    fun mainActivityDelegatesPlatformSetupHelpers() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityPlatform.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun keepScreenOn()"))
        assertFalse(activitySource.contains("private fun startRendererForegroundService()"))
        assertTrue(activitySource.contains("requestKeepScreenOn(window)"))
        assertTrue(activitySource.contains("launchRendererForegroundService("))
    }

    @Test
    fun mainActivityDelegatesPlaybackCommands() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityPlaybackCoordinator.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun playUrl("))
        assertFalse(activitySource.contains("private fun stopPlayback("))
        assertFalse(activitySource.contains("private fun pausePlayback("))
        assertFalse(activitySource.contains("private fun postToUi("))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("playbackCoordinator = MainActivityPlaybackCoordinator("))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(servicesSource.contains("onPlayRequested = playbackCoordinator::handlePlayRequest"))
        assertTrue(servicesSource.contains("onStopRequested = playbackCoordinator::handleStopRequest"))
        assertTrue(servicesSource.contains("onPauseRequested = playbackCoordinator::handlePauseRequest"))
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
        assertTrue(servicesSource.contains("startMainActivitySsdp("))
    }

    @Test
    fun mainActivityDelegatesLogStateStorage() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityLogState.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private fun appendLog("))
        assertFalse(activitySource.contains("private fun addLogEntry("))
        assertFalse(activitySource.contains("private fun logSnapshot("))
        assertFalse(activitySource.contains("UiLogBuffer(maxEntries = 1000)"))
        assertTrue(activitySource.contains("MainActivityLogState("))
        assertTrue(activitySource.contains("logState::append"))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(servicesSource.contains("logState::snapshot"))
    }

    @Test
    fun mainActivityDelegatesPlayerRuntimeSetup() {
        val activitySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val helperPath = sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackRuntime.kt").firstOrNull(Files::exists)

        assertTrue("MainActivityPlaybackRuntime.kt should exist", helperPath != null)
        assertFalse(activitySource.contains("private val telemetryHandler = Handler("))
        assertFalse(activitySource.contains("private val telemetryIntervalMs = 500L"))
        assertFalse(activitySource.contains("private val telemetryUpdater = object : Runnable"))
        assertFalse(activitySource.contains("ExoPlayer.Builder(this)"))
        assertFalse(activitySource.contains("DefaultLoadControl.Builder()"))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("buildMainActivityPlayer("))
        assertTrue(runtimeSource.contains("MainActivityPlaybackTelemetry("))
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
        assertTrue(servicesSource.contains("ApkUpdater(activity"))
        assertTrue(servicesSource.contains("SharedPreferencesProxySettingsStore("))
        assertTrue(servicesSource.contains("LocalHlsProxy("))
        assertTrue(servicesSource.contains("startMainActivitySsdp("))
    }

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()
}
