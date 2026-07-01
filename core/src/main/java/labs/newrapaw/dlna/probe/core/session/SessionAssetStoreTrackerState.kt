package labs.newrapaw.dlna.probe.core.session

internal data class SessionAssetStoreTrackerState(
    val sessionStates: Map<String, SessionAssetStoreSessionState> = emptyMap(),
    val closedSessionIds: List<String> = emptyList(),
) {
    fun withTrackedSession(
        sessionId: String,
        sessionState: SessionAssetStoreSessionState,
    ): SessionAssetStoreTrackerState =
        copy(sessionStates = sessionStates + (sessionId to sessionState))

    fun withClosedSession(
        sessionId: String,
        maxClosedSessionTombstoneHistory: Int,
    ): SessionAssetStoreTrackerState =
        copy(
            sessionStates = sessionStates - sessionId,
            closedSessionIds = appendClosedSessionId(
                sessionId = sessionId,
                maxClosedSessionTombstoneHistory = maxClosedSessionTombstoneHistory,
            ),
        )

    fun withClosedSessions(
        sessionIds: Set<String>,
        maxClosedSessionTombstoneHistory: Int,
    ): SessionAssetStoreTrackerState {
        val nextClosed = sessionIds.fold(closedSessionIds) { current, sessionId ->
            appendClosedSessionId(
                closedSessionIds = current,
                sessionId = sessionId,
                maxClosedSessionTombstoneHistory = maxClosedSessionTombstoneHistory,
            )
        }
        return copy(
            sessionStates = emptyMap(),
            closedSessionIds = nextClosed,
        )
    }

    private fun appendClosedSessionId(
        sessionId: String,
        maxClosedSessionTombstoneHistory: Int,
    ): List<String> =
        appendClosedSessionId(
            closedSessionIds = closedSessionIds,
            sessionId = sessionId,
            maxClosedSessionTombstoneHistory = maxClosedSessionTombstoneHistory,
        )

    private fun appendClosedSessionId(
        closedSessionIds: List<String>,
        sessionId: String,
        maxClosedSessionTombstoneHistory: Int,
    ): List<String> =
        (closedSessionIds.filterNot { it == sessionId } + sessionId)
            .takeLast(maxClosedSessionTombstoneHistory)
}
