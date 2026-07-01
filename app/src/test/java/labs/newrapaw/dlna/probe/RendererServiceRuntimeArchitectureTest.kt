package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererServiceRuntimeArchitectureTest {
    @Test
    fun rendererServiceRuntimeDefersProxyLookupUntilAfterProxyConstruction() {
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt")),
            Charsets.UTF_8,
        )
        val bootstrapSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")),
            Charsets.UTF_8,
        )
        val playbackSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServicePlayback.kt")),
            Charsets.UTF_8,
        )
        val blockingSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/BlockingDispatch.kt")),
            Charsets.UTF_8,
        )

        assertTrue(playbackSource.contains("private val proxyProvider: () -> LocalHlsProxy"))
        assertTrue(playbackSource.contains("proxyProvider()"))
        assertTrue(bootstrapSource.contains("proxyProvider = { proxy }"))
        assertTrue(playbackSource.contains("awaitCountDownOrThrow("))
        assertFalse(playbackSource.contains("completion.await()"))
        assertTrue(blockingSource.contains("fun awaitCountDownOrThrow("))
        assertFalse(
            bootstrapSource.contains(
                "val playbackController = RendererServicePlayerController(\n" +
                    "        player = player,\n" +
                    "        proxy = proxy,",
            ),
        )
    }

    @Test
    fun rendererServiceRuntimeStartsSsdpOnlyAfterProxyStartSucceeds() {
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt")),
            Charsets.UTF_8,
        )
        val bootstrapSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")),
            Charsets.UTF_8,
        )

        assertFalse(runtimeSource.contains("val proxyStarted = runCatching {"))
        assertFalse(runtimeSource.contains("startRendererServiceSsdp("))
        assertFalse(runtimeSource.contains("startRendererServiceNetworkMonitor("))
        assertTrue(bootstrapSource.contains("val proxyStarted = runCatching {"))
        assertTrue(bootstrapSource.contains("if (proxyStarted) {"))
        assertTrue(bootstrapSource.contains("startRendererServiceSsdp("))
        assertTrue(bootstrapSource.contains("startRendererServiceNetworkMonitor("))
        assertTrue(bootstrapSource.contains("Proxy start failed: ${'$'}{it.message}"))
    }

    @Test
    fun rendererServiceRuntimeFailsFastWhenProxyCannotStart() {
        val bootstrapSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")),
            Charsets.UTF_8,
        )

        assertTrue(bootstrapSource.contains("runCatching { player.release() }"))
        assertTrue(bootstrapSource.contains("runCatching { proxy.close() }"))
        assertTrue(bootstrapSource.contains("throw it"))
    }

    @Test
    fun rendererServiceRuntimeCleansUpStartedResourcesWhenLaterInitializationFails() {
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt")),
            Charsets.UTF_8,
        )
        val bootstrapSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntimeBootstrap.kt")),
            Charsets.UTF_8,
        )

        assertFalse(runtimeSource.contains("val startedRuntime = runCatching {"))
        assertTrue(bootstrapSource.contains("val startedRuntime = runCatching {"))
        assertTrue(bootstrapSource.contains("runCatching { playbackTelemetry.stop() }"))
        assertTrue(bootstrapSource.contains("runCatching { player.removeListener(playerListener) }"))
        assertTrue(bootstrapSource.contains("runCatching { networkMonitor?.close() }"))
        assertTrue(bootstrapSource.contains("runCatching { ssdp?.close() }"))
        assertTrue(bootstrapSource.contains("}.getOrThrow()"))
    }

    @Test
    fun rendererServiceRuntimeDelegatesBootstrapConstructionToDedicatedFile() {
        val runtimeSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceRuntime.kt")),
            Charsets.UTF_8,
        )

        assertTrue(runtimeSource.contains("buildRendererServiceRuntime(context)"))
        assertFalse(runtimeSource.contains("val appHttpClient = OkHttpClient()"))
        assertFalse(runtimeSource.contains("val updater = ApkUpdater("))
        assertFalse(runtimeSource.contains("val playbackController = RendererServicePlayerController("))
        assertFalse(runtimeSource.contains("val playerListener = RendererServicePlayerListener("))
        assertFalse(runtimeSource.contains("val playbackTelemetry = RendererServicePlaybackTelemetry("))
    }
}
