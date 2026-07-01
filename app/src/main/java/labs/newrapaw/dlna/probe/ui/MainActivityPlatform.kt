package labs.newrapaw.dlna.probe.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import java.io.Closeable
import labs.newrapaw.dlna.probe.platform.RendererForegroundService
import labs.newrapaw.dlna.probe.platform.RendererServiceRuntime

fun updatePlaybackKeepScreenOn(
    window: Window,
    playerView: PlayerView,
    keepScreenOn: Boolean,
) {
    if (keepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    playerView.keepScreenOn = keepScreenOn
}

fun connectRendererForegroundService(
    activity: AppCompatActivity,
    appendLog: (String) -> Unit,
    onConnectionFailed: (String) -> Unit,
    onConnected: (RendererServiceRuntime, Closeable) -> Unit,
) {
    val intent = Intent(activity, RendererForegroundService::class.java)
    runCatching {
        ContextCompat.startForegroundService(
            activity,
            intent,
        )
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? RendererForegroundService.RendererForegroundBinder
                if (binder == null) {
                    val message = "Renderer service bind failed: unexpected binder"
                    appendLog(message)
                    onConnectionFailed(message)
                    runCatching { activity.unbindService(this) }
                    runCatching { activity.stopService(intent) }
                    return
                }
                onConnected(
                    binder.runtime(),
                    Closeable {
                        runCatching { activity.unbindService(this) }
                    },
                )
            }

            override fun onNullBinding(name: ComponentName?) {
                val message = "Renderer service unavailable"
                appendLog(message)
                onConnectionFailed(message)
                runCatching { activity.unbindService(this) }
                runCatching { activity.stopService(intent) }
            }

            override fun onBindingDied(name: ComponentName?) {
                val message = "Renderer service binding died"
                appendLog(message)
                onConnectionFailed(message)
                runCatching { activity.unbindService(this) }
                runCatching { activity.stopService(intent) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                val message = "Renderer service disconnected"
                appendLog(message)
                onConnectionFailed(message)
            }
        }
        val bound = activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            val message = "Renderer service bind failed"
            appendLog(message)
            onConnectionFailed(message)
            runCatching { activity.stopService(intent) }
        }
    }.onFailure {
        val message = "Foreground service start failed: ${it.message}"
        appendLog(message)
        onConnectionFailed(message)
    }
}

fun disconnectRendererForegroundService(connection: Closeable?) {
    connection?.close()
}
