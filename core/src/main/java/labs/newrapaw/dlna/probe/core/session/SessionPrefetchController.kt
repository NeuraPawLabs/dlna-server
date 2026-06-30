package labs.newrapaw.dlna.probe.core.session

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import labs.newrapaw.dlna.probe.core.ProxySettingsState

class SessionPrefetchController(
    private val queue: ArrayDeque<String>,
    private val executor: ExecutorService,
    initialConcurrency: Int,
    private var loadAsset: (String) -> Unit,
) {
    private val running = AtomicBoolean(true)
    private val coordinatorStarted = AtomicBoolean(false)
    private val desiredConcurrency = AtomicInteger(initialConcurrency.coerceIn(1, ProxySettingsState.MAX_PREFETCH_CONCURRENCY))
    private val activeCount = AtomicInteger(0)
    private val activeAssetIds = linkedSetOf<String>()
    private val lock = Object()

    fun replaceLoadAsset(next: (String) -> Unit) {
        loadAsset = next
    }

    fun start() {
        if (!coordinatorStarted.compareAndSet(false, true)) return
        executor.execute {
            while (running.get()) {
                while (running.get() && activeCount.get() < desiredConcurrency.get()) {
                    val assetId = synchronized(lock) {
                        if (queue.isEmpty()) null else queue.removeFirst()
                    } ?: break
                    activeCount.incrementAndGet()
                    synchronized(lock) { activeAssetIds += assetId }
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
                }
                synchronized(lock) {
                    if (!running.get()) return@execute
                    if (queue.isEmpty() && activeCount.get() == 0) return@execute
                    runCatching { lock.wait(100L) }
                }
            }
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
        running.set(false)
        synchronized(lock) { lock.notifyAll() }
    }
}
