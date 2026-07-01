package labs.newrapaw.dlna.probe.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.Closeable
import labs.newrapaw.dlna.probe.R
import labs.newrapaw.dlna.probe.platform.RendererServiceRuntime

class MainActivity : AppCompatActivity() {
    private lateinit var runtime: MainActivityRuntime
    private lateinit var serviceRuntime: RendererServiceRuntime
    private lateinit var connectionStatusView: TextView
    private var rendererServiceConnection: Closeable? = null
    private var rendererServiceReady = false
    private var playbackRecoveryAttempts = 0
    private var lastRecoverySeekPositionMs: Long? = null
    private var isFullscreenPlayback = false
    private var selectedMenuItem = TvMenuItem.PLAY
    private val logState = MainActivityLogState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionStatusView = buildMainActivityConnectionStatusView(this)
        setContentView(connectionStatusView)
        connectRendererForegroundService(
            activity = this,
            appendLog = ::appendServiceLog,
            onConnectionFailed = ::showRendererServiceConnectionError,
        ) { connectedRuntime, connection ->
            var connectedActivityRuntime: MainActivityRuntime? = null
            runCatching {
                serviceRuntime = connectedRuntime
                rendererServiceConnection = connection
                logState.attach(serviceRuntime.logState)
                connectedActivityRuntime = buildMainActivityRuntime(
                    activity = this,
                    serviceRuntime = connectedRuntime,
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
                runtime = requireNotNull(connectedActivityRuntime)
                rendererServiceReady = true
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
            }.onFailure { error ->
                rendererServiceReady = false
                if (!::runtime.isInitialized) {
                    runCatching { connectedActivityRuntime?.close() }
                }
                showRendererServiceConnectionError(
                    "Renderer service UI bootstrap failed: ${error::class.java.simpleName}: ${error.message}",
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::runtime.isInitialized || !rendererServiceReady || event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggleRemotePlayback()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                runtime.playbackCoordinator.handleResumeRequest()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                runtime.playbackCoordinator.handlePauseRequest()
                true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                runtime.playbackCoordinator.handleStopRequest()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) {
            runtime.close()
        }
        disconnectRendererForegroundService(rendererServiceConnection)
        rendererServiceConnection = null
        super.onDestroy()
    }

    private fun selectMenuItem(item: TvMenuItem) {
        if (!rendererServiceReady) return
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
        if (!::runtime.isInitialized || !rendererServiceReady) return

        runtime.shell.statusView.text = mainActivityStatusTextFor(status)
        runtime.shell.managementUrlView.text = getString(
            R.string.main_activity_management_url,
            buildPublicControlUrl(resolveLocalIpAddress(), runtime.proxy::publicBaseUrl),
        )
    }

    private fun enterFullscreenPlayback() {
        if (isFullscreenPlayback || !::runtime.isInitialized || !rendererServiceReady) return

        isFullscreenPlayback = true
        applyFullscreenChrome(
            rootView = runtime.shell.rootView,
            playerView = runtime.shell.playerView,
            chromeViews = runtime.shell.chromeViews,
            window = window,
        )
    }

    private fun exitFullscreenPlayback() {
        if (!isFullscreenPlayback || !::runtime.isInitialized || !rendererServiceReady) return

        isFullscreenPlayback = false
        restoreWindowedChrome(
            rootView = runtime.shell.rootView,
            playerView = runtime.shell.playerView,
            chromeViews = runtime.shell.chromeViews,
            window = window,
            focusTarget = runtime.shell.menuItemViews[selectedMenuItem],
        )
    }

    private fun toggleRemotePlayback() {
        if (!rendererServiceReady) return
        if (runtime.player.isPlaying) {
            runtime.playbackCoordinator.handlePauseRequest()
        } else {
            runtime.playbackCoordinator.handleResumeRequest()
        }
    }

    private fun appendServiceLog(message: String) {
        logState.append(message)
    }

    private fun showRendererServiceConnectionError(message: String) {
        logState.append(message)
        rendererServiceReady = false
        disconnectRendererForegroundService(rendererServiceConnection)
        rendererServiceConnection = null
        if (::runtime.isInitialized) {
            isFullscreenPlayback = false
            runCatching { runtime.close() }
            setContentView(connectionStatusView)
        }
        if (::connectionStatusView.isInitialized) {
            updateMainActivityConnectionStatus(connectionStatusView, message)
        }
    }
}

fun mainActivityStatusTextFor(status: String): String =
    when (status) {
        "Idle", "Stopped" -> "等待投屏..."
        "Buffering" -> "正在缓冲..."
        "Playing" -> "正在播放"
        "Paused" -> "已暂停"
        "Error", "Play failed" -> "播放失败"
        "Ended" -> "播放结束"
        else -> status
    }
