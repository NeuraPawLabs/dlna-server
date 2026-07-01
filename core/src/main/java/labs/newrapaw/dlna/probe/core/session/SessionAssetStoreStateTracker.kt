package labs.newrapaw.dlna.probe.core.session

internal class SessionAssetStoreStateTracker(
    private val maxClosedSessionTombstoneHistory: Int,
) {
    private val lock = Any()
    private var state = SessionAssetStoreTrackerState()

    fun writableSessionState(sessionId: String): SessionAssetStoreSessionState =
        synchronized(lock) {
            check(sessionId !in state.closedSessionIds) { "session asset store is closed for $sessionId" }
            state.sessionStates[sessionId] ?: SessionAssetStoreSessionState().also { sessionState ->
                state = state.withTrackedSession(sessionId, sessionState)
            }
        }

    fun activeSessionStateOrNull(sessionId: String): SessionAssetStoreSessionState? =
        synchronized(lock) {
            if (sessionId in state.closedSessionIds) return@synchronized null
            state.sessionStates[sessionId]
        }

    fun closeSession(sessionId: String): SessionAssetStoreSessionState =
        synchronized(lock) {
            val sessionState = state.sessionStates[sessionId] ?: SessionAssetStoreSessionState()
            state = state.withClosedSession(
                sessionId = sessionId,
                maxClosedSessionTombstoneHistory = maxClosedSessionTombstoneHistory,
            )
            sessionState
        }

    fun closeAllSessions(knownSessionIds: Set<String>): ClosedSessionStateSnapshot =
        synchronized(lock) {
            val trackedSessionIds = linkedSetOf<String>().apply {
                addAll(state.sessionStates.keys)
                addAll(knownSessionIds)
            }
            val trackedStates = state.sessionStates
            state = state.withClosedSessions(
                sessionIds = trackedSessionIds,
                maxClosedSessionTombstoneHistory = maxClosedSessionTombstoneHistory,
            )
            ClosedSessionStateSnapshot(
                statesBySession = trackedStates,
                trackedSessionIds = trackedSessionIds,
            )
        }
}

internal class SessionAssetStoreSessionState {
    val lock = Any()
    var closed: Boolean = false
}

internal data class ClosedSessionStateSnapshot(
    val statesBySession: Map<String, SessionAssetStoreSessionState>,
    val trackedSessionIds: Set<String>,
)
