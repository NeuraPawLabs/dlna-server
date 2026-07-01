package labs.newrapaw.dlna.probe.core.session

import java.io.File

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
