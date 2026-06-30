package labs.newrapaw.dlna.probe.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import androidx.media3.ui.PlayerView

data class MainActivityHomeContent(
    val statusText: String,
    val instructionText: String,
    val managementText: String,
)

fun renderHomeContent(
    selectedMenuLabel: String,
    selectedProxyDisplayUrl: String?,
    recentLogs: List<String>,
    publicControlUrl: String,
): MainActivityHomeContent =
    when (selectedMenuLabel) {
        "播放" -> MainActivityHomeContent(
            statusText = "等待投屏...",
            instructionText = "请在同一网络下的投屏设备中选择本设备",
            managementText = "管理页面: $publicControlUrl",
        )
        "代理" -> MainActivityHomeContent(
            statusText = "代理",
            instructionText = selectedProxyDisplayUrl ?: "当前为直连",
            managementText = "请在网页管理页中添加或切换代理: $publicControlUrl",
        )
        "日志" -> MainActivityHomeContent(
            statusText = "日志",
            instructionText = recentLogs.takeLast(3).joinToString("\n").ifBlank { "暂无日志" },
            managementText = "完整日志: $publicControlUrl",
        )
        else -> MainActivityHomeContent(
            statusText = "设置",
            instructionText = "缓存、更新和代理配置请使用网页管理页",
            managementText = "管理页面: $publicControlUrl",
        )
    }

fun applyFullscreenChrome(
    rootView: LinearLayout,
    playerView: PlayerView,
    chromeViews: List<View>,
    window: Window,
) {
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

fun restoreWindowedChrome(
    rootView: LinearLayout,
    playerView: PlayerView,
    chromeViews: List<View>,
    window: Window,
) {
    playerView.visibility = View.GONE
    rootView.gravity = Gravity.CENTER_VERTICAL
    rootView.setPadding(32, 32, 48, 32)
    chromeViews.forEach { it.visibility = View.VISIBLE }
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
}
