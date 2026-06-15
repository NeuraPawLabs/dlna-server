package labs.newrapaw.dlna.probe

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicLong

data class HlsSegmentCacheStats(
    val entries: Int,
    val sizeBytes: Long,
    val hits: Long,
    val misses: Long,
    val inFlight: Int,
)

class HlsSegmentCache(
    private val directory: File,
    private val maxBytes: Long,
) {
    private val lock = Any()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<ByteArray>>()
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val touchCounter = AtomicLong(0)

    init {
        directory.mkdirs()
    }

    fun getOrFetch(url: String, fetcher: () -> ByteArray): ByteArray {
        val cached = readCached(url)
        if (cached != null) {
            hits.incrementAndGet()
            return cached
        }

        val future = CompletableFuture<ByteArray>()
        val existing = inFlight.putIfAbsent(url, future)
        if (existing != null) {
            return await(existing)
        }

        misses.incrementAndGet()
        return try {
            val bytes = fetcher()
            writeCached(url, bytes)
            future.complete(bytes)
            bytes
        } catch (error: Throwable) {
            future.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(url, future)
        }
    }

    fun prefetch(
        url: String,
        executor: Executor = ForkJoinPool.commonPool(),
        fetcher: () -> ByteArray,
    ) {
        if (hasCached(url) || inFlight.containsKey(url)) return
        executor.execute {
            runCatching { getOrFetch(url, fetcher) }
        }
    }

    fun clear() = synchronized(lock) {
        directory.listFiles()?.forEach { it.delete() }
    }

    fun stats(): HlsSegmentCacheStats = synchronized(lock) {
        val files = segmentFiles()
        HlsSegmentCacheStats(
            entries = files.size,
            sizeBytes = files.sumOf { it.length() },
            hits = hits.get(),
            misses = misses.get(),
            inFlight = inFlight.size,
        )
    }

    private fun readCached(url: String): ByteArray? = synchronized(lock) {
        val file = cacheFile(url)
        if (!file.isFile) return@synchronized null

        touch(file)
        file.readBytes()
    }

    private fun hasCached(url: String): Boolean = synchronized(lock) {
        cacheFile(url).isFile
    }

    private fun writeCached(url: String, bytes: ByteArray) = synchronized(lock) {
        directory.mkdirs()
        val file = cacheFile(url)
        file.writeBytes(bytes)
        touch(file)
        trimToMaxBytes()
    }

    private fun trimToMaxBytes() {
        var files = segmentFiles()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            val length = file.length()
            if (file.delete()) total -= length
        }
        files = segmentFiles()
        if (files.sumOf { it.length() } > maxBytes) {
            files.sortedBy { it.name }.forEach { it.delete() }
        }
    }

    private fun segmentFiles(): List<File> =
        directory.listFiles()?.filter { it.isFile && it.extension == "seg" }.orEmpty()

    private fun cacheFile(url: String): File =
        File(directory, "${sha256(url)}.seg")

    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis() + touchCounter.incrementAndGet())
    }

    private fun await(future: CompletableFuture<ByteArray>): ByteArray =
        try {
            future.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
