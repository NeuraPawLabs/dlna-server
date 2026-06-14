package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityScreenOnTest {
    @Test
    fun activityKeepsScreenOnWhileRendererIsOpen() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(source.contains("playerView.keepScreenOn = true"))
    }
}
