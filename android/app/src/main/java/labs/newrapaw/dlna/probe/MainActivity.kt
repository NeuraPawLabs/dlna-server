package labs.newrapaw.dlna.probe

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.ContextCompat
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import okhttp3.OkHttpClient
import java.net.NetworkInterface
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var proxy: LocalHlsProxy
    private lateinit var updater: ApkUpdater
    private var ssdp: SsdpAdvertiser? = null
    private lateinit var rootView: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var statusView: TextView
    private lateinit var instructionView: TextView
    private lateinit var managementUrlView: TextView
    private lateinit var chromeViews: List<View>
    private val logs = ArrayDeque<String>()
    private val logLock = Any()
    private var isFullscreenPlayback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keepScreenOn()
        startRendererForegroundService()
        player = ExoPlayer.Builder(this).build()
        updater = ApkUpdater(this, OkHttpClient(), ::appendLog)
        proxy = LocalHlsProxy(
            client = OkHttpClient(),
            log = ::appendLog,
            getLogs = ::logSnapshot,
            dlnaConfig = ::dlnaDeviceConfig,
            onPlayRequested = { url -> postToUi("play") { playUrl(url) } },
            onStopRequested = { postToUi("stop") { stopPlayback() } },
            onPauseRequested = { postToUi("pause") { pausePlayback() } },
            onUpdateRequested = { apkUrl -> updater.downloadAndLaunchInstaller(apkUrl) },
        )
        runCatching { proxy.start() }
            .onFailure { appendLog("Proxy start failed: ${it.message}") }

        setContentView(buildContentView())
        installBackHandler()
        appendLog("IP: ${localIpAddress()}")
        appendLog("Proxy: ${proxy.baseUrl}")
        appendLog("Open on phone: ${publicControlUrl()}")
        startSsdp()
        setStatus("Idle")
    }

    override fun onDestroy() {
        ssdp?.close()
        player.release()
        proxy.close()
        super.onDestroy()
    }

    private fun buildContentView(): LinearLayout {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val titleView = TextView(this).apply {
            text = "NewraPaw DLNA TV"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        rootView.addView(titleView)

        statusView = TextView(this).apply {
            text = "等待投屏..."
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 40
            }
        }
        rootView.addView(statusView)

        instructionView = TextView(this).apply {
            text = "请在同一网络下的投屏设备中选择本设备"
            textSize = 18f
            setTextColor(0xffcccccc.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 24
            }
        }
        rootView.addView(instructionView)

        managementUrlView = TextView(this).apply {
            text = "管理页面: ${publicControlUrl()}"
            textSize = 15f
            setTextColor(0xff999999.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 64
            }
        }
        rootView.addView(managementUrlView)

        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        playerView.keepScreenOn = true
        rootView.addView(playerView)

        chromeViews = listOf(titleView, statusView, instructionView, managementUrlView)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val label = when (playbackState) {
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_BUFFERING -> "Buffering"
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
                setStatus(label)
                appendLog("Player: $label")
                when (playbackState) {
                    Player.STATE_BUFFERING, Player.STATE_READY -> enterFullscreenPlayback()
                    Player.STATE_ENDED -> exitFullscreenPlayback()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                exitFullscreenPlayback()
                setStatus("Error")
                appendLog("Player error: ${error.errorCodeName}: ${error.message}")
            }
        })

        return rootView
    }

    private fun playUrl(source: String) {
        if (source.isEmpty()) {
            appendLog("Enter a test m3u8 URL first")
            return
        }

        runCatching {
            val playable = resolvePlayableUri(source, proxy.baseUrl)
            appendLog("Play: $playable")
            enterFullscreenPlayback()
            player.setMediaItem(MediaItem.fromUri(playable))
            player.prepare()
            player.play()
        }.onFailure {
            exitFullscreenPlayback()
            setStatus("Play failed")
            appendLog("Play failed: ${it::class.java.simpleName}: ${it.message}")
        }
    }

    private fun stopPlayback() {
        player.stop()
        exitFullscreenPlayback()
        setStatus("Stopped")
        appendLog("Stopped")
    }

    private fun pausePlayback() {
        player.pause()
        setStatus("Paused")
        appendLog("Paused")
    }

    private fun setStatus(status: String) {
        statusView.text = when (status) {
            "Idle", "Stopped" -> "等待投屏..."
            "Buffering" -> "正在缓冲..."
            "Ready", "Playing" -> "正在播放"
            "Paused" -> "已暂停"
            "Error", "Play failed" -> "播放失败"
            "Ended" -> "播放结束"
            else -> status
        }
        managementUrlView.text = "管理页面: ${publicControlUrl()}"
    }

    private fun enterFullscreenPlayback() {
        if (isFullscreenPlayback || !::chromeViews.isInitialized) return

        isFullscreenPlayback = true
        rootView.setBackgroundColor(Color.BLACK)
        playerView.setBackgroundColor(Color.BLACK)
        playerView.visibility = View.VISIBLE
        rootView.setPadding(0, 0, 0, 0)
        rootView.gravity = Gravity.NO_GRAVITY
        chromeViews.forEach { it.visibility = View.GONE }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun exitFullscreenPlayback() {
        if (!isFullscreenPlayback || !::chromeViews.isInitialized) return

        isFullscreenPlayback = false
        playerView.visibility = View.GONE
        rootView.gravity = Gravity.CENTER
        rootView.setPadding(48, 40, 48, 40)
        chromeViews.forEach { it.visibility = View.VISIBLE }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }
    }

    private fun handleBackPressed() {
        if (isFullscreenPlayback) {
            stopPlayback()
            return
        }

        showExitConfirmation()
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("确认退出")
            .setMessage("是否退出 NewraPaw DLNA TV？")
            .setPositiveButton("退出") { _, _ -> finish() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun appendLog(message: String) {
        runCatching {
            runOnUiThread {
                runCatching {
                    addLogEntry(message)
                }
            }
        }
    }

    private fun addLogEntry(message: String): List<String> = synchronized(logLock) {
        logs.addLast(message)
        while (logs.size > 50) logs.removeFirst()
        logs.toList()
    }

    private fun logSnapshot(): List<String> = synchronized(logLock) {
        logs.toList()
    }

    private fun localIpAddress(): String =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress
            ?: "unknown"

    private fun publicControlUrl(): String =
        localIpAddress().takeIf { it != "unknown" }?.let(proxy::publicBaseUrl) ?: "unknown"

    private fun dlnaDeviceConfig(): DlnaDeviceConfig? {
        val ip = localIpAddress().takeIf { it != "unknown" } ?: return null
        return DlnaDeviceConfig(
            baseUrl = proxy.publicBaseUrl(ip),
            deviceName = "NewraPaw DLNA TV",
            uuid = deviceUuid(),
        )
    }

    private fun startSsdp() {
        ssdp = SsdpAdvertiser(this, ::dlnaDeviceConfig, ::appendLog).also { it.start() }
        appendLog("DLNA renderer: NewraPaw DLNA TV")
    }

    private fun startRendererForegroundService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RendererForegroundService::class.java),
            )
        }.onFailure {
            appendLog("Foreground service start failed: ${it.message}")
        }
    }

    private fun postToUi(operation: String, block: () -> Unit) {
        runCatching {
            runOnUiThread {
                runCatching {
                    block()
                }.onFailure {
                    setStatus("Error")
                    appendLog("UI $operation failed: ${it::class.java.simpleName}: ${it.message}")
                }
            }
        }.onFailure {
            appendLog("UI $operation post failed: ${it::class.java.simpleName}: ${it.message}")
        }
    }

    private fun deviceUuid(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return UUID.nameUUIDFromBytes("newrapaw-dlna-$androidId".toByteArray(Charsets.UTF_8)).toString()
    }
}
