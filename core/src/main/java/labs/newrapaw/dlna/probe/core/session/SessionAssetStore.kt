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
