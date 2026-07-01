package labs.newrapaw.dlna.probe.platform

import androidx.media3.common.PlaybackException

class RendererServicePlayerRecoveryState {
    private val lock = Any()
    private var attemptCount = 0
    private var lastSeekPositionMs: Long? = null

    fun attemptCount(): Int = synchronized(lock) { attemptCount }

    fun lastSeekPositionMs(): Long? = synchronized(lock) { lastSeekPositionMs }

    fun update(attemptCount: Int, seekPositionMs: Long?) = synchronized(lock) {
        this.attemptCount = attemptCount
        this.lastSeekPositionMs = seekPositionMs
    }

    fun clear() = synchronized(lock) {
        attemptCount = 0
        lastSeekPositionMs = null
    }
}

enum class RendererPlaybackRecoveryAction {
    FAIL,
    SEEK,
    REBUILD_SESSION,
}

data class RendererPlaybackRecoveryDecision(
    val action: RendererPlaybackRecoveryAction,
    val seekPositionMs: Long?,
    val nextAttemptCount: Int,
) {
    val shouldRecover: Boolean
        get() = action != RendererPlaybackRecoveryAction.FAIL
}

object RendererPlaybackRecoveryDecider {
    fun decide(
        errorCode: Int,
        currentPositionMs: Long?,
        durationMs: Long?,
        attemptCount: Int,
    ): RendererPlaybackRecoveryDecision {
        val recoverable = when (errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> true
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> attemptCount > 0
            else -> false
        }
        if (!recoverable) {
            return RendererPlaybackRecoveryDecision(
                action = RendererPlaybackRecoveryAction.FAIL,
                seekPositionMs = null,
                nextAttemptCount = attemptCount,
            )
        }
        val currentPosition = currentPositionMs
            ?: return RendererPlaybackRecoveryDecision(RendererPlaybackRecoveryAction.FAIL, null, attemptCount)
        if (currentPosition < 0L || attemptCount >= MAX_RECOVERY_ATTEMPTS) {
            return RendererPlaybackRecoveryDecision(RendererPlaybackRecoveryAction.FAIL, null, attemptCount)
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
            return RendererPlaybackRecoveryDecision(RendererPlaybackRecoveryAction.FAIL, null, attemptCount)
        }
        return RendererPlaybackRecoveryDecision(
            action = if (attemptCount >= SESSION_REBUILD_RECOVERY_THRESHOLD) {
                RendererPlaybackRecoveryAction.REBUILD_SESSION
            } else {
                RendererPlaybackRecoveryAction.SEEK
            },
            seekPositionMs = targetPosition,
            nextAttemptCount = attemptCount + 1,
        )
    }
}

fun decideRendererPlaybackRecovery(
    errorCode: Int,
    currentPositionMs: Long?,
    durationMs: Long?,
    attemptCount: Int,
): RendererPlaybackRecoveryDecision =
    RendererPlaybackRecoveryDecider.decide(
        errorCode = errorCode,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        attemptCount = attemptCount,
    )

private const val BASE_RECOVERY_SKIP_MS = 4_000L
private const val MAX_RECOVERY_SKIP_MS = 20_000L
private const val MAX_RECOVERY_ATTEMPTS = 5
private const val SESSION_REBUILD_RECOVERY_THRESHOLD = 2
