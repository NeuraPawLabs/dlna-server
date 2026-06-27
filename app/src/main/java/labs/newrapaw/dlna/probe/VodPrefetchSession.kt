package labs.newrapaw.dlna.probe

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class VodPrefetchSessionStats(
    val configuredConcurrency: Int,
    val currentPlayIndex: Int,
    val nextPrefetchIndex: Int,
    val inFlightCount: Int,
    val completedCount: Int,
)

class VodPrefetchSession(
    private val manifestId: String,
    private val segmentEntries: List<HlsSegmentEntry>,
    initialConcurrency: Int,
    private val fetchSegment: (HlsSegmentEntry) -> ByteArray,
    private val cacheSegment: (String, Int, ByteArray) -> Unit,
    private val isCached: (String) -> Boolean,
    private val logger: (String) -> Unit,
    private val diagnosticsLogger: (String) -> Unit = {},
    private val onPlaybackIndexUpdated: (Int) -> Unit = {},
    private val executor: ExecutorService,
) {
    private val cancelled = AtomicBoolean(false)
    private val lock = Any()
    private val configuredConcurrency = AtomicInteger(clampConcurrency(initialConcurrency))
    private val currentPlayIndex = AtomicInteger(0)
    private val nextPrefetchIndex = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    private val inFlightCount = AtomicInteger(0)
    private val inFlightIndexes = linkedSetOf<Int>()
    private val completedIndexes = linkedSetOf<Int>()
    private val priorityQueue = ArrayDeque<Int>()
    private var idleSignal = CountDownLatch(1)

    fun start() {
        logger("VOD prefetch session created: $manifestId concurrency=${configuredConcurrency.get()} segments=${segmentEntries.size}")
        pump()
    }

    fun cancel() {
        cancelled.set(true)
        synchronized(lock) {
            priorityQueue.clear()
            inFlightIndexes.clear()
            idleSignal.countDown()
        }
    }

    fun updateConcurrency(value: Int) {
        configuredConcurrency.set(clampConcurrency(value))
        logger("VOD prefetch concurrency updated: ${configuredConcurrency.get()}")
        pump()
    }

    fun onSegmentRequested(url: String) {
        val entry = segmentEntries.firstOrNull { it.url == url } ?: return
        updatePlaybackIndex(entry.index)
        synchronized(lock) {
            if (!completedIndexes.contains(entry.index) && !inFlightIndexes.contains(entry.index)) {
                priorityQueue.remove(entry.index)
                priorityQueue.addFirst(entry.index)
            }
        }
        pump()
    }

    fun updatePlaybackIndex(index: Int) {
        val updated = currentPlayIndex.updateAndGet { current -> maxOf(current, index) }
        if (updated == index) {
            onPlaybackIndexUpdated(updated)
            diagnosticsLogger("[diag] playback index update manifestId=$manifestId index=$updated")
        }
    }

    fun stats(): VodPrefetchSessionStats =
        VodPrefetchSessionStats(
            configuredConcurrency = configuredConcurrency.get(),
            currentPlayIndex = currentPlayIndex.get(),
            nextPrefetchIndex = nextPrefetchIndex.get(),
            inFlightCount = inFlightCount.get(),
            completedCount = completedCount.get(),
        )

    fun awaitIdle(timeout: Long, unit: TimeUnit): Boolean =
        synchronized(lock) { idleSignal }.await(timeout, unit)

    private fun pump() {
        if (cancelled.get()) return
        synchronized(lock) {
            resetIdleSignalIfNeeded()
        }
        while (!cancelled.get()) {
            val dispatch = synchronized(lock) {
                val hasPriorityWork = priorityQueue.isNotEmpty()
                val maxAllowedInFlight = configuredConcurrency.get() + if (hasPriorityWork) 1 else 0
                if (inFlightIndexes.size >= maxAllowedInFlight) return
                dequeueNextIndexLocked()
            } ?: return

            inFlightCount.incrementAndGet()
            executor.execute {
                val entry = segmentEntries[dispatch.index]
                val fetchLabel = if (dispatch.priority) "priority" else "background"
                val startedAt = System.nanoTime()
                try {
                    if (!isCached(entry.url)) {
                        diagnosticsLogger("[diag] $fetchLabel fetch start manifestId=$manifestId index=${entry.index} url=${entry.url}")
                        val bytes = fetchSegment(entry)
                        cacheSegment(entry.url, entry.index, bytes)
                        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                        diagnosticsLogger(
                            "[diag] $fetchLabel fetch complete manifestId=$manifestId index=${entry.index} bytes=${bytes.size} elapsedMs=$elapsedMs",
                        )
                    } else {
                        diagnosticsLogger("[diag] cache hit skip fetch manifestId=$manifestId index=${entry.index} url=${entry.url}")
                    }
                    synchronized(lock) {
                        completedIndexes.add(dispatch.index)
                    }
                    completedCount.incrementAndGet()
                } catch (error: Throwable) {
                    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    diagnosticsLogger(
                        "[diag] $fetchLabel fetch fail manifestId=$manifestId index=${entry.index} elapsedMs=$elapsedMs error=${error::class.java.simpleName}:${error.message}",
                    )
                    throw error
                } finally {
                    inFlightCount.decrementAndGet()
                    synchronized(lock) {
                        inFlightIndexes.remove(dispatch.index)
                        signalIdleIfNeededLocked()
                    }
                    pump()
                }
            }
        }
    }

    private fun dequeueNextIndexLocked(): FetchDispatch? {
        while (priorityQueue.isNotEmpty()) {
            val priorityIndex = priorityQueue.removeFirst()
            if (claimIndexLocked(priorityIndex)) return FetchDispatch(priorityIndex, priority = true)
        }
        while (nextPrefetchIndex.get() < segmentEntries.size) {
            val index = nextPrefetchIndex.getAndIncrement()
            if (claimIndexLocked(index)) return FetchDispatch(index, priority = false)
        }
        signalIdleIfNeededLocked()
        return null
    }

    private fun claimIndexLocked(index: Int): Boolean {
        if (index !in segmentEntries.indices) return false
        if (completedIndexes.contains(index)) return false
        if (inFlightIndexes.contains(index)) return false
        val entry = segmentEntries[index]
        if (isCached(entry.url)) {
            completedIndexes.add(index)
            completedCount.incrementAndGet()
            return false
        }
        inFlightIndexes.add(index)
        return true
    }

    private fun resetIdleSignalIfNeeded() {
        if (idleSignal.count == 0L && (inFlightIndexes.isNotEmpty() || nextPrefetchIndex.get() < segmentEntries.size || priorityQueue.isNotEmpty())) {
            idleSignal = CountDownLatch(1)
        }
    }

    private fun signalIdleIfNeededLocked() {
        if (inFlightIndexes.isEmpty() && priorityQueue.isEmpty() && nextPrefetchIndex.get() >= segmentEntries.size) {
            idleSignal.countDown()
        }
    }

    private fun clampConcurrency(value: Int): Int =
        value.coerceIn(ProxySettingsState.MIN_PREFETCH_CONCURRENCY, ProxySettingsState.MAX_PREFETCH_CONCURRENCY)

    private data class FetchDispatch(
        val index: Int,
        val priority: Boolean,
    )
}
