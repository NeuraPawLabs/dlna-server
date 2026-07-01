package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLoggingTest {
    @Test
    fun activityUsesThousandEntryLogBuffer() {
        val wrapperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt")),
            Charsets.UTF_8,
        )
        val backingSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererLogState.kt")),
            Charsets.UTF_8,
        )

        assertTrue(wrapperSource.contains("RendererLogState()"))
        assertTrue(backingSource.contains("const val MAX_ENTRIES = 1000"))
    }

    @Test
    fun activityDoesNotLogEveryPlayerStateTransition() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertFalse(source.contains("""appendLog("Player: ${'$'}label")"""))
    }

    @Test
    fun activityReportsPlayerStateBackToProxyDiagnostics() {
        val coordinatorSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")),
            Charsets.UTF_8,
        )
        val servicePlaybackSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")),
            Charsets.UTF_8,
        )
        val stateSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackState.kt")),
            Charsets.UTF_8,
        )
        val listenerSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt")),
            Charsets.UTF_8,
        )

        assertTrue(listenerSource.contains("proxy.updatePlaybackStatus("))
        assertTrue(servicePlaybackSource.contains("PlaybackDiagnosticsStatus.BUFFERING"))
        assertTrue(stateSource.contains("PlaybackDiagnosticsStatus.PLAYING"))
        assertTrue(coordinatorSource.contains("applyCommandState(rendererPlayCommandState())"))
        assertTrue(coordinatorSource.contains("applyCommandState(rendererPauseCommandState())"))
        assertTrue(coordinatorSource.contains("applyCommandState(rendererStopCommandState())"))
        assertTrue(listenerSource.contains("PlaybackDiagnosticsStatus.FAILED"))
        assertTrue(listenerSource.contains("proxy.updatePlaybackError("))
        assertTrue(listenerSource.contains("proxy.updateDlnaTransportState("))
        assertTrue(listenerSource.contains("class RendererServicePlayerListener("))
    }

    @Test
    fun activityCanEscalateRepeatedRecoverableErrorsToSessionRebuild() {
        val servicePlaybackSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")),
            Charsets.UTF_8,
        )
        val listenerSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt")),
            Charsets.UTF_8,
        )
        val recoverySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlaybackRecovery.kt")),
            Charsets.UTF_8,
        )

        assertTrue(recoverySource.contains("REBUILD_SESSION"))
        assertTrue(listenerSource.contains("proxy.recoverActivePlaybackSession("))
        assertTrue(listenerSource.contains("player.clearMediaItems()"))
        assertTrue(servicePlaybackSource.contains("proxy().clearActivePlaybackSession()"))
        assertTrue(servicePlaybackSource.contains("applyCommandState(rendererPlayCommandState())"))
        assertTrue(listenerSource.contains("player.setMediaItem("))
    }

    @Test
    fun activityClearsActiveSessionAfterFatalPlayerErrors() {
        val listenerSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayerListener.kt")),
            Charsets.UTF_8,
        )

        assertTrue(listenerSource.contains("proxy.clearActivePlaybackSession()"))
        assertTrue(listenerSource.contains("PlaybackDiagnosticsStatus.FAILED"))
        assertTrue(listenerSource.contains("transportStatus = \"ERROR_OCCURRED\""))
    }

    @Test
    fun activityConfiguresPlayerBufferingForProxyPlayback() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")
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
