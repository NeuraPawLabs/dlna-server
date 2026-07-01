package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityServiceConnectionTest {
    @Test
    fun activityShowsConnectionStatusUiBeforeRendererServiceBinds() {
        val helperPath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityConnectionView.kt")
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val platformSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt")),
            Charsets.UTF_8,
        )
        val helperSource = helperPath.takeIf(Files::exists)?.let {
            String(Files.readAllBytes(it), Charsets.UTF_8)
        }

        assertTrue(activitySource.contains("buildMainActivityConnectionStatusView(this)"))
        assertTrue(activitySource.contains("setContentView(connectionStatusView)"))
        assertTrue(activitySource.contains("onConnectionFailed = ::showRendererServiceConnectionError"))
        assertTrue(activitySource.contains("rendererServiceReady = false"))
        assertTrue(activitySource.contains("if (::runtime.isInitialized) {\n            isFullscreenPlayback = false\n            runCatching { runtime.close() }\n            setContentView(connectionStatusView)\n        }"))
        assertTrue(platformSource.contains("override fun onNullBinding"))
        assertTrue(platformSource.contains("override fun onBindingDied"))
        assertTrue(platformSource.contains("activity.stopService(intent)"))
        assertTrue(platformSource.contains("Renderer service binding died"))
        assertTrue(platformSource.contains("Renderer service unavailable"))
        assertNotNull(helperSource)
        assertTrue(helperSource!!.contains("正在连接渲染服务..."))
        assertTrue(helperSource.contains("fun updateMainActivityConnectionStatus("))
    }

    @Test
    fun activityTearsDownStalePlaybackUiWhenRendererServiceDisconnects() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("private var rendererServiceReady = false"))
        assertTrue(activitySource.contains("rendererServiceReady = true"))
        assertTrue(activitySource.contains("rendererServiceReady = false"))
        assertTrue(activitySource.contains("if (!::runtime.isInitialized || !rendererServiceReady || event.action != KeyEvent.ACTION_DOWN)"))
        assertTrue(activitySource.contains("runCatching { runtime.close() }"))
        assertTrue(activitySource.contains("rendererServiceConnection = null"))
        assertTrue(activitySource.contains("setContentView(connectionStatusView)"))
        assertTrue(activitySource.contains("updateMainActivityConnectionStatus(connectionStatusView, message)"))
    }
}
