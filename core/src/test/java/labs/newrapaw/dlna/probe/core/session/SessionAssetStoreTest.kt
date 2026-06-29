package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionAssetStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun storeWritesSessionAssetAndClearsWholeSessionTree() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        val file = store.writeAsset("session-1", "video-0", byteArrayOf(1, 2, 3))
        assertTrue(file.isFile)
        assertEquals(3, file.length())

        store.clearSession("session-1")

        assertTrue(!file.exists())
        assertTrue(!root.resolve("session-1").exists())
    }
}
