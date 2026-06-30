package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStabilityTest {
    @Test
    fun dlnaCallbacksUseSafeUiPosting() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")),
            Charsets.UTF_8,
        )
        val servicesSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(servicesSource.contains("onPlayRequested = playbackCoordinator::handlePlayRequest"))
        assertTrue(servicesSource.contains("onStopRequested = playbackCoordinator::handleStopRequest"))
        assertTrue(servicesSource.contains("onPauseRequested = playbackCoordinator::handlePauseRequest"))
        assertTrue(helperSource.contains("postToUi(\"play\")"))
        assertTrue(helperSource.contains("postToUi(\"stop\")"))
        assertTrue(helperSource.contains("postToUi(\"pause\")"))
    }
}
