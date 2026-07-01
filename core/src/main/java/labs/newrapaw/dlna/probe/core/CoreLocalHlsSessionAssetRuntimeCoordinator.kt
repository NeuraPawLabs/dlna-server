package labs.newrapaw.dlna.probe.core

import java.io.File
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore

internal sealed class SessionAssetAcquireResult {
    data class Stored(val bytes: ByteArray) : SessionAssetAcquireResult()

    data class Waited(val bytes: ByteArray?) : SessionAssetAcquireResult()

    data class Download(val runtime: SessionAssetRuntime) : SessionAssetAcquireResult()
}

internal class CoreLocalHlsSessionAssetRuntimeCoordinator(
    private val sessionAssetStore: SessionAssetStore,
    private val assetWaitTimeoutMs: Long,
) {
    fun acquire(
        sessionId: String,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
    ): SessionAssetAcquireResult {
        val runtime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.readAsset(sessionId, assetId)
            if (existing != null) {
                markReadyFromStoredAsset(
                    runtime = runtime,
                    file = existing.file,
                    bytes = existing.bytes,
                )
                return SessionAssetAcquireResult.Stored(existing.bytes)
            }
            if (runtime.state == SessionAssetState.DOWNLOADING) {
                return SessionAssetAcquireResult.Waited(
                    waitForPreparedAssetInFlight(
                        sessionId = sessionId,
                        assetRuntime = assetRuntime,
                        assetId = assetId,
                        runtime = runtime,
                    ),
                )
            }
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
            return SessionAssetAcquireResult.Download(runtime)
        }
    }

    fun markCancelled(runtime: SessionAssetRuntime) {
        synchronized(runtime.lock) {
            runtime.state = SessionAssetState.NOT_STARTED
            runtime.lastError = "cancelled"
            runtime.lock.notifyAll()
        }
    }

    fun markAttemptStarting(runtime: SessionAssetRuntime) {
        synchronized(runtime.lock) {
            runtime.retryCount += 1
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
        }
    }

    fun markReady(
        runtime: SessionAssetRuntime,
        file: File,
        bytes: ByteArray,
        elapsedMs: Long,
        source: String,
        upstreamFirstByteMs: Long,
        upstreamCompleteMs: Long,
        diskWriteMs: Long,
    ) {
        synchronized(runtime.lock) {
            runtime.state = SessionAssetState.READY
            runtime.localFile = file
            runtime.localSizeBytes = bytes.size.toLong()
            runtime.lastElapsedMs = elapsedMs
            runtime.lastSource = source
            runtime.upstreamFirstByteMs = upstreamFirstByteMs
            runtime.upstreamCompleteMs = upstreamCompleteMs
            runtime.diskWriteMs = diskWriteMs.coerceAtLeast(0L)
            runtime.lastError = null
            runtime.lock.notifyAll()
        }
    }

    fun markFailure(
        runtime: SessionAssetRuntime,
        error: Throwable,
        cancelled: Boolean,
        lastAttempt: Boolean,
    ) {
        synchronized(runtime.lock) {
            runtime.lastElapsedMs = null
            runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
            runtime.lastSource = "session-local"
            if (cancelled) {
                runtime.state = SessionAssetState.NOT_STARTED
            } else if (lastAttempt) {
                runtime.state = SessionAssetState.FAILED
            }
            runtime.lock.notifyAll()
        }
    }

    fun markInterruptedDuringBackoff(
        runtime: SessionAssetRuntime,
        error: InterruptedException,
    ) {
        synchronized(runtime.lock) {
            runtime.state = SessionAssetState.NOT_STARTED
            runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
            runtime.lock.notifyAll()
        }
    }

    private fun markReadyFromStoredAsset(
        runtime: SessionAssetRuntime,
        file: File,
        bytes: ByteArray,
    ) {
        runtime.state = SessionAssetState.READY
        runtime.localFile = file
        runtime.localSizeBytes = bytes.size.toLong()
        runtime.lastSource = "session-local"
    }

    private fun waitForPreparedAssetInFlight(
        sessionId: String,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
        runtime: SessionAssetRuntime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() },
    ): ByteArray? {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(assetWaitTimeoutMs)
        while (true) {
            val state = synchronized(runtime.lock) {
                val existing = sessionAssetStore.readAsset(sessionId, assetId)
                if (existing != null) {
                    markReadyFromStoredAsset(
                        runtime = runtime,
                        file = existing.file,
                        bytes = existing.bytes,
                    )
                    return existing.bytes
                }
                val currentState = runtime.state
                if (currentState == SessionAssetState.FAILED || currentState == SessionAssetState.NOT_STARTED) {
                    return null
                }
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) {
                    return null
                }
                try {
                    runtime.lock.wait(minOf(50L, TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
                currentState
            }
            if (state == SessionAssetState.FAILED || state == SessionAssetState.NOT_STARTED) {
                return null
            }
        }
    }
}
