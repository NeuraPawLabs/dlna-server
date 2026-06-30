package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsPlaybackRuntime(
    private val sessionAssetStore: SessionAssetStore,
    private val sessionLocalServer: SessionLocalServer,
) {
    private var activeSessionShell: PlaybackSession? = null
    private var activePreparedSession: PreparedSessionPlayback? = null
    private var latestPlayerPositionMs: Long? = null
    private var latestBufferedPositionMs: Long? = null

    fun activeSessionShell(): PlaybackSession? = activeSessionShell

    fun activePreparedSession(): PreparedSessionPlayback? = activePreparedSession

    fun latestPlayerPositionMs(): Long? = latestPlayerPositionMs

    fun latestBufferedPositionMs(): Long? = latestBufferedPositionMs

    fun setActivePreparedSession(prepared: PreparedSessionPlayback?) {
        activePreparedSession = prepared
    }

    fun activeSessionInfo(baseUrl: String): ActiveSessionInfo? {
        val prepared = activePreparedSession
        val session = prepared?.session ?: activeSessionShell ?: return null
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

    fun updatePlaybackPosition(positionMs: Long?) {
        latestPlayerPositionMs = positionMs
        latestBufferedPositionMs = positionMs
    }

    fun updatePlayerTelemetry(positionMs: Long?, bufferedPositionMs: Long?) {
        latestPlayerPositionMs = positionMs
        latestBufferedPositionMs = bufferedPositionMs
    }

    fun openSession(session: PlaybackSession) {
        clearCurrentPlaybackState()
        activeSessionShell = session
    }

    fun clearActiveSessionCache() {
        clearCurrentPlaybackState()
    }

    fun cleanupSession(sessionId: String) {
        sessionAssetStore.clearSession(sessionId)
        if (activeSessionShell?.sessionId == sessionId) {
            activeSessionShell = null
        }
        activePreparedSession
            ?.takeIf { prepared -> prepared.session.sessionId == sessionId }
            ?.let(::cancelPreparedSession)
        clearTelemetryIfInactive()
    }

    fun close() {
        clearCurrentPlaybackState()
        sessionAssetStore.clearAllSessions()
    }

    private fun clearCurrentPlaybackState() {
        val activeShell = activeSessionShell
        val prepared = activePreparedSession
        prepared?.let(::cancelPreparedSession)
        linkedSetOf<String>().apply {
            activeShell?.sessionId?.let(::add)
            prepared?.session?.sessionId?.let(::add)
        }.forEach(sessionAssetStore::clearSession)
        activeSessionShell = null
        activePreparedSession = null
        latestPlayerPositionMs = null
        latestBufferedPositionMs = null
    }

    private fun cancelPreparedSession(prepared: PreparedSessionPlayback) {
        prepared.prefetchController.cancel()
        prepared.callTracker.cancel()
        if (activePreparedSession?.session?.sessionId == prepared.session.sessionId) {
            activePreparedSession = null
        }
    }

    private fun clearTelemetryIfInactive() {
        if (activeSessionShell == null && activePreparedSession == null) {
            latestPlayerPositionMs = null
            latestBufferedPositionMs = null
        }
    }
}
