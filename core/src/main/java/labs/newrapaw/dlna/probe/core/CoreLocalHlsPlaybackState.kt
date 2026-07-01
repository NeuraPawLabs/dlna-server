package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSession

internal data class CoreLocalHlsPlaybackState(
    val activeSessionShell: PlaybackSession? = null,
    val activePreparedSession: PreparedSessionPlayback? = null,
    val latestPlayerPositionMs: Long? = null,
    val latestBufferedPositionMs: Long? = null,
) {
    fun toSnapshot(): CoreLocalHlsPlaybackSnapshot =
        CoreLocalHlsPlaybackSnapshot(
            activeSessionShell = activeSessionShell,
            activePreparedSession = activePreparedSession,
            latestPlayerPositionMs = latestPlayerPositionMs,
            latestBufferedPositionMs = latestBufferedPositionMs,
        )
}

internal data class CoreLocalHlsPlaybackSnapshot(
    val activeSessionShell: PlaybackSession?,
    val activePreparedSession: PreparedSessionPlayback?,
    val latestPlayerPositionMs: Long?,
    val latestBufferedPositionMs: Long?,
)
