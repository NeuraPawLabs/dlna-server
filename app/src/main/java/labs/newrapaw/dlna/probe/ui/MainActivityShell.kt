package labs.newrapaw.dlna.probe.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

data class MainActivityShell(
    val rootView: LinearLayout,
    val playerView: PlayerView,
    val statusView: TextView,
    val instructionView: TextView,
    val managementUrlView: TextView,
    val chromeViews: List<View>,
    val menuItemViews: Map<TvMenuItem, TextView>,
)

enum class TvMenuItem(val label: String) {
    PLAY("播放"),
    PROXY("代理"),
    LOGS("日志"),
    SETTINGS("设置"),
}

fun buildMainActivityShell(
    context: Context,
    player: ExoPlayer,
    publicControlUrl: String,
    selectMenuItem: (TvMenuItem) -> Unit,
    onMenuFocusChange: (TvMenuItem, TextView, Boolean) -> Unit,
): MainActivityShell {
    val menuItemViews = linkedMapOf<TvMenuItem, TextView>()
    val rootView = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.BLACK)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(32, 32, 48, 32)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    val menuView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            220,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    rootView.addView(menuView)

    val titleView = TextView(context).apply {
        text = "PawCast"
        textSize = 22f
        setTextColor(Color.WHITE)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = 42
        }
    }
    menuView.addView(titleView)

    TvMenuItem.entries.forEach { item ->
        val itemView = buildTvMenuItemView(
            context = context,
            item = item,
            selectMenuItem = selectMenuItem,
            onMenuFocusChange = onMenuFocusChange,
        )
        menuItemViews[item] = itemView
        menuView.addView(itemView)
    }

    val contentView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f,
        )
    }
    rootView.addView(contentView)

    val statusView = TextView(context).apply {
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
    contentView.addView(statusView)

    val instructionView = TextView(context).apply {
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
    contentView.addView(instructionView)

    val managementUrlView = TextView(context).apply {
        text = "管理页面: $publicControlUrl"
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
    contentView.addView(managementUrlView)

    val playerView = PlayerView(context).apply {
        this.player = player
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    playerView.keepScreenOn = true
    contentView.addView(playerView)

    return MainActivityShell(
        rootView = rootView,
        playerView = playerView,
        statusView = statusView,
        instructionView = instructionView,
        managementUrlView = managementUrlView,
        chromeViews = listOf(menuView, statusView, instructionView, managementUrlView),
        menuItemViews = menuItemViews,
    )
}

fun updateTvMenuItemStyle(
    selectedMenuItem: TvMenuItem,
    item: TvMenuItem,
    view: TextView,
    hasFocus: Boolean,
) {
    val selected = selectedMenuItem == item
    view.text = if (selected) "▶ ${item.label}" else item.label
    view.textSize = if (selected || hasFocus) 22f else 18f
    view.setTextColor(
        when {
            selected -> Color.WHITE
            hasFocus -> 0xffeeeeee.toInt()
            else -> 0xffaaaaaa.toInt()
        },
    )
    view.setBackgroundColor(
        when {
            hasFocus -> 0xff333333.toInt()
            selected -> 0xff1f1f1f.toInt()
            else -> Color.TRANSPARENT
        },
    )
}

fun updateMainActivityHomeContent(
    shell: MainActivityShell,
    selectedMenuItem: TvMenuItem,
    selectedProxyDisplayUrl: String?,
    recentLogs: List<String>,
    publicControlUrl: String,
    isFullscreenPlayback: Boolean,
) {
    if (isFullscreenPlayback) return

    val homeContent = renderHomeContent(
        selectedMenuLabel = selectedMenuItem.label,
        selectedProxyDisplayUrl = selectedProxyDisplayUrl,
        recentLogs = recentLogs,
        publicControlUrl = publicControlUrl,
    )
    shell.statusView.text = homeContent.statusText
    shell.instructionView.text = homeContent.instructionText
    shell.managementUrlView.text = homeContent.managementText
}

private fun buildTvMenuItemView(
    context: Context,
    item: TvMenuItem,
    selectMenuItem: (TvMenuItem) -> Unit,
    onMenuFocusChange: (TvMenuItem, TextView, Boolean) -> Unit,
): TextView =
    TextView(context).apply {
        text = item.label
        textSize = 18f
        setTextColor(0xffaaaaaa.toInt())
        gravity = Gravity.START
        isFocusable = true
        isClickable = true
        setPadding(14, 10, 14, 10)
        setOnClickListener { selectMenuItem(item) }
        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) selectMenuItem(item) else onMenuFocusChange(item, view as TextView, false)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = 24
        }
    }
