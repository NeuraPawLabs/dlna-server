package labs.newrapaw.dlna.probe.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import labs.newrapaw.dlna.probe.R

class RendererForegroundService : Service() {
    private val binder = RendererForegroundBinder()
    private lateinit var runtime: RendererServiceRuntime

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        runCatching { requireRendererServiceRuntime(applicationContext) }
            .onSuccess { runtime = it }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::runtime.isInitialized) {
            stopSelf()
            return START_NOT_STICKY
        }
        return when (chooseRendererServiceRestartMode(runtime.playbackActivity())) {
            RendererServiceRestartMode.STICKY -> START_STICKY
            RendererServiceRestartMode.NOT_STICKY -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (!::runtime.isInitialized) return null
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!::runtime.isInitialized) {
            stopSelf()
            return super.onUnbind(intent)
        }
        if (runtime.playbackActivity().onLastClientUnbound() == RendererServiceLifetimeAction.STOP_SERVICE) {
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) {
            closeRendererServiceRuntime()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "DLNA Renderer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the DLNA renderer discoverable"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PawCast")
            .setContentText("DLNA renderer is running")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private companion object {
        const val CHANNEL_ID = "newrapaw_dlna_renderer"
        const val NOTIFICATION_ID = 1001
    }

    inner class RendererForegroundBinder : Binder() {
        fun runtime(): RendererServiceRuntime = this@RendererForegroundService.runtime
    }
}
