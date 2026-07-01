package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.RendererNetworkRecoveryAction
import labs.newrapaw.dlna.probe.platform.RendererServiceNetworkRecoveryCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RendererServiceNetworkRecoveryTest {
    @Test
    fun networkLossPausesActiveSessionAndRecoveryRebuildsCapturedPlayback() {
        val coordinator = RendererServiceNetworkRecoveryCoordinator(initialIpAddress = "192.168.1.10")

        val lost = coordinator.onNetworkLost(
            currentPositionMs = 12_500L,
            isPlaying = true,
            hasActiveSession = true,
        )
        val recovered = coordinator.onNetworkAvailable(
            currentIpAddress = "192.168.1.22",
            currentPositionMs = 18_000L,
            isPlaying = false,
            hasActiveSession = true,
        )

        assertEquals(RendererNetworkRecoveryAction.PAUSE_PLAYBACK, lost.action)
        assertNull(lost.resumePositionMs)
        assertEquals(RendererNetworkRecoveryAction.REBUILD_SESSION, recovered.action)
        assertEquals(12_500L, recovered.resumePositionMs)
        assertEquals(true, recovered.resumePlayback)
    }

    @Test
    fun ipChangeWhileConnectedRebuildsActiveSessionFromCurrentPlayback() {
        val coordinator = RendererServiceNetworkRecoveryCoordinator(initialIpAddress = "192.168.1.10")

        val recovered = coordinator.onNetworkAvailable(
            currentIpAddress = "192.168.1.55",
            currentPositionMs = 40_000L,
            isPlaying = true,
            hasActiveSession = true,
        )

        assertEquals(RendererNetworkRecoveryAction.REBUILD_SESSION, recovered.action)
        assertEquals(40_000L, recovered.resumePositionMs)
        assertEquals(true, recovered.resumePlayback)
    }

    @Test
    fun networkRecoveryDoesNothingWithoutActiveSession() {
        val coordinator = RendererServiceNetworkRecoveryCoordinator(initialIpAddress = "192.168.1.10")

        val lost = coordinator.onNetworkLost(
            currentPositionMs = 12_500L,
            isPlaying = true,
            hasActiveSession = false,
        )
        val recovered = coordinator.onNetworkAvailable(
            currentIpAddress = "192.168.1.22",
            currentPositionMs = 18_000L,
            isPlaying = true,
            hasActiveSession = false,
        )

        assertEquals(RendererNetworkRecoveryAction.NONE, lost.action)
        assertEquals(RendererNetworkRecoveryAction.NONE, recovered.action)
    }
}
