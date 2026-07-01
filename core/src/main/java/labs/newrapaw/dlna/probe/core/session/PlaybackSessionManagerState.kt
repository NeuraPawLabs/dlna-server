package labs.newrapaw.dlna.probe.core.session

internal data class PlaybackSessionManagerState(
    val active: PlaybackSession? = null,
    val closed: List<PlaybackSession> = emptyList(),
) {
    fun withStartedSession(session: PlaybackSession): PlaybackSessionManagerState =
        copy(active = session)

    fun withClosedSession(
        session: PlaybackSession,
        maxClosedSessionHistory: Int,
    ): PlaybackSessionManagerState {
        val nextClosed = (closed + session).takeLast(maxClosedSessionHistory)
        return copy(
            active = null,
            closed = nextClosed,
        )
    }
}
