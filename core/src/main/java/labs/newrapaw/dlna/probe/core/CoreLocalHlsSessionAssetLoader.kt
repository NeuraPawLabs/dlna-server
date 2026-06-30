package labs.newrapaw.dlna.probe.core

import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker

internal class CoreLocalHlsSessionAssetLoader(
    private val sessionAssetStore: SessionAssetStore,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val upstreamClient: CoreLocalHlsUpstreamClient,
    private val refreshDiagnosticsSnapshot: () -> Unit,
) {
    fun loadSessionAsset(
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): ByteArray {
        return preparedLoadAssetById(
            sessionId = prepared.session.sessionId,
            assetsById = prepared.assetsById,
            assetRuntime = prepared.assetRuntime,
            assetId = asset.assetId,
            callTracker = prepared.callTracker,
        )
    }

    fun waitForSessionAsset(
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): ByteArray? {
        if (isPreparedAssetReady(sessionAssetStore, prepared, asset.assetId)) {
            return loadSessionAsset(prepared, asset)
        }
        val runtime = prepared.assetRuntime.getOrPut(asset.assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            if (runtime.state == SessionAssetState.FAILED) {
                return null
            }
        }
        return runCatching {
            loadSessionAsset(prepared, asset)
        }.getOrElse {
            val updatedRuntime = prepared.assetRuntime[asset.assetId]
            if (updatedRuntime?.state == SessionAssetState.FAILED) {
                null
            } else {
                throw it
            }
        }
    }

    private fun preparedLoadAssetById(
        sessionId: String,
        assetsById: Map<String, SessionAsset>,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
        callTracker: SessionCallTracker,
    ): ByteArray {
        val asset = assetsById.getValue(assetId)
        val runtime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.resolveAsset(sessionId, assetId)
            if (existing.exists()) {
                runtime.state = SessionAssetState.READY
                runtime.localFile = existing
                runtime.localSizeBytes = existing.length()
                runtime.lastSource = "session-local"
                return existing.readBytes()
            }
            if (runtime.state == SessionAssetState.DOWNLOADING) {
                return waitForPreparedAssetInFlight(sessionId, assetRuntime, assetId, runtime)
                    ?: throw IllegalStateException("asset load did not complete: $assetId")
            }
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
        }
        var lastError: Throwable? = null
        repeat(SESSION_ASSET_MAX_RETRY_COUNT) { attempt ->
            if (callTracker.isCancelled()) {
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.NOT_STARTED
                    runtime.lastError = "cancelled"
                    runtime.lock.notifyAll()
                }
                throw SessionCancelledException("session cancelled while loading asset: $assetId")
            }
            runCatching {
                val startedAt = System.nanoTime()
                synchronized(runtime.lock) {
                    runtime.retryCount += 1
                    runtime.state = SessionAssetState.DOWNLOADING
                    runtime.lastError = null
                }
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = asset.assetId,
                    kind = asset.kind.name,
                    trackId = asset.trackId,
                    source = "session-local",
                )
                val fetchResult = upstreamClient.fetchSegmentBytesMeasured(asset.url, callTracker)
                val writeStartedAt = System.nanoTime()
                val file = sessionAssetStore.writeAsset(sessionId, assetId, fetchResult.bytes)
                val diskWriteMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - writeStartedAt)
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = file
                    runtime.localSizeBytes = fetchResult.bytes.size.toLong()
                    runtime.lastElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.lastSource = fetchResult.source
                    runtime.upstreamFirstByteMs = fetchResult.firstByteMs
                    runtime.upstreamCompleteMs = fetchResult.completeMs
                    runtime.diskWriteMs = diskWriteMs.coerceAtLeast(0L)
                    runtime.lastError = null
                    runtime.lock.notifyAll()
                }
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = null,
                    kind = null,
                    trackId = null,
                    source = null,
                )
                refreshDiagnosticsSnapshot()
                return fetchResult.bytes
            }.onFailure { error ->
                lastError = error
                synchronized(runtime.lock) {
                    runtime.lastElapsedMs = null
                    runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
                    runtime.lastSource = "session-local"
                    if (callTracker.isCancelled()) {
                        runtime.state = SessionAssetState.NOT_STARTED
                    } else if (attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                        runtime.state = SessionAssetState.FAILED
                    }
                    runtime.lock.notifyAll()
                }
                if (callTracker.isCancelled() || attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                    diagnosticsState.updateCurrentLoadingAsset(
                        assetId = null,
                        kind = null,
                        trackId = null,
                        source = null,
                    )
                }
                refreshDiagnosticsSnapshot()
                if (callTracker.isCancelled()) {
                    throw SessionCancelledException("session cancelled while loading asset: $assetId")
                }
                if (attempt < SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                    Thread.sleep(SESSION_ASSET_RETRY_DELAY_MS)
                }
            }
        }
        throw lastError ?: IllegalStateException("asset load failed without exception: $assetId")
    }

    private fun waitForPreparedAssetInFlight(
        sessionId: String,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
        runtime: SessionAssetRuntime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() },
    ): ByteArray? {
        while (true) {
            val state = synchronized(runtime.lock) {
                val existing = sessionAssetStore.resolveAsset(sessionId, assetId)
                if (existing.exists()) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = existing
                    runtime.localSizeBytes = existing.length()
                    return existing.readBytes()
                }
                val currentState = runtime.state
                if (currentState == SessionAssetState.FAILED || currentState == SessionAssetState.NOT_STARTED) {
                    return null
                }
                runCatching { runtime.lock.wait(50L) }
                currentState
            }
            if (state == SessionAssetState.FAILED || state == SessionAssetState.NOT_STARTED) {
                return null
            }
        }
    }

    private companion object {
        const val SESSION_ASSET_MAX_RETRY_COUNT = 3
        const val SESSION_ASSET_RETRY_DELAY_MS = 100L
    }
}
