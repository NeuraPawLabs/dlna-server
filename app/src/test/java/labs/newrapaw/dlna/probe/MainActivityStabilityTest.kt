package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
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
        val serviceRuntimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt")),
            Charsets.UTF_8,
        )
        val servicePlaybackSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("buildMainActivityRuntime("))
        assertTrue(runtimeSource.contains("buildMainActivityServices("))
        assertTrue(serviceRuntimeSource.contains("handlePlayRequest(url)"))
        assertTrue(serviceRuntimeSource.contains("handleStopRequest()"))
        assertTrue(serviceRuntimeSource.contains("handlePauseRequest()"))
        assertTrue(helperSource.contains("postToUi(\"play\")"))
        assertTrue(helperSource.contains("postToUi(\"stop\")"))
        assertTrue(helperSource.contains("postToUi(\"pause\")"))
        assertTrue(helperSource.contains("postToUi(\"seek\")"))
        assertTrue(helperSource.contains("playbackStateProvider().clearActivePlaybackSession()"))
        assertTrue(helperSource.contains("proxyProvider().updateDlnaPosition(positionMs)"))
        assertTrue(helperSource.contains("applyCommandState(rendererPlayCommandState())"))
        assertTrue(helperSource.contains("applyCommandState(rendererPauseCommandState())"))
        assertTrue(helperSource.contains("applyCommandState(rendererStopCommandState())"))
        assertTrue(serviceRuntimeSource.contains("playbackController.handleStopRequest()"))
        assertTrue(servicePlaybackSource.contains("proxy().updateDlnaPosition(positionMs)"))
        assertTrue(servicePlaybackSource.contains("playbackState().updatePlaybackStatus(update.diagnosticsStatus)"))
    }

    @Test
    fun remotePlaybackPreparesPlayerBeforeSwitchingProxySession() {
        val proxySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")),
            Charsets.UTF_8,
        )
        val playbackRouterSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyPlaybackRouter.kt")),
            Charsets.UTF_8,
        )
        val coordinatorSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt")),
            Charsets.UTF_8,
        )
        val blockingSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/BlockingDispatch.kt")),
            Charsets.UTF_8,
        )

        assertTrue(proxySource.contains("private val playbackRouter = LocalHlsProxyPlaybackRouter("))
        assertTrue(playbackRouterSource.contains("beforePlaybackSwitch()"))
        assertTrue(playbackRouterSource.contains("coreProxy.openSession("))
        assertTrue(
            playbackRouterSource.indexOf("beforePlaybackSwitch()") < playbackRouterSource.indexOf("coreProxy.openSession("),
        )
        assertTrue(coordinatorSource.contains("fun prepareForPlaybackSwitch("))
        assertTrue(coordinatorSource.contains("player.stop()"))
        assertTrue(coordinatorSource.contains("player.clearMediaItems()"))
        assertTrue(coordinatorSource.contains("awaitCountDownOrThrow("))
        assertFalse(coordinatorSource.contains("completion.await()"))
        assertTrue(blockingSource.contains("TimeoutException"))
    }

    @Test
    fun activityCloseDoesNotShutdownServiceOwnedProxyOrSsdp() {
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")),
            Charsets.UTF_8,
        )
        val serviceSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererForegroundService.kt")),
            Charsets.UTF_8,
        )

        assertFalse(runtimeSource.contains("proxy.close()"))
        assertFalse(runtimeSource.contains("ssdp?.close()"))
        assertFalse(runtimeSource.contains("player.release()"))
        assertTrue(runtimeSource.contains("services.close()"))
        assertTrue(serviceSource.contains("closeRendererServiceRuntime()"))
    }
}
