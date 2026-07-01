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
    private val assetWaitTimeoutMs: Long = DEFAULT_ASSET_WAIT_TIMEOUT_MS,
) {
    private val runtimeCoordinator = CoreLocalHlsSessionAssetRuntimeCoordinator(
        sessionAssetStore = sessionAssetStore,
        assetWaitTimeoutMs = assetWaitTimeoutMs,
    )

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
            if (runtime.state == SessionAssetState.DOWNLOADING) {
                return when (val acquire = runtimeCoordinator.acquire(
                    sessionId = prepared.session.sessionId,
                    assetRuntime = prepared.assetRuntime,
                    assetId = asset.assetId,
                )) {
                    is SessionAssetAcquireResult.Waited -> acquire.bytes
                    is SessionAssetAcquireResult.Stored -> acquire.bytes
                    is SessionAssetAcquireResult.Download -> null
                }
            }
        }
        return runCatching {
            loadSessionAsset(prepared, asset)
        }.getOrElse {
            val updatedRuntime = prepared.assetRuntime[asset.assetId]
            if (updatedRuntime?.state == SessionAssetState.FAILED || updatedRuntime?.state == SessionAssetState.NOT_STARTED) {
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
        val runtime = when (val acquire = runtimeCoordinator.acquire(sessionId, assetRuntime, assetId)) {
            is SessionAssetAcquireResult.Stored -> return acquire.bytes
            is SessionAssetAcquireResult.Waited ->
                return acquire.bytes ?: throw IllegalStateException("asset load did not complete: $assetId")
            is SessionAssetAcquireResult.Download -> acquire.runtime
        }
        var lastError: Throwable? = null
        repeat(SESSION_ASSET_MAX_RETRY_COUNT) { attempt ->
            if (callTracker.isCancelled()) {
                runtimeCoordinator.markCancelled(runtime)
                throw SessionCancelledException("session cancelled while loading asset: $assetId")
            }
            runCatching {
                val startedAt = System.nanoTime()
                runtimeCoordinator.markAttemptStarting(runtime)
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
                runtimeCoordinator.markReady(
                    runtime = runtime,
                    file = file,
                    bytes = fetchResult.bytes,
                    elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    source = fetchResult.source,
                    upstreamFirstByteMs = fetchResult.firstByteMs,
                    upstreamCompleteMs = fetchResult.completeMs,
                    diskWriteMs = diskWriteMs,
                )
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
                runtimeCoordinator.markFailure(
                    runtime = runtime,
                    error = error,
                    cancelled = callTracker.isCancelled(),
                    lastAttempt = attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1,
                )
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
                    try {
                        Thread.sleep(SESSION_ASSET_RETRY_DELAY_MS)
                    } catch (error: InterruptedException) {
                        lastError = error
                        runtimeCoordinator.markInterruptedDuringBackoff(runtime, error)
                        diagnosticsState.updateCurrentLoadingAsset(
                            assetId = null,
                            kind = null,
                            trackId = null,
                            source = null,
                        )
                        refreshDiagnosticsSnapshot()
                        Thread.currentThread().interrupt()
                        throw error
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("asset load failed without exception: $assetId")
    }

    private companion object {
        const val SESSION_ASSET_MAX_RETRY_COUNT = 3
        const val SESSION_ASSET_RETRY_DELAY_MS = 100L
        const val DEFAULT_ASSET_WAIT_TIMEOUT_MS = 15_000L
    }
}
