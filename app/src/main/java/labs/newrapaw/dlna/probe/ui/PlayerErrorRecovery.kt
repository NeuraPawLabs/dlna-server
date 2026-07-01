package labs.newrapaw.dlna.probe.ui

import labs.newrapaw.dlna.probe.platform.RendererPlaybackRecoveryDecision
import labs.newrapaw.dlna.probe.platform.decideRendererPlaybackRecovery

typealias PlayerErrorRecoveryDecision = RendererPlaybackRecoveryDecision

fun decidePlayerErrorRecovery(
    errorCode: Int,
    currentPositionMs: Long?,
    durationMs: Long?,
    attemptCount: Int,
): PlayerErrorRecoveryDecision =
    decideRendererPlaybackRecovery(
        errorCode = errorCode,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        attemptCount = attemptCount,
    )
