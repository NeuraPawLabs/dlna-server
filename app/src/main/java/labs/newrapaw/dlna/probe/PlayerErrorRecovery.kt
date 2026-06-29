package labs.newrapaw.dlna.probe

import androidx.media3.common.PlaybackException

private const val BASE_RECOVERY_SKIP_MS = 4_000L
private const val MAX_RECOVERY_SKIP_MS = 20_000L
private const val MAX_RECOVERY_ATTEMPTS = 5

data class PlayerErrorRecoveryDecision(
    val shouldRecover: Boolean,
    val seekPositionMs: Long?,
    val nextAttemptCount: Int,
)

fun decidePlayerErrorRecovery(
    errorCode: Int,
    currentPositionMs: Long?,
    durationMs: Long?,
    attemptCount: Int,
): PlayerErrorRecoveryDecision {
    val recoverable = when (errorCode) {
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> attemptCount > 0
        else -> false
    }
    if (!recoverable) {
        return PlayerErrorRecoveryDecision(
            shouldRecover = false,
            seekPositionMs = null,
            nextAttemptCount = attemptCount,
        )
    }
    val currentPosition = currentPositionMs ?: return PlayerErrorRecoveryDecision(false, null, attemptCount)
    if (currentPosition < 0L || attemptCount >= MAX_RECOVERY_ATTEMPTS) {
        return PlayerErrorRecoveryDecision(false, null, attemptCount)
    }
    val skipMs = minOf(
        MAX_RECOVERY_SKIP_MS,
        BASE_RECOVERY_SKIP_MS * (1L shl attemptCount.coerceAtMost(4)),
    )
    val targetPosition = (currentPosition + skipMs)
        .let { target ->
            val boundedDuration = durationMs?.takeIf { it >= 0L }
            if (boundedDuration == null) target else minOf(target, boundedDuration)
        }
    if (targetPosition <= currentPosition) {
        return PlayerErrorRecoveryDecision(false, null, attemptCount)
    }
    return PlayerErrorRecoveryDecision(
        shouldRecover = true,
        seekPositionMs = targetPosition,
        nextAttemptCount = attemptCount + 1,
    )
}
