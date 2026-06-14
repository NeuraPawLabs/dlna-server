package labs.newrapaw.dlna.probe

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HlsSegmentCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun getOrFetchStoresSegmentAndServesSecondRequestFromCache() {
        val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 1024)
        val fetchCount = AtomicInteger(0)

        val first = cache.getOrFetch("https://cdn.example/seg0.ts") {
            fetchCount.incrementAndGet()
            byteArrayOf(1, 2, 3)
        }
        val second = cache.getOrFetch("https://cdn.example/seg0.ts") {
            fetchCount.incrementAndGet()
            byteArrayOf(9, 9, 9)
        }

        assertArrayEquals(byteArrayOf(1, 2, 3), first)
        assertArrayEquals(byteArrayOf(1, 2, 3), second)
        assertEquals(1, fetchCount.get())
        assertEquals(1, cache.stats().hits)
        assertEquals(1, cache.stats().misses)
    }

    @Test
    fun concurrentRequestsForSameSegmentShareSingleFetch() {
        val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 1024)
        val fetchCount = AtomicInteger(0)
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val first = executor.submit<ByteArray> {
            cache.getOrFetch("https://cdn.example/seg1.ts") {
                fetchCount.incrementAndGet()
                fetchStarted.countDown()
                releaseFetch.await(2, TimeUnit.SECONDS)
                byteArrayOf(4, 5, 6)
            }
        }
        assertTrue(fetchStarted.await(2, TimeUnit.SECONDS))
        val second = executor.submit<ByteArray> {
            cache.getOrFetch("https://cdn.example/seg1.ts") {
                fetchCount.incrementAndGet()
                byteArrayOf(7, 8, 9)
            }
        }

        releaseFetch.countDown()

        assertArrayEquals(byteArrayOf(4, 5, 6), first.get(2, TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(4, 5, 6), second.get(2, TimeUnit.SECONDS))
        assertEquals(1, fetchCount.get())
        executor.shutdownNow()
    }

    @Test
    fun trimRemovesLeastRecentlyUsedSegmentsWhenCacheExceedsMaxBytes() {
        val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 5)

        cache.getOrFetch("https://cdn.example/seg-a.ts") { byteArrayOf(1, 1, 1) }
        cache.getOrFetch("https://cdn.example/seg-b.ts") { byteArrayOf(2, 2, 2) }

        val stats = cache.stats()
        assertTrue(stats.sizeBytes <= 5)
        assertEquals(1, stats.entries)

        val refetched = cache.getOrFetch("https://cdn.example/seg-a.ts") { byteArrayOf(9, 9, 9) }
        assertArrayEquals(byteArrayOf(9, 9, 9), refetched)
    }

    @Test
    fun clearDeletesCachedSegmentsAndResetsSize() {
        val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 1024)
        cache.getOrFetch("https://cdn.example/seg2.ts") { byteArrayOf(1, 2, 3) }

        cache.clear()

        assertEquals(0, cache.stats().entries)
        assertEquals(0, cache.stats().sizeBytes)
    }
}
