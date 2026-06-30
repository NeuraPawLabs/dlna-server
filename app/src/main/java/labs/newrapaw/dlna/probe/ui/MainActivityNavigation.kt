package labs.newrapaw.dlna.probe.ui

import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

fun installMainActivityBackHandler(
    activity: AppCompatActivity,
    isFullscreenPlayback: () -> Boolean,
    onStopPlayback: () -> Unit,
    onExitActivity: () -> Unit,
) {
    activity.onBackPressedDispatcher.addCallback(activity) {
        if (isFullscreenPlayback()) {
            showPlaybackExitConfirmation(activity, onStopPlayback)
            return@addCallback
        }

        showExitConfirmation(activity, onExitActivity)
    }
}

private fun showPlaybackExitConfirmation(
    activity: AppCompatActivity,
    onStopPlayback: () -> Unit,
) {
    AlertDialog.Builder(activity)
        .setTitle("确认退出播放")
        .setMessage("是否停止播放并返回主页面？")
        .setPositiveButton("停止播放") { _, _ -> onStopPlayback() }
        .setNegativeButton("取消", null)
        .show()
}

private fun showExitConfirmation(
    activity: AppCompatActivity,
    onExitActivity: () -> Unit,
) {
    AlertDialog.Builder(activity)
        .setTitle("确认退出")
        .setMessage("是否退出 PawCast？")
        .setPositiveButton("退出") { _, _ -> onExitActivity() }
        .setNegativeButton("取消", null)
        .show()
}
