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
    private val diagnosticsLogger: (String) -> Unit = {},
) {
    private val lock = Any()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<ByteArray>>()
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val touchCounter = AtomicLong(0)
    private val metadataByKey = mutableMapOf<String, CachedSegmentMetadata>()
    private val playbackPositions = mutableMapOf<String, Int>()

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
            writeCached(url, null, null, bytes)
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
        metadataByKey.clear()
        playbackPositions.clear()
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

    fun store(url: String, manifestId: String, segmentIndex: Int, bytes: ByteArray) {
        writeCached(url, manifestId, segmentIndex, bytes)
    }

    fun readIfCached(url: String): ByteArray? = readCached(url)

    fun updatePlaybackPosition(manifestId: String, currentPlayIndex: Int) = synchronized(lock) {
        playbackPositions[manifestId] = currentPlayIndex
        diagnosticsLogger("[diag] playback position update manifestId=$manifestId index=$currentPlayIndex")
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

    private fun writeCached(url: String, manifestId: String?, segmentIndex: Int?, bytes: ByteArray) = synchronized(lock) {
        directory.mkdirs()
        val key = cacheKey(url)
        val file = cacheFileForKey(key)
        file.writeBytes(bytes)
        val accessOrder = touch(file)
        metadataByKey[key] = CachedSegmentMetadata(
            url = url,
            manifestId = manifestId,
            segmentIndex = segmentIndex,
            lastAccessOrder = accessOrder,
        )
        diagnosticsLogger(
            "[diag] cache write url=$url bytes=${bytes.size} manifestId=${manifestId ?: "-"} index=${segmentIndex ?: -1}",
        )
        trimToMaxBytes()
    }

    private fun trimToMaxBytes() {
        var files = segmentFiles()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        files.sortedWith(compareBy<File> { evictionBucket(it) }.thenBy { evictionRank(it) }.thenBy { it.lastModified() }).forEach { file ->
            if (total <= maxBytes) return
            val length = file.length()
            if (file.delete()) {
                val metadata = metadataByKey[file.nameWithoutExtension]
                metadataByKey.remove(file.nameWithoutExtension)
                total -= length
                diagnosticsLogger(
                    "[diag] cache evict url=${metadata?.url ?: file.name} bytes=$length reason=max-bytes bucket=${evictionBucketForLog(metadata)}",
                )
            }
        }
        files = segmentFiles()
        if (files.sumOf { it.length() } > maxBytes) {
            files.sortedBy { it.name }.forEach {
                val metadata = metadataByKey[it.nameWithoutExtension]
                metadataByKey.remove(it.nameWithoutExtension)
                it.delete()
                diagnosticsLogger(
                    "[diag] cache evict url=${metadata?.url ?: it.name} bytes=${it.length()} reason=overflow-reset bucket=${evictionBucketForLog(metadata)}",
                )
            }
        }
    }

    private fun segmentFiles(): List<File> =
        directory.listFiles()?.filter { it.isFile && it.extension == "seg" }.orEmpty()

    private fun cacheFile(url: String): File =
        cacheFileForKey(cacheKey(url))

    private fun cacheFileForKey(key: String): File =
        File(directory, "$key.seg")

    private fun touch(file: File): Long {
        val accessOrder = touchCounter.incrementAndGet()
        file.setLastModified(System.currentTimeMillis() + accessOrder)
        metadataByKey[file.nameWithoutExtension]?.lastAccessOrder = accessOrder
        return accessOrder
    }

    private fun await(future: CompletableFuture<ByteArray>): ByteArray =
        try {
            future.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }

    private fun cacheKey(url: String): String = sha256(url)

    private fun evictionBucket(file: File): Int {
        val metadata = metadataByKey[file.nameWithoutExtension] ?: return 2
        val manifestId = metadata.manifestId ?: return 2
        val segmentIndex = metadata.segmentIndex ?: return 2
        val playIndex = playbackPositions[manifestId] ?: return 2
        return if (segmentIndex < playIndex) 0 else 1
    }

    private fun evictionRank(file: File): Long {
        val metadata = metadataByKey[file.nameWithoutExtension] ?: return file.lastModified()
        val manifestId = metadata.manifestId
        val segmentIndex = metadata.segmentIndex
        if (manifestId == null || segmentIndex == null) return metadata.lastAccessOrder
        val playIndex = playbackPositions[manifestId] ?: return metadata.lastAccessOrder
        return if (segmentIndex < playIndex) segmentIndex.toLong() else -segmentIndex.toLong()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun evictionBucketForLog(metadata: CachedSegmentMetadata?): String {
        if (metadata?.manifestId == null || metadata.segmentIndex == null) return "untracked"
        val playIndex = playbackPositions[metadata.manifestId] ?: return "unknown-playback"
        return if (metadata.segmentIndex < playIndex) "played" else "forward"
    }
}

private data class CachedSegmentMetadata(
    val url: String,
    val manifestId: String?,
    val segmentIndex: Int?,
    var lastAccessOrder: Long,
)
