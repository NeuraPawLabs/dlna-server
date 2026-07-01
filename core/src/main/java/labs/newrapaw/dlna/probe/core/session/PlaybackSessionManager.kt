package labs.newrapaw.dlna.probe.core.session

class PlaybackSessionManager(
    private val createSessionId: () -> String,
    private val cleanupSession: (PlaybackSession) -> Unit,
) {
    private val lock = Any()
    private var state = PlaybackSessionManagerState()

    fun startSession(
        sourceUrl: String,
        entryManifestUrl: String,
        localRootDir: String,
    ): PlaybackSession = synchronized(lock) {
        state.active?.let { previous ->
            val closedSession = previous.copy(status = PlaybackSessionStatus.CLOSED)
            cleanupSession(closedSession)
            state = state.withClosedSession(
                session = closedSession,
                maxClosedSessionHistory = MAX_CLOSED_SESSION_HISTORY,
            )
        }
        val session = PlaybackSession.create(
            sessionId = createSessionId(),
            sourceUrl = sourceUrl,
            entryManifestUrl = entryManifestUrl,
            localRootDir = localRootDir,
        )
        state = state.withStartedSession(session)
        return session
    }

    fun activeSession(): PlaybackSession? = synchronized(lock) { state.active }

    fun closedSessions(): List<PlaybackSession> = synchronized(lock) { state.closed }

    fun isClosedSessionId(sessionId: String): Boolean = synchronized(lock) {
        state.closed.any { it.sessionId == sessionId }
    }

    companion object {
        internal const val MAX_CLOSED_SESSION_HISTORY = 64
    }
}

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
