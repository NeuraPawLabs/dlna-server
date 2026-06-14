package labs.newrapaw.dlna.probe

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var proxy: LocalHlsProxy
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var urlInput: EditText
    private val logs = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()
        proxy = LocalHlsProxy(
            client = OkHttpClient(),
            log = ::appendLog,
            onPlayRequested = { url -> runOnUiThread { playUrl(url) } },
            onStopRequested = { runOnUiThread { stopPlayback() } },
        )
        runCatching { proxy.start() }
            .onFailure { appendLog("Proxy start failed: ${it.message}") }

        setContentView(buildContentView())
        appendLog("IP: ${localIpAddress()}")
        appendLog("Proxy: ${proxy.baseUrl}")
        appendLog("Open on phone: ${publicControlUrl()}")
        setStatus("Idle")
    }

    override fun onDestroy() {
        player.release()
        proxy.close()
        super.onDestroy()
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(TextView(this).apply {
            text = "NewraPaw DLNA Probe"
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        statusView = TextView(this).apply { textSize = 18f }
        root.addView(statusView)

        urlInput = EditText(this).apply {
            hint = "m3u8 test URL"
            setSingleLine(false)
            minLines = 2
            setText(ProbeConfig.DefaultTestUrl)
        }
        root.addView(urlInput)

        val playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(playerView)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttonRow.addView(Button(this).apply {
            text = "Play Test Stream"
            setOnClickListener { playTestStream() }
        })
        buttonRow.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopPlayback() }
        })
        root.addView(buttonRow)

        logView = TextView(this).apply {
            textSize = 14f
            movementMethod = ScrollingMovementMethod()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                220,
            )
        }
        root.addView(logView)

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
            }

            override fun onPlayerError(error: PlaybackException) {
                setStatus("Error")
                appendLog("Player error: ${error.errorCodeName}: ${error.message}")
            }
        })

        return root
    }

    private fun playTestStream() {
        playUrl(urlInput.text.toString().trim())
    }

    private fun playUrl(source: String) {
        if (source.isEmpty()) {
            appendLog("Enter a test m3u8 URL first")
            return
        }

        val playable = resolvePlayableUri(source, proxy.baseUrl)
        appendLog("Play: $playable")
        player.setMediaItem(MediaItem.fromUri(playable))
        player.prepare()
        player.play()
    }

    private fun stopPlayback() {
        player.stop()
        setStatus("Stopped")
        appendLog("Stopped")
    }

    private fun setStatus(status: String) {
        statusView.text = "Status: $status    Open on phone: ${publicControlUrl()}    Playback proxy: ${proxy.baseUrl}"
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            logs.addLast(message)
            while (logs.size > 50) logs.removeFirst()
            if (::logView.isInitialized) {
                logView.text = logs.joinToString("\n")
            }
        }
    }

    private fun localIpAddress(): String =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress
            ?: "unknown"

    private fun publicControlUrl(): String =
        localIpAddress().takeIf { it != "unknown" }?.let(proxy::publicBaseUrl) ?: "unknown"
}
