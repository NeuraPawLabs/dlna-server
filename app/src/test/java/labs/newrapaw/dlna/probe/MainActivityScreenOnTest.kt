package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityScreenOnTest {
    @Test
    fun activityKeepsScreenOnOnlyWhilePlaybackIsActive() {
        val shellSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt")),
            Charsets.UTF_8,
        )
        val listenerSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlayerListener.kt")),
            Charsets.UTF_8,
        )
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")),
            Charsets.UTF_8,
        )

        assertFalse(shellSource.contains("playerView.keepScreenOn = true"))
        assertTrue(helperSource.contains("window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(helperSource.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(runtimeSource.contains("updatePlaybackKeepScreenOn ="))
        assertTrue(listenerSource.contains("updatePlaybackKeepScreenOn("))
    }
}
