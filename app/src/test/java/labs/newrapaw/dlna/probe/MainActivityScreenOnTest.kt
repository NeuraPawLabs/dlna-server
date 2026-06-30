package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityScreenOnTest {
    @Test
    fun activityKeepsScreenOnWhileRendererIsOpen() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val shellSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("requestKeepScreenOn(window)"))
        assertTrue(shellSource.contains("playerView.keepScreenOn = true"))
        assertTrue(helperSource.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
    }
}
