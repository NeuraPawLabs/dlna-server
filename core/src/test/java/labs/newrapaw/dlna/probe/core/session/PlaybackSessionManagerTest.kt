package labs.newrapaw.dlna.probe.core.session

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionManagerTest {
    @Test
    fun sessionFactoryCreatesPreparingVodSessionWithSingleActiveIdentity() {
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = "/tmp/session-1",
        )

        assertEquals("session-1", session.sessionId)
        assertEquals(PlaybackSessionStatus.PREPARING, session.status)
        assertEquals("https://example.com/video.m3u8", session.sourceUrl)
        assertTrue(session.timeline.slots.isEmpty())
        assertTrue(session.timeline.assets.isEmpty())
    }

    @Test
    fun startingNewSessionClosesPreviousSessionAndInvokesCleanup() {
        val cleaned = mutableListOf<String>()
        val manager = PlaybackSessionManager(
            createSessionId = { "session-${cleaned.size + 1}" },
            cleanupSession = { cleaned += it.sessionId },
        )

        val first = manager.startSession(
            sourceUrl = "https://example.com/a.m3u8",
            entryManifestUrl = "https://example.com/a.m3u8",
            localRootDir = "/tmp/a",
        )
        val second = manager.startSession(
            sourceUrl = "https://example.com/b.m3u8",
            entryManifestUrl = "https://example.com/b.m3u8",
            localRootDir = "/tmp/b",
        )

        assertEquals("session-1", first.sessionId)
        assertEquals("session-2", second.sessionId)
        assertEquals(listOf("session-1"), cleaned)
        assertEquals("session-2", manager.activeSession()?.sessionId)
        assertEquals(PlaybackSessionStatus.CLOSED, manager.closedSessions().single().status)
    }

    @Test
    fun managerCoordinatesActiveAndClosedStateBehindSingleLock() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackSessionManager.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("private val lock = Any()"))
        assertTrue(source.contains("synchronized(lock)"))
    }

    @Test
    fun managerUsesSingleStateModelForActiveAndClosedSessions() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackSessionManager.kt")),
            Charsets.UTF_8,
        )

        assertFalse(source.contains("private var active: PlaybackSession? = null"))
        assertFalse(source.contains("private val closed = ArrayDeque<PlaybackSession>()"))
        assertTrue(source.contains("private var state = PlaybackSessionManagerState()"))
        assertFalse(Files.exists(Paths.get("src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackSessionManagerState.kt")))
        assertTrue(source.contains("internal data class PlaybackSessionManagerState("))
        assertTrue(source.contains("fun withStartedSession("))
    }

    @Test
    fun closedSessionHistoryIsBoundedAndKeepsMostRecentEntries() {
        val manager = PlaybackSessionManager(
            createSessionId = object {
                var next = 0
                fun create(): String {
                    next += 1
                    return "session-$next"
                }
            }::create,
            cleanupSession = {},
        )

        repeat(PlaybackSessionManager.MAX_CLOSED_SESSION_HISTORY + 5) { index ->
            manager.startSession(
                sourceUrl = "https://example.com/$index.m3u8",
                entryManifestUrl = "https://example.com/$index.m3u8",
                localRootDir = "/tmp/$index",
            )
        }

        val closed = manager.closedSessions()

        assertEquals(PlaybackSessionManager.MAX_CLOSED_SESSION_HISTORY, closed.size)
        assertEquals(
            "session-5",
            closed.first().sessionId,
        )
        assertEquals(
            "session-${PlaybackSessionManager.MAX_CLOSED_SESSION_HISTORY + 4}",
            closed.last().sessionId,
        )
        assertTrue(!manager.isClosedSessionId("session-1"))
        assertTrue(manager.isClosedSessionId("session-5"))
    }
}
