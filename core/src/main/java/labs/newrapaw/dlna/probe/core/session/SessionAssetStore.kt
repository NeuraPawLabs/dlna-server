package labs.newrapaw.dlna.probe.core.session

import java.io.File

class SessionAssetStore(
    private val rootDir: File,
) {
    init {
        rootDir.mkdirs()
    }

    fun writeAsset(sessionId: String, assetId: String, bytes: ByteArray): File {
        val sessionDir = rootDir.resolve(sessionId).also { it.mkdirs() }
        val file = sessionDir.resolve("$assetId.bin")
        file.writeBytes(bytes)
        return file
    }

    fun resolveAsset(sessionId: String, assetId: String): File =
        rootDir.resolve(sessionId).resolve("$assetId.bin")

    fun clearSession(sessionId: String) {
        rootDir.resolve(sessionId).deleteRecursively()
    }

    fun clearAllSessions() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
