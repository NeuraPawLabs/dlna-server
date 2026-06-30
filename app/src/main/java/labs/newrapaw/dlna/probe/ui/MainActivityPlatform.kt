package labs.newrapaw.dlna.probe.ui

import android.content.Context
import android.content.Intent
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import labs.newrapaw.dlna.probe.platform.RendererForegroundService

fun requestKeepScreenOn(window: Window) {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

fun launchRendererForegroundService(
    context: Context,
    appendLog: (String) -> Unit,
) {
    runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RendererForegroundService::class.java),
        )
    }.onFailure {
        appendLog("Foreground service start failed: ${it.message}")
    }
}
