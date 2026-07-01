package labs.newrapaw.dlna.probe.core.session

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPrefetchControllerTest {
    @Test
    fun rejectedWorkerSubmissionDoesNotLosePrefetchAsset() {
        val blockerReleased = CountDownLatch(1)
        val executor = ThreadPoolExecutor(
            1,
            1,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            ThreadPoolExecutor.AbortPolicy(),
        )
        executor.execute { blockerReleased.await(2, TimeUnit.SECONDS) }
        val loaded = CountDownLatch(1)
        val controller = SessionPrefetchController(
            queue = ArrayDeque(listOf("asset-1")),
            executor = executor,
            initialConcurrency = 1,
        ) {
            loaded.countDown()
        }

        try {
            controller.start()

            assertFalse(loaded.await(200, TimeUnit.MILLISECONDS))

            blockerReleased.countDown()

            assertTrue(loaded.await(2, TimeUnit.SECONDS))
            assertTrue(
                waitUntil(timeoutMs = 2_000L) {
                    controller.snapshotQueue().isEmpty() &&
                        controller.snapshotActiveAssetIds().isEmpty()
                },
            )
        } finally {
            controller.cancel()
            blockerReleased.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun singleThreadExecutorStillRunsPrefetchWork() {
        val executor = Executors.newSingleThreadExecutor()
        val loaded = CountDownLatch(1)
        val controller = SessionPrefetchController(
            queue = ArrayDeque(listOf("asset-1")),
            executor = executor,
            initialConcurrency = 1,
        ) {
            loaded.countDown()
        }

        try {
            controller.start()

            assertTrue(loaded.await(2, TimeUnit.SECONDS))
        } finally {
            controller.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun enqueueFrontAfterQueueDrainsStillSchedulesAsset() {
        val executor = Executors.newFixedThreadPool(2)
        val firstLoaded = CountDownLatch(1)
        val secondLoaded = CountDownLatch(1)
        val loadedAssets = mutableListOf<String>()
        val failure = AtomicReference<Throwable?>(null)
        val controller = SessionPrefetchController(
            queue = ArrayDeque(listOf("asset-1")),
            executor = executor,
            initialConcurrency = 1,
        ) { assetId ->
            synchronized(loadedAssets) {
                loadedAssets += assetId
            }
            when (assetId) {
                "asset-1" -> firstLoaded.countDown()
                "asset-2" -> secondLoaded.countDown()
            }
        }

        try {
            controller.start()
            assertTrue(firstLoaded.await(2, TimeUnit.SECONDS))

            val drained = waitUntil(timeoutMs = 2_000L) {
                controller.snapshotQueue().isEmpty() &&
                    controller.snapshotActiveAssetIds().isEmpty()
            }
            assertTrue("prefetch queue should drain before re-enqueue", drained)

            runCatching { controller.enqueueFront("asset-2") }
                .onFailure(failure::set)

            assertEquals(null, failure.get())
            assertTrue(secondLoaded.await(2, TimeUnit.SECONDS))
            synchronized(loadedAssets) {
                assertEquals(listOf("asset-1", "asset-2"), loadedAssets)
            }
        } finally {
            controller.cancel()
            executor.shutdownNow()
        }
    }
}

private fun waitUntil(
    timeoutMs: Long,
    condition: () -> Boolean,
): Boolean {
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (System.nanoTime() < deadlineNanos) {
        if (condition()) {
            return true
        }
        Thread.sleep(10L)
    }
    return condition()
}
