package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLoggingTest {
    @Test
    fun activityUsesThousandEntryLogBuffer() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("UiLogBuffer(maxEntries = 1000)"))
    }

    @Test
    fun activityDoesNotLogEveryPlayerStateTransition() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertFalse(source.contains("""appendLog("Player: ${'$'}label")"""))
    }

    @Test
    fun activityReportsPlayerStateBackToProxyDiagnostics() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("proxy.updatePlaybackStatus("))
        assertTrue(source.contains("PlaybackDiagnosticsStatus.BUFFERING"))
        assertTrue(source.contains("PlaybackDiagnosticsStatus.PLAYING"))
        assertTrue(source.contains("PlaybackDiagnosticsStatus.STOPPED"))
        assertTrue(source.contains("PlaybackDiagnosticsStatus.PAUSED"))
        assertTrue(source.contains("PlaybackDiagnosticsStatus.FAILED"))
        assertTrue(source.contains("proxy.updatePlaybackError("))
    }

    @Test
    fun activityConfiguresPlayerBufferingForProxyPlayback() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("DefaultLoadControl.Builder()"))
        assertTrue(source.contains("setBufferDurationsMs("))
        assertTrue(source.contains("15_000"))
        assertTrue(source.contains("120_000"))
        assertTrue(source.contains("1_000"))
        assertTrue(source.contains("2_000"))
    }

    @Test
    fun activityDoesNotPostEveryLogEntryOntoUiThread() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertFalse(source.contains("runOnUiThread {\n                runCatching {\n                    addLogEntry(message)\n                }\n            }"))
    }
}
