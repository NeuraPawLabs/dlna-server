package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLoggingTest {
    @Test
    fun activityUsesThousandEntryLogBuffer() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("UiLogBuffer(maxEntries = 1000)"))
    }

    @Test
    fun activityDoesNotLogEveryPlayerStateTransition() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertFalse(source.contains("""appendLog("Player: ${'$'}label")"""))
    }

    @Test
    fun activityReportsPlayerStateBackToProxyDiagnostics() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")),
            Charsets.UTF_8,
        )
        val coordinatorSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")),
            Charsets.UTF_8,
        )
        val listenerSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlayerListener.kt")),
            Charsets.UTF_8,
        )

        assertTrue(listenerSource.contains("proxy.updatePlaybackStatus("))
        assertTrue(listenerSource.contains("PlaybackDiagnosticsStatus.BUFFERING"))
        assertTrue(listenerSource.contains("PlaybackDiagnosticsStatus.PLAYING"))
        assertTrue(coordinatorSource.contains("PlaybackDiagnosticsStatus.STOPPED"))
        assertTrue(coordinatorSource.contains("PlaybackDiagnosticsStatus.PAUSED"))
        assertTrue(listenerSource.contains("PlaybackDiagnosticsStatus.FAILED"))
        assertTrue(listenerSource.contains("proxy.updatePlaybackError("))
        assertTrue(activitySource.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("MainActivityPlayerListener("))
    }

    @Test
    fun activityConfiguresPlayerBufferingForProxyPlayback() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackRuntime.kt")
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
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt")),
            Charsets.UTF_8,
        )

        assertFalse(activitySource.contains("runOnUiThread {\n                runCatching {\n                    addLogEntry(message)\n                }\n            }"))
        assertFalse(helperSource.contains("runOnUiThread"))
    }
}
