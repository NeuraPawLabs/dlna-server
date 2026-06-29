package labs.newrapaw.dlna.probe.core.session

class PlaybackSessionManager(
    private val createSessionId: () -> String,
    private val cleanupSession: (PlaybackSession) -> Unit,
) {
    private var active: PlaybackSession? = null
    private val closed = mutableListOf<PlaybackSession>()

    fun startSession(
        sourceUrl: String,
        entryManifestUrl: String,
        localRootDir: String,
    ): PlaybackSession {
        active?.let { previous ->
            val closedSession = previous.copy(status = PlaybackSessionStatus.CLOSED)
            cleanupSession(closedSession)
            closed += closedSession
        }
        val session = PlaybackSession.create(
            sessionId = createSessionId(),
            sourceUrl = sourceUrl,
            entryManifestUrl = entryManifestUrl,
            localRootDir = localRootDir,
        )
        active = session
        return session
    }

    fun activeSession(): PlaybackSession? = active

    fun closedSessions(): List<PlaybackSession> = closed.toList()
}
