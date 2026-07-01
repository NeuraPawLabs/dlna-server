package labs.newrapaw.dlna.probe.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

fun buildMainActivityConnectionStatusView(context: Context): TextView =
    TextView(context).apply {
        text = "正在连接渲染服务..."
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.BLACK)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

fun updateMainActivityConnectionStatus(
    statusView: TextView,
    statusText: String,
) {
    statusView.text = statusText
}
