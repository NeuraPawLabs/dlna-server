package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.RendererServiceLifetimeAction
import labs.newrapaw.dlna.probe.platform.RendererServiceRestartMode
import labs.newrapaw.dlna.probe.platform.buildRendererServicePlaybackActivity
import labs.newrapaw.dlna.probe.platform.chooseRendererServiceRestartMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererServiceLifetimeTest {
    @Test
    fun idleRendererStillUsesStickyRestartToRemainDiscoverable() {
        assertEquals(
            RendererServiceRestartMode.STICKY,
            chooseRendererServiceRestartMode(
                buildRendererServicePlaybackActivity(
                    isPlaying = false,
                    isLoading = false,
                    playbackState = 1,
                    keepRendererDiscoverable = true,
                ),
            ),
        )
    }

    @Test
    fun stickyRestartIsUsedForActivePlaybackEvenWhenDiscoverabilityIsDisabled() {
        assertEquals(
            RendererServiceRestartMode.STICKY,
            chooseRendererServiceRestartMode(
                buildRendererServicePlaybackActivity(
                    isPlaying = false,
                    isLoading = false,
                    playbackState = 3,
                    keepRendererDiscoverable = false,
                ),
            ),
        )
    }

    @Test
    fun lastClientUnbindKeepsServiceRunningWhileRendererShouldStayDiscoverable() {
        val idleDecision = buildRendererServicePlaybackActivity(
            isPlaying = false,
            isLoading = false,
            playbackState = 1,
            keepRendererDiscoverable = true,
        )

        assertEquals(RendererServiceLifetimeAction.KEEP_RUNNING, idleDecision.onLastClientUnbound())
    }

    @Test
    fun lastClientUnbindStopsOnlyWhenPlaybackIsInactiveAndDiscoverabilityIsDisabled() {
        val idleDecision = buildRendererServicePlaybackActivity(
            isPlaying = false,
            isLoading = false,
            playbackState = 1,
            keepRendererDiscoverable = false,
        )
        val activeDecision = buildRendererServicePlaybackActivity(
            isPlaying = false,
            isLoading = true,
            playbackState = 2,
            keepRendererDiscoverable = false,
        )

        assertEquals(RendererServiceLifetimeAction.STOP_SERVICE, idleDecision.onLastClientUnbound())
        assertEquals(RendererServiceLifetimeAction.KEEP_RUNNING, activeDecision.onLastClientUnbound())
    }

    @Test
    fun pausedPlaybackStillCountsAsActiveWork() {
        val playback = buildRendererServicePlaybackActivity(
            isPlaying = false,
            isLoading = false,
            playbackState = 3,
            keepRendererDiscoverable = false,
        )

        assertTrue(playback.hasActivePlayback())
        assertFalse(
            buildRendererServicePlaybackActivity(
                isPlaying = false,
                isLoading = false,
                playbackState = 4,
                keepRendererDiscoverable = false,
            ).hasActivePlayback(),
        )
    }
}
