package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
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
}
