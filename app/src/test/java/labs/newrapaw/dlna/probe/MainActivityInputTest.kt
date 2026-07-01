package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityInputTest {
    @Test
    fun activityHandlesRemoteMediaKeys() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("override fun dispatchKeyEvent(event: KeyEvent): Boolean"))
        assertTrue(activitySource.contains("KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE"))
        assertTrue(activitySource.contains("KeyEvent.KEYCODE_MEDIA_PLAY"))
        assertTrue(activitySource.contains("KeyEvent.KEYCODE_MEDIA_PAUSE"))
        assertTrue(activitySource.contains("KeyEvent.KEYCODE_MEDIA_STOP"))
        assertTrue(activitySource.contains("runtime.playbackCoordinator.handleResumeRequest()"))
        assertTrue(activitySource.contains("runtime.playbackCoordinator.handlePauseRequest()"))
        assertTrue(activitySource.contains("runtime.playbackCoordinator.handleStopRequest()"))
    }
}
