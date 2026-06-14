package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStabilityTest {
    @Test
    fun dlnaCallbacksUseSafeUiPosting() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("onPlayRequested = { url -> postToUi(\"play\") { playUrl(url) } }"))
        assertTrue(source.contains("onStopRequested = { postToUi(\"stop\") { stopPlayback() } }"))
        assertTrue(source.contains("onPauseRequested = { postToUi(\"pause\") { pausePlayback() } }"))
    }
}
