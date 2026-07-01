package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsPlaybackRuntime(
    private val sessionAssetStore: SessionAssetStore,
    private val sessionLocalServer: SessionLocalServer,
) {
    private val lock = Any()
    private var state = CoreLocalHlsPlaybackState()

    fun snapshot(): CoreLocalHlsPlaybackSnapshot = synchronized(lock) { state.toSnapshot() }

    fun activeSessionShell(): PlaybackSession? = synchronized(lock) { state.activeSessionShell }

    fun activePreparedSession(): PreparedSessionPlayback? = synchronized(lock) { state.activePreparedSession }

    fun latestPlayerPositionMs(): Long? = synchronized(lock) { state.latestPlayerPositionMs }

    fun latestBufferedPositionMs(): Long? = synchronized(lock) { state.latestBufferedPositionMs }

    fun setActivePreparedSession(prepared: PreparedSessionPlayback?) = synchronized(lock) {
        state = state.copy(activePreparedSession = prepared)
    }

    fun activeSessionInfo(baseUrl: String): ActiveSessionInfo? {
        val snapshot = synchronized(lock) { state }
        val prepared = snapshot.activePreparedSession
        val session = prepared?.session ?: snapshot.activeSessionShell ?: return null
        return ActiveSessionInfo(
            sessionId = session.sessionId,
            status = session.status,
            sourceUrl = session.sourceUrl,
            localManifestUrl = "$baseUrl${sessionLocalServer.masterManifestPath(session.sessionId)}",
            slotCount = prepared?.session?.timeline?.slots?.size ?: session.timeline.slots.size,
            assetCount = prepared?.assetsById?.size ?: session.timeline.assets.size,
            prepared = prepared != null,
            pendingPrefetchAssetIds = prepared?.prefetchController?.snapshotQueue() ?: emptyList(),
        )
    }

    fun updatePlaybackPosition(positionMs: Long?) = synchronized(lock) {
        state = state.copy(
            latestPlayerPositionMs = positionMs,
        )
    }

    fun updatePlayerTelemetry(positionMs: Long?, bufferedPositionMs: Long?) = synchronized(lock) {
        state = state.copy(
            latestPlayerPositionMs = positionMs,
            latestBufferedPositionMs = bufferedPositionMs,
        )
    }

    fun openSession(session: PlaybackSession) = synchronized(lock) {
        clearCurrentPlaybackState()
        state = state.copy(activeSessionShell = session)
    }

    fun clearActiveSessionCache() = synchronized(lock) {
        clearCurrentPlaybackState()
    }

    fun cleanupSession(sessionId: String) = synchronized(lock) {
        state.activePreparedSession
            ?.takeIf { prepared -> prepared.session.sessionId == sessionId }
            ?.let(::cancelPreparedSession)
        if (state.activeSessionShell?.sessionId == sessionId) {
            state = state.copy(activeSessionShell = null)
        }
        sessionAssetStore.clearSession(sessionId)
        clearTelemetryIfInactive()
    }

    fun close() = synchronized(lock) {
        clearCurrentPlaybackState()
        sessionAssetStore.clearAllSessions()
    }

    private fun clearCurrentPlaybackState() {
        val activeShell = state.activeSessionShell
        val prepared = state.activePreparedSession
        prepared?.let(::cancelPreparedSession)
        linkedSetOf<String>().apply {
            activeShell?.sessionId?.let(::add)
            prepared?.session?.sessionId?.let(::add)
        }.forEach(sessionAssetStore::clearSession)
        state = CoreLocalHlsPlaybackState()
    }

    private fun cancelPreparedSession(prepared: PreparedSessionPlayback) {
        prepared.prefetchController.cancel()
        prepared.callTracker.cancel()
        if (state.activePreparedSession?.session?.sessionId == prepared.session.sessionId) {
            state = state.copy(activePreparedSession = null)
        }
    }

    private fun clearTelemetryIfInactive() {
        if (state.activeSessionShell == null && state.activePreparedSession == null) {
            state = state.copy(
                latestPlayerPositionMs = null,
                latestBufferedPositionMs = null,
            )
        }
    }
}
