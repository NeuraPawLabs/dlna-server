package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun clearSessionRejectsLateWritesForClosedSession() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        store.clearSession("session-1")

        val failure = runCatching {
            store.writeAsset("session-1", "video-0", byteArrayOf(1, 2, 3))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(root.resolve("session-1").exists())
    }

    @Test
    fun storeReadsSessionAssetThroughCoordinatedStoreApi() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        store.writeAsset("session-1", "video-0", byteArrayOf(1, 2, 3))

        val asset = store.readAsset("session-1", "video-0")

        assertTrue(asset != null)
        assertEquals(3, asset!!.bytes.size)
        assertEquals(3L, asset.file.length())
    }

    @Test
    fun clearAllSessionsMakesCoordinatedReadsReturnNull() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        store.writeAsset("session-1", "video-0", byteArrayOf(1, 2, 3))
        store.clearAllSessions()

        assertEquals(null, store.readAsset("session-1", "video-0"))
        assertFalse(root.resolve("session-1").exists())
    }

    @Test
    fun clearSessionReleasesTrackedStateButKeepsBoundedClosedSessionTombstones() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        repeat(SessionAssetStore.MAX_CLOSED_SESSION_TOMBSTONE_HISTORY + 5) { index ->
            val sessionId = "session-$index"
            store.writeAsset(sessionId, "video-0", byteArrayOf(1, 2, 3))
            store.clearSession(sessionId)
        }

        assertEquals(0, trackedSessionStateCount(store))
        assertEquals(
            SessionAssetStore.MAX_CLOSED_SESSION_TOMBSTONE_HISTORY,
            closedSessionTombstoneCount(store),
        )
        val retainedSessionId = "session-5"
        val failure = runCatching {
            store.writeAsset(retainedSessionId, "video-1", byteArrayOf(4, 5, 6))
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun clearAllSessionsReleasesTrackedStatesButKeepsClosedTombstonesBounded() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        repeat(SessionAssetStore.MAX_CLOSED_SESSION_TOMBSTONE_HISTORY + 5) { index ->
            store.writeAsset("session-$index", "video-0", byteArrayOf(1, 2, 3))
        }

        store.clearAllSessions()

        assertEquals(0, trackedSessionStateCount(store))
        assertEquals(
            SessionAssetStore.MAX_CLOSED_SESSION_TOMBSTONE_HISTORY,
            closedSessionTombstoneCount(store),
        )
        val failure = runCatching {
            store.writeAsset("session-10", "video-1", byteArrayOf(4, 5, 6))
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun unknownReadLookupsDoNotCreateTrackedSessionState() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        assertEquals(null, store.readAsset("unknown-session", "video-0"))
        assertEquals(null, store.assetLength("unknown-session", "video-0"))

        assertEquals(0, trackedSessionStateCount(store))
        assertEquals(0, closedSessionTombstoneCount(store))
    }
}

private fun trackedSessionStateCount(store: SessionAssetStore): Int {
    val field = SessionAssetStore::class.java.getDeclaredField("stateTracker").apply {
        isAccessible = true
    }
    val tracker = field.get(store)
    val stateField = tracker.javaClass.getDeclaredField("state").apply {
        isAccessible = true
    }
    val state = stateField.get(tracker)
    val trackerField = state.javaClass.getDeclaredField("sessionStates").apply {
        isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    return (trackerField.get(state) as Map<String, *>).size
}

private fun closedSessionTombstoneCount(store: SessionAssetStore): Int {
    val field = SessionAssetStore::class.java.getDeclaredField("stateTracker").apply {
        isAccessible = true
    }
    val tracker = field.get(store)
    val stateField = tracker.javaClass.getDeclaredField("state").apply {
        isAccessible = true
    }
    val state = stateField.get(tracker)
    val trackerField = state.javaClass.getDeclaredField("closedSessionIds").apply {
        isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    return (trackerField.get(state) as List<String>).size
}
