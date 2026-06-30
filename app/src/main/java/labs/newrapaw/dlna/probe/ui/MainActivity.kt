package labs.newrapaw.dlna.probe.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var runtime: MainActivityRuntime
    private val logState = MainActivityLogState()
    private var playbackRecoveryAttempts = 0
    private var lastRecoverySeekPositionMs: Long? = null
    private var isFullscreenPlayback = false
    private var selectedMenuItem = TvMenuItem.PLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestKeepScreenOn(window)
        launchRendererForegroundService(this, logState::append)
        runtime = buildMainActivityRuntime(
            activity = this,
            logState = logState,
            setStatus = ::setStatus,
            enterFullscreenPlayback = ::enterFullscreenPlayback,
            exitFullscreenPlayback = ::exitFullscreenPlayback,
            selectMenuItem = ::selectMenuItem,
            onMenuFocusChange = { item, view, hasFocus ->
                updateTvMenuItemStyle(selectedMenuItem, item, view, hasFocus)
            },
            currentRecoveryAttempts = { playbackRecoveryAttempts },
            currentRecoverySeekPositionMs = { lastRecoverySeekPositionMs },
            updateRecoveryState = { attemptCount, seekPositionMs ->
                playbackRecoveryAttempts = attemptCount
                lastRecoverySeekPositionMs = seekPositionMs
            },
            clearRecoveryState = {
                playbackRecoveryAttempts = 0
                lastRecoverySeekPositionMs = null
            },
        )
        setContentView(runtime.shell.rootView)
        selectMenuItem(TvMenuItem.PLAY)
        installMainActivityBackHandler(
            activity = this,
            isFullscreenPlayback = { isFullscreenPlayback },
            onStopPlayback = runtime.playbackCoordinator::handleStopRequest,
            onExitActivity = ::finish,
        )
        logState.append("IP: ${resolveLocalIpAddress()}")
        logState.append("Proxy: ${runtime.proxy.baseUrl}")
        logState.append("Open on phone: ${buildPublicControlUrl(resolveLocalIpAddress(), runtime.proxy::publicBaseUrl)}")
        setStatus("Idle")
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) {
            runtime.close()
        }
        super.onDestroy()
    }

    private fun selectMenuItem(item: TvMenuItem) {
        selectedMenuItem = item
        runtime.shell.menuItemViews.forEach { (menuItem, view) ->
            updateTvMenuItemStyle(selectedMenuItem, menuItem, view, view.hasFocus())
        }
        updateMainActivityHomeContent(
            shell = runtime.shell,
            selectedMenuItem = selectedMenuItem,
            selectedProxyDisplayUrl = runtime.proxySettingsStore.load().selectedProxy()?.displayUrl(),
            recentLogs = logState.snapshot(),
            publicControlUrl = buildPublicControlUrl(resolveLocalIpAddress(), runtime.proxy::publicBaseUrl),
            isFullscreenPlayback = isFullscreenPlayback,
        )
    }

    private fun setStatus(status: String) {
        if (!::runtime.isInitialized) return

        runtime.shell.statusView.text = when (status) {
            "Idle", "Stopped" -> "等待投屏..."
            "Buffering" -> "正在缓冲..."
            "Ready", "Playing" -> "正在播放"
            "Paused" -> "已暂停"
            "Error", "Play failed" -> "播放失败"
            "Ended" -> "播放结束"
            else -> status
        }
        runtime.shell.managementUrlView.text = "管理页面: ${buildPublicControlUrl(resolveLocalIpAddress(), runtime.proxy::publicBaseUrl)}"
    }

    private fun enterFullscreenPlayback() {
        if (isFullscreenPlayback || !::runtime.isInitialized) return

        isFullscreenPlayback = true
        applyFullscreenChrome(
            rootView = runtime.shell.rootView,
            playerView = runtime.shell.playerView,
            chromeViews = runtime.shell.chromeViews,
            window = window,
        )
    }

    private fun exitFullscreenPlayback() {
        if (!isFullscreenPlayback || !::runtime.isInitialized) return

        isFullscreenPlayback = false
        restoreWindowedChrome(
            rootView = runtime.shell.rootView,
            playerView = runtime.shell.playerView,
            chromeViews = runtime.shell.chromeViews,
            window = window,
        )
    }
}
