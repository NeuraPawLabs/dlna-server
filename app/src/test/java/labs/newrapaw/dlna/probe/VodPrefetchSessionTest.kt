package labs.newrapaw.dlna.probe

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPrefetchSessionTest {
    @Test
    fun sessionSchedulesSegmentsInOrderWithBoundedConcurrency() {
        val started = CopyOnWriteArrayList<Int>()
        val cached = CopyOnWriteArrayList<Int>()
        val release = CountDownLatch(1)
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..4).map { HlsSegmentEntry(it, "https://example.com/$it.ts") },
            initialConcurrency = 2,
            fetchSegment = { entry ->
                started += entry.index
                release.await(2, TimeUnit.SECONDS)
                byteArrayOf(entry.index.toByte())
            },
            cacheSegment = { _, index, _ -> cached += index },
            isCached = { false },
            logger = {},
            executor = Executors.newCachedThreadPool(),
        )

        session.start()

        eventually {
            assertTrue(started.containsAll(listOf(0, 1)))
            assertEquals(2, started.size)
        }

        release.countDown()
        session.awaitIdle(2, TimeUnit.SECONDS)

        assertTrue(cached.containsAll(listOf(0, 1, 2, 3, 4)))
    }

    @Test
    fun sessionUpdatesConcurrencyImmediately() {
        val release = CountDownLatch(1)
        val started = CopyOnWriteArrayList<Int>()
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..4).map { HlsSegmentEntry(it, "https://example.com/$it.ts") },
            initialConcurrency = 1,
            fetchSegment = { entry ->
                started += entry.index
                release.await(2, TimeUnit.SECONDS)
                byteArrayOf(entry.index.toByte())
            },
            cacheSegment = { _, _, _ -> },
            isCached = { false },
            logger = {},
            executor = Executors.newCachedThreadPool(),
        )

        session.start()
        eventually { assertEquals(1, started.size) }

        session.updateConcurrency(4)

        eventually {
            assertTrue(started.containsAll(listOf(0, 1, 2, 3)))
            assertEquals(4, session.stats().configuredConcurrency)
        }

        release.countDown()
        session.awaitIdle(2, TimeUnit.SECONDS)
    }

    @Test
    fun sessionPromotesPlayerRequestedSegmentAheadOfBackgroundProgress() {
        val started = CopyOnWriteArrayList<Int>()
        val releases = ConcurrentHashMap<Int, CountDownLatch>()
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..5).map { index ->
                releases[index] = CountDownLatch(1)
                HlsSegmentEntry(index, "https://example.com/$index.ts")
            },
            initialConcurrency = 1,
            fetchSegment = { entry ->
                started += entry.index
                releases.getValue(entry.index).await(2, TimeUnit.SECONDS)
                byteArrayOf(entry.index.toByte())
            },
            cacheSegment = { _, _, _ -> },
            isCached = { false },
            logger = {},
            executor = Executors.newCachedThreadPool(),
        )

        session.start()
        eventually { assertEquals(listOf(0), started.toList()) }

        session.onSegmentRequested("https://example.com/4.ts")

        eventually { assertTrue(started.contains(4)) }

        releases.values.forEach { it.countDown() }
        session.awaitIdle(2, TimeUnit.SECONDS)
    }

    @Test
    fun sessionEmitsDetailedDiagnosticsForBackgroundAndPriorityFetches() {
        val logs = CopyOnWriteArrayList<String>()
        val releases = ConcurrentHashMap<Int, CountDownLatch>()
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..2).map { index ->
                releases[index] = CountDownLatch(1)
                HlsSegmentEntry(index, "https://example.com/$index.ts")
            },
            initialConcurrency = 1,
            fetchSegment = { entry ->
                releases.getValue(entry.index).await(2, TimeUnit.SECONDS)
                byteArrayOf(entry.index.toByte())
            },
            cacheSegment = { _, _, _ -> },
            isCached = { false },
            logger = {},
            diagnosticsLogger = logs::add,
            executor = Executors.newCachedThreadPool(),
        )

        session.start()
        eventually {
            assertTrue(logs.any { it.contains("[diag] background fetch start") && it.contains("index=0") })
        }

        session.onSegmentRequested("https://example.com/2.ts")
        eventually {
            assertTrue(logs.any { it.contains("[diag] priority fetch start") && it.contains("index=2") })
        }

        releases.values.forEach { it.countDown() }
        session.awaitIdle(2, TimeUnit.SECONDS)

        assertTrue(logs.any { it.contains("[diag] background fetch complete") && it.contains("index=0") })
        assertTrue(logs.any { it.contains("[diag] priority fetch complete") && it.contains("index=2") })
    }

    @Test
    fun sessionPublishesPlaybackIndexUpdates() {
        val updates = CopyOnWriteArrayList<Int>()
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..2).map { HlsSegmentEntry(it, "https://example.com/$it.ts") },
            initialConcurrency = 1,
            fetchSegment = { entry -> byteArrayOf(entry.index.toByte()) },
            cacheSegment = { _, _, _ -> },
            isCached = { false },
            logger = {},
            onPlaybackIndexUpdated = updates::add,
            executor = Executors.newCachedThreadPool(),
        )

        session.onSegmentRequested("https://example.com/1.ts")
        session.onSegmentRequested("https://example.com/0.ts")
        session.onSegmentRequested("https://example.com/2.ts")

        assertEquals(listOf(1, 2), updates.distinct())
    }

    private fun eventually(timeoutMs: Long = 2000, assertion: () -> Unit) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastError: AssertionError? = null
        while (System.nanoTime() < deadline) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                lastError = error
                Thread.sleep(25)
            }
        }
        throw lastError ?: AssertionError("eventually block did not complete")
    }
}
