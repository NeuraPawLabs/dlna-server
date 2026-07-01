package labs.newrapaw.dlna.probe.core.session

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import labs.newrapaw.dlna.probe.core.ProxySettingsState

class SessionPrefetchController(
    private val queue: ArrayDeque<String>,
    private val executor: ExecutorService,
    initialConcurrency: Int,
    private var loadAsset: (String) -> Unit,
) {
    private val running = AtomicBoolean(true)
    private val coordinatorStarted = AtomicBoolean(false)
    private val coordinatorThread = AtomicReference<Thread?>(null)
    private val desiredConcurrency = AtomicInteger(initialConcurrency.coerceIn(1, ProxySettingsState.MAX_PREFETCH_CONCURRENCY))
    private val activeCount = AtomicInteger(0)
    private val activeAssetIds = linkedSetOf<String>()
    private val lock = Object()

    fun replaceLoadAsset(next: (String) -> Unit) {
        loadAsset = next
    }

    fun start() {
        if (!coordinatorStarted.compareAndSet(false, true)) return
        val thread = Thread(
            {
                runCoordinator()
            },
            "pawcast-session-prefetch",
        ).apply {
            isDaemon = true
        }
        coordinatorThread.set(thread)
        thread.start()
    }

    private fun runCoordinator() {
        try {
            while (running.get()) {
                var waitingForCapacityRetry = false
                while (running.get() && activeCount.get() < desiredConcurrency.get()) {
                    val assetId = synchronized(lock) {
                        if (queue.isEmpty()) null else queue.removeFirst()
                    } ?: break
                    if (!submitAssetLoad(assetId)) {
                        waitingForCapacityRetry = true
                        break
                    }
                }
                synchronized(lock) {
                    if (!running.get()) return
                    val shouldRetrySoon = waitingForCapacityRetry || (queue.isNotEmpty() && activeCount.get() == 0)
                    if (shouldRetrySoon) {
                        runCatching { lock.wait(REJECTED_SUBMISSION_RETRY_WAIT_MS) }
                    } else {
                        runCatching { lock.wait() }
                    }
                }
            }
        } finally {
            coordinatorThread.compareAndSet(Thread.currentThread(), null)
        }
    }

    private fun submitAssetLoad(assetId: String): Boolean {
        activeCount.incrementAndGet()
        synchronized(lock) { activeAssetIds += assetId }
        return try {
            executor.execute {
                try {
                    loadAsset(assetId)
                } finally {
                    activeCount.decrementAndGet()
                    synchronized(lock) {
                        activeAssetIds.remove(assetId)
                        lock.notifyAll()
                    }
                }
            }
            true
        } catch (error: RejectedExecutionException) {
            activeCount.decrementAndGet()
            synchronized(lock) {
                activeAssetIds.remove(assetId)
                if (running.get()) {
                    queue.addFirst(assetId)
                }
                lock.notifyAll()
            }
            false
        }
    }

    fun updateConcurrency(value: Int) {
        desiredConcurrency.set(value.coerceIn(1, ProxySettingsState.MAX_PREFETCH_CONCURRENCY))
        synchronized(lock) { lock.notifyAll() }
    }

    fun replaceQueue(assetIds: List<String>) {
        synchronized(lock) {
            queue.clear()
            queue.addAll(assetIds)
            lock.notifyAll()
        }
    }

    fun enqueueFront(assetId: String) {
        synchronized(lock) {
            if (assetId in activeAssetIds || assetId in queue) return
            queue.addFirst(assetId)
            lock.notifyAll()
        }
    }

    fun snapshotQueue(): List<String> = synchronized(lock) { queue.toList() }

    fun snapshotActiveAssetIds(): List<String> = synchronized(lock) { activeAssetIds.toList() }

    fun snapshotPlannedAssetIds(): List<String> = synchronized(lock) { (activeAssetIds.toList() + queue.toList()).distinct() }

    fun cancel() {
        if (!running.getAndSet(false)) return
        synchronized(lock) { lock.notifyAll() }
        coordinatorThread.get()?.interrupt()
    }

    private companion object {
        const val REJECTED_SUBMISSION_RETRY_WAIT_MS = 50L
    }
}
