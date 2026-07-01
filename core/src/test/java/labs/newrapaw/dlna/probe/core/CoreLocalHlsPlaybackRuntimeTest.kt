package labs.newrapaw.dlna.probe.core

import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import labs.newrapaw.dlna.probe.core.session.SessionTimeline
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer
import labs.newrapaw.dlna.probe.core.session.SessionPrefetchController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout

class CoreLocalHlsPlaybackRuntimeTest {
    @Test
    fun snapshotExposesConsistentSessionAndTelemetryState() {
        val sessionAssetRootDir = Files.createTempDirectory("runtime-test").toFile()
        val runtime = CoreLocalHlsPlaybackRuntime(
            sessionAssetStore = SessionAssetStore(sessionAssetRootDir),
            sessionLocalServer = SessionLocalServer(),
        )
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = sessionAssetRootDir.resolve("session-1").absolutePath,
        )

        try {
            runtime.openSession(session)
            runtime.updatePlayerTelemetry(positionMs = 12_000L, bufferedPositionMs = 18_000L)

            val snapshot = runtime.snapshot()

            assertEquals("session-1", snapshot.activeSessionShell?.sessionId)
            assertEquals(12_000L, snapshot.latestPlayerPositionMs)
            assertEquals(18_000L, snapshot.latestBufferedPositionMs)
        } finally {
            runtime.close()
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupClearsSnapshotWhenRuntimeBecomesInactive() {
        val sessionAssetRootDir = Files.createTempDirectory("runtime-test").toFile()
        val runtime = CoreLocalHlsPlaybackRuntime(
            sessionAssetStore = SessionAssetStore(sessionAssetRootDir),
            sessionLocalServer = SessionLocalServer(),
        )
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = sessionAssetRootDir.resolve("session-1").absolutePath,
        )

        try {
            runtime.openSession(session)
            runtime.updatePlayerTelemetry(positionMs = 12_000L, bufferedPositionMs = 18_000L)

            runtime.cleanupSession(session.sessionId)

            val snapshot = runtime.snapshot()
            assertNull(snapshot.activeSessionShell)
            assertNull(snapshot.activePreparedSession)
            assertNull(snapshot.latestPlayerPositionMs)
            assertNull(snapshot.latestBufferedPositionMs)
        } finally {
            runtime.close()
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun requestPathPositionUpdateDoesNotOverwriteBufferedTelemetry() {
        val sessionAssetRootDir = Files.createTempDirectory("runtime-test").toFile()
        val runtime = CoreLocalHlsPlaybackRuntime(
            sessionAssetStore = SessionAssetStore(sessionAssetRootDir),
            sessionLocalServer = SessionLocalServer(),
        )
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = sessionAssetRootDir.resolve("session-1").absolutePath,
        )

        try {
            runtime.openSession(session)
            runtime.updatePlayerTelemetry(positionMs = 12_000L, bufferedPositionMs = 18_000L)

            runtime.updatePlaybackPosition(13_000L)

            val snapshot = runtime.snapshot()
            assertEquals(13_000L, snapshot.latestPlayerPositionMs)
            assertEquals(18_000L, snapshot.latestBufferedPositionMs)
        } finally {
            runtime.close()
            sessionAssetRootDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupSessionCancelsPreparedCallsBeforeWaitingForAssetStoreCleanup() {
        val sessionAssetRootDir = Files.createTempDirectory("runtime-test").toFile()
        val sessionAssetStore = SessionAssetStore(sessionAssetRootDir)
        val runtime = CoreLocalHlsPlaybackRuntime(
            sessionAssetStore = sessionAssetStore,
            sessionLocalServer = SessionLocalServer(),
        )
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = sessionAssetRootDir.resolve("session-1").absolutePath,
        )
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val prepared = PreparedSessionPlayback(
            session = session.copy(
                status = PlaybackSessionStatus.READY,
                timeline = SessionTimeline(emptyList(), emptyList()),
            ),
            masterManifest = "",
            videoPlaylist = "",
            primaryVideoTrackId = "video-main",
            videoPlaylists = emptyMap(),
            audioPlaylists = emptyMap(),
            subtitlePlaylists = emptyMap(),
            assetsById = emptyMap(),
            assetRuntime = mutableMapOf(),
            telemetryBridge = PlaybackTelemetryBridge(emptyList()),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = ArrayDeque(),
                executor = prefetchExecutor,
                initialConcurrency = 1,
                loadAsset = {},
            ),
            preparationFailure = null,
        )
        val call = RuntimeTestCall("https://example.com/segment.ts")
        prepared.callTracker.register(call)
        runtime.openSession(session)
        runtime.setActivePreparedSession(prepared)
        sessionAssetStore.writeAsset(session.sessionId, "video-0", byteArrayOf(0x01))
        val sessionStateLock = sessionStateLock(sessionAssetStore, session.sessionId)
        val cleanupThread = Thread {
            runtime.cleanupSession(session.sessionId)
        }

        try {
            synchronized(sessionStateLock) {
                cleanupThread.start()
                waitForBlockedThread(cleanupThread)
                assertTrue(
                    "cleanup should cancel in-flight calls before waiting on store cleanup",
                    prepared.callTracker.isCancelled(),
                )
            }
            cleanupThread.join(TimeUnit.SECONDS.toMillis(5))
            assertTrue("cleanup thread should complete", !cleanupThread.isAlive)
            assertTrue(call.cancelled.get())
        } finally {
            if (cleanupThread.isAlive) {
                cleanupThread.join(TimeUnit.SECONDS.toMillis(5))
            }
            prefetchExecutor.shutdownNow()
            runtime.close()
            sessionAssetRootDir.deleteRecursively()
        }
    }
}

private fun sessionStateLock(
    store: SessionAssetStore,
    sessionId: String,
): Any {
    val trackerField = SessionAssetStore::class.java.getDeclaredField("stateTracker").apply {
        isAccessible = true
    }
    val tracker = trackerField.get(store)
    val stateField = tracker.javaClass.getDeclaredField("state").apply {
        isAccessible = true
    }
    val trackerState = stateField.get(tracker)
    val statesField = trackerState.javaClass.getDeclaredField("sessionStates").apply {
        isAccessible = true
    }
    val states = statesField.get(trackerState) as Map<*, *>
    val state = states[sessionId] ?: error("missing session state for $sessionId")
    return state.javaClass.getDeclaredField("lock").apply {
        isAccessible = true
    }.get(state)
}

private fun waitForBlockedThread(thread: Thread) {
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (thread.state != Thread.State.BLOCKED) {
        if (System.nanoTime() >= deadlineNanos) {
            throw AssertionError("thread did not block on time; state=${thread.state}")
        }
        Thread.sleep(10L)
    }
}

private class RuntimeTestCall(
    private val requestUrl: String,
) : Call {
    val cancelled = AtomicBoolean(false)

    override fun request(): Request = Request.Builder().url(requestUrl).build()

    override fun execute(): Response = throw UnsupportedOperationException("Not used in test")

    override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException("Not used in test")

    override fun cancel() {
        cancelled.set(true)
    }

    override fun isExecuted(): Boolean = false

    override fun isCanceled(): Boolean = cancelled.get()

    override fun timeout(): Timeout = Timeout.NONE

    override fun clone(): Call = RuntimeTestCall(requestUrl)
}
