package labs.newrapaw.dlna.probe.core.session

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SessionAssetStore(
    private val rootDir: File,
) {
    private val cleanup = SessionAssetStoreCleanup(rootDir)
    private val stateTracker = SessionAssetStoreStateTracker(
        maxClosedSessionTombstoneHistory = MAX_CLOSED_SESSION_TOMBSTONE_HISTORY,
    )

    init {
        rootDir.mkdirs()
    }

    fun writeAsset(sessionId: String, assetId: String, bytes: ByteArray): File {
        val state = stateTracker.writableSessionState(sessionId)
        synchronized(state.lock) {
            check(!state.closed) { "session asset store is closed for $sessionId" }
            val sessionDir = rootDir.resolve(sessionId).also { it.mkdirs() }
            val file = sessionDir.resolve("$assetId.bin")
            val tempFile = sessionDir.resolve("$assetId.bin.tmp-${System.nanoTime()}-${Thread.currentThread().id}")
            return try {
                tempFile.writeBytes(bytes)
                moveIntoPlace(tempFile, file)
                file
            } catch (error: Throwable) {
                tempFile.delete()
                throw error
            }
        }
    }

    fun resolveAsset(sessionId: String, assetId: String): File =
        rootDir.resolve(sessionId).resolve("$assetId.bin")

    fun readAsset(sessionId: String, assetId: String): StoredSessionAsset? {
        val state = stateTracker.activeSessionStateOrNull(sessionId) ?: return null
        synchronized(state.lock) {
            if (state.closed) return null
            val file = resolveAsset(sessionId, assetId)
            if (!file.isFile) return null
            return StoredSessionAsset(
                file = file,
                bytes = file.readBytes(),
            )
        }
    }

    fun assetLength(sessionId: String, assetId: String): Long? {
        val state = stateTracker.activeSessionStateOrNull(sessionId) ?: return null
        synchronized(state.lock) {
            if (state.closed) return null
            val file = resolveAsset(sessionId, assetId)
            if (!file.isFile) return null
            return file.length()
        }
    }

    fun clearSession(sessionId: String) {
        val state = stateTracker.closeSession(sessionId)
        synchronized(state.lock) {
            state.closed = true
        }
        cleanup.deleteClosedSession(sessionId)
    }

    fun clearAllSessions() {
        val snapshot = stateTracker.closeAllSessions(
            knownSessionIds = rootDir.listFiles()?.map { it.name }?.toSet().orEmpty(),
        )
        val statesBySession = snapshot.statesBySession
        val trackedSessionIds = snapshot.trackedSessionIds
        statesBySession.forEach { (sessionId, state) ->
            synchronized(state.lock) {
                state.closed = true
            }
        }
        cleanup.deleteClosedSessions(trackedSessionIds)
    }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        internal const val MAX_CLOSED_SESSION_TOMBSTONE_HISTORY = 64
    }
}

data class StoredSessionAsset(
    val file: File,
    val bytes: ByteArray,
)

internal class SessionAssetStoreCleanup(
    private val rootDir: File,
) {
    fun deleteClosedSession(sessionId: String) {
        rootDir.resolve(sessionId).deleteRecursively()
    }

    fun deleteClosedSessions(sessionIds: Set<String>) {
        sessionIds.forEach(::deleteClosedSession)
    }
}

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

internal class SessionAssetStoreSessionState {
    val lock = Any()
    var closed: Boolean = false
}

internal data class ClosedSessionStateSnapshot(
    val statesBySession: Map<String, SessionAssetStoreSessionState>,
    val trackedSessionIds: Set<String>,
)
