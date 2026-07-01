package labs.newrapaw.dlna.probe

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import labs.newrapaw.dlna.probe.platform.RendererPlaybackRecoveryAction
import labs.newrapaw.dlna.probe.platform.playbackDiagnosticsStatusFor
import labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.ui.decidePlayerErrorRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorRecoveryTest {
    @Test
    fun malformedContainerErrorIsRecoverableWithinRetryBudget() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            currentPositionMs = 12_000L,
            durationMs = 60_000L,
            attemptCount = 1,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(RendererPlaybackRecoveryAction.SEEK, decision.action)
        assertEquals(20_000L, decision.seekPositionMs)
        assertEquals(2, decision.nextAttemptCount)
    }

    @Test
    fun malformedContainerErrorStopsRecoveringAfterRetryBudget() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            currentPositionMs = 12_000L,
            durationMs = 60_000L,
            attemptCount = 5,
        )

        assertFalse(decision.shouldRecover)
        assertEquals(null, decision.seekPositionMs)
        assertEquals(5, decision.nextAttemptCount)
    }

    @Test
    fun unknownErrorsRemainFatal() {
        val decision = decidePlayerErrorRecovery(
            errorCode = 999_999,
            currentPositionMs = 12_000L,
            durationMs = 60_000L,
            attemptCount = 0,
        )

        assertFalse(decision.shouldRecover)
        assertEquals(null, decision.seekPositionMs)
        assertEquals(0, decision.nextAttemptCount)
    }

    @Test
    fun networkConnectionFailedIsRecoverable() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            currentPositionMs = 12_000L,
            durationMs = 60_000L,
            attemptCount = 0,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(RendererPlaybackRecoveryAction.SEEK, decision.action)
        assertEquals(16_000L, decision.seekPositionMs)
        assertEquals(1, decision.nextAttemptCount)
    }

    @Test
    fun repeatedRecoverableErrorsEscalateToSessionRebuild() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            currentPositionMs = 40_000L,
            durationMs = 60_000L,
            attemptCount = 2,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(RendererPlaybackRecoveryAction.REBUILD_SESSION, decision.action)
        assertEquals(56_000L, decision.seekPositionMs)
        assertEquals(3, decision.nextAttemptCount)
    }

    @Test
    fun badHttpStatusIsRecoverable() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            currentPositionMs = 30_000L,
            durationMs = 60_000L,
            attemptCount = 1,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(38_000L, decision.seekPositionMs)
        assertEquals(2, decision.nextAttemptCount)
    }

    @Test
    fun networkTimeoutIsRecoverable() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            currentPositionMs = 5_000L,
            durationMs = 60_000L,
            attemptCount = 0,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(9_000L, decision.seekPositionMs)
        assertEquals(1, decision.nextAttemptCount)
    }

    @Test
    fun recoverySeekIsClampedToDuration() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            currentPositionMs = 59_500L,
            durationMs = 60_000L,
            attemptCount = 0,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(60_000L, decision.seekPositionMs)
        assertEquals(1, decision.nextAttemptCount)
    }

    @Test
    fun unspecifiedIoErrorIsRecoverableAfterPriorRecoveryAttempts() {
        val decision = decidePlayerErrorRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            currentPositionMs = 40_000L,
            durationMs = 60_000L,
            attemptCount = 2,
        )

        assertTrue(decision.shouldRecover)
        assertEquals(56_000L, decision.seekPositionMs)
        assertEquals(3, decision.nextAttemptCount)
    }

    @Test
    fun readyButNotPlayingMapsToPausedDiagnosticsStatus() {
        assertEquals(
            PlaybackDiagnosticsStatus.PAUSED,
            playbackDiagnosticsStatusFor(
                playbackState = Player.STATE_READY,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun readyAndPlayingMapsToPlayingDiagnosticsStatus() {
        assertEquals(
            PlaybackDiagnosticsStatus.PLAYING,
            playbackDiagnosticsStatusFor(
                playbackState = Player.STATE_READY,
                isPlaying = true,
            ),
        )
    }
}
