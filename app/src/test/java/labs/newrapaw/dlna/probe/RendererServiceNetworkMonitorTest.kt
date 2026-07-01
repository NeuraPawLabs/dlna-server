package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.RendererNetworkRecoveryAction
import labs.newrapaw.dlna.probe.platform.RendererNetworkRecoveryDecision
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererServiceNetworkMonitorTest {
    @Test
    fun rebuildFailuresAreLoggedInsteadOfEscapingNetworkCallback() {
        val logs = mutableListOf<String>()
        val applyDecision = applyDecisionMethod()

        val result = runCatching {
            applyDecision!!.invoke(
                null,
                "available",
                RendererNetworkRecoveryDecision(
                    action = RendererNetworkRecoveryAction.REBUILD_SESSION,
                    resumePositionMs = 12_000L,
                    resumePlayback = true,
                ),
                {},
                { _: Long?, _: Boolean -> error("rebuild exploded") },
                { message: String -> logs.add(message) },
            )
        }

        assertNotNull("applyRendererNetworkRecoveryDecision(...) should exist", applyDecision)
        assertTrue(result.isSuccess)
        assertTrue(logs.any { it.contains("Network available, rebuilding active session") })
        assertTrue(logs.any { it.contains("Network recovery failed") && it.contains("rebuild exploded") })
    }

    @Test
    fun noneDecisionDoesNotInvokeCallbacks() {
        var paused = false
        var rebuilt = false
        val applyDecision = applyDecisionMethod()

        assertNotNull("applyRendererNetworkRecoveryDecision(...) should exist", applyDecision)
        applyDecision!!.invoke(
            null,
            "available",
            RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.NONE),
            { paused = true },
            { _: Long?, _: Boolean -> rebuilt = true },
            { _: String -> },
        )

        assertFalse(paused)
        assertFalse(rebuilt)
    }

    @Test
    fun pauseDecisionInvokesPauseCallback() {
        var paused = false
        val applyDecision = applyDecisionMethod()

        assertNotNull("applyRendererNetworkRecoveryDecision(...) should exist", applyDecision)
        applyDecision!!.invoke(
            null,
            "unavailable",
            RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.PAUSE_PLAYBACK),
            { paused = true },
            { _: Long?, _: Boolean -> },
            { _: String -> },
        )

        assertEquals(true, paused)
    }

    @Test
    fun networkMonitorIgnoresLateCallbacksAfterClose() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceNetworkMonitor.kt")),
            Charsets.UTF_8,
        )
        val callbacksSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/RendererServiceNetworkCallbacks.kt")),
            Charsets.UTF_8,
        )
        val callbackBlock = callbacksSource.substringAfter("object : ConnectivityManager.NetworkCallback() {")
            .substringBefore("fun register() {")
        val handleAvailableBlock = source.substringAfter("private fun handleAvailable(reason: String) {")
            .substringBeforeLast("}")

        assertTrue(source.contains("private val closed = AtomicBoolean(false)"))
        assertFalse(source.contains("private val callback = object : ConnectivityManager.NetworkCallback() {"))
        assertFalse(source.contains("registerDefaultNetworkCallback(callback)"))
        assertFalse(source.contains("registerNetworkCallback(NetworkRequest.Builder().build(), callback)"))
        assertFalse(source.contains("unregisterNetworkCallback(callback)"))
        assertTrue(callbacksSource.contains("class RendererServiceNetworkCallbacks("))
        assertTrue(callbacksSource.contains("private val closed: AtomicBoolean"))
        assertTrue(callbackBlock.contains("override fun onAvailable(network: Network) {"))
        assertTrue(callbackBlock.contains("override fun onLost(network: Network) {"))
        assertTrue(callbackBlock.contains("override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {"))
        assertTrue(callbackBlock.contains("if (closed.get()) return"))
        assertTrue(callbacksSource.contains("registerDefaultNetworkCallback(callback)"))
        assertTrue(callbacksSource.contains("registerNetworkCallback(NetworkRequest.Builder().build(), callback)"))
        assertTrue(callbacksSource.contains("unregisterNetworkCallback(callback)"))
        assertTrue(handleAvailableBlock.contains("if (closed.get()) return"))
        assertTrue(source.contains("if (closed.getAndSet(true)) return"))
        assertTrue(source.contains("callbacks.close()"))
    }

    private fun applyDecisionMethod() =
        Class.forName("labs.newrapaw.dlna.probe.platform.RendererServiceNetworkMonitorKt")
            .methods
            .firstOrNull { method ->
                method.name == "applyRendererNetworkRecoveryDecision" &&
                    method.parameterCount == 5
            }
}
