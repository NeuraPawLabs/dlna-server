package labs.newrapaw.dlna.probe.core

import java.io.OutputStream
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore

internal class CoreLocalHlsSessionAssetStreamer(
    private val proxySettingsStore: ProxySettingsStore,
    private val sessionAssetStore: SessionAssetStore,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val upstreamClient: CoreLocalHlsUpstreamClient,
    private val refreshDiagnosticsSnapshot: () -> Unit,
) {
    fun tryStreamSessionAsset(
        output: OutputStream,
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): Boolean {
        if (isWrappedTransportStream(asset.url)) return false
        val settings = proxySettingsStore.load()
        if (settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY) return false
        val runtime = prepared.assetRuntime.getOrPut(asset.assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.resolveAsset(prepared.session.sessionId, asset.assetId)
            if (existing.exists() || runtime.state == SessionAssetState.DOWNLOADING) {
                return false
            }
            runtime.retryCount += 1
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
        }
        val (source, call) = upstreamClient.openStreamingCall(asset.url)
        prepared.callTracker.register(call)
        var responseStarted = false
        val startedAt = System.nanoTime()
        return try {
            diagnosticsState.updateCurrentLoadingAsset(
                assetId = asset.assetId,
                kind = asset.kind.name,
                trackId = asset.trackId,
                source = source,
            )
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val failure = formatUpstreamFailure(
                        statusCode = response.code,
                        statusMessage = response.message,
                        body = response.body?.string().orEmpty(),
                    )
                    throw UpstreamFetchException(response.code, failure)
                }
                val body = response.body ?: throw UpstreamFetchException(502, "empty upstream body")
                val stream = body.byteStream()
                writeResponseHeaders(
                    output = output,
                    status = 200,
                    contentType = guessSegmentContentType(asset.url),
                    contentLength = body.contentLength().takeIf { it >= 0L },
                )
                responseStarted = true
                val buffer = ByteArray(8 * 1024)
                val capture = java.io.ByteArrayOutputStream()
                var firstByteAt: Long? = null
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (firstByteAt == null) firstByteAt = System.nanoTime()
                    output.write(buffer, 0, count)
                    output.flush()
                    capture.write(buffer, 0, count)
                }
                val bytes = if (looksLikeTransportStream(asset.url)) {
                    stripPngWrapperFromSegment(capture.toByteArray())
                } else {
                    capture.toByteArray()
                }
                val file = sessionAssetStore.writeAsset(prepared.session.sessionId, asset.assetId, bytes)
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = file
                    runtime.localSizeBytes = bytes.size.toLong()
                    runtime.lastElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.lastSource = source
                    runtime.upstreamFirstByteMs = firstByteAt?.let { TimeUnit.NANOSECONDS.toMillis(it - startedAt) } ?: 0L
                    runtime.upstreamCompleteMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.diskWriteMs = 0L
                    runtime.lastError = null
                    runtime.lock.notifyAll()
                }
                diagnosticsState.onSegmentResult(asset.url, source, runtime.upstreamCompleteMs ?: 0L, success = true)
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = null,
                    kind = null,
                    trackId = null,
                    source = null,
                )
                refreshDiagnosticsSnapshot()
                true
            }
        } catch (error: Throwable) {
            synchronized(runtime.lock) {
                runtime.lastElapsedMs = null
                runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
                runtime.lastSource = source
                runtime.state = if (prepared.callTracker.isCancelled()) SessionAssetState.NOT_STARTED else SessionAssetState.FAILED
                runtime.lock.notifyAll()
            }
            diagnosticsState.onSegmentResult(
                url = asset.url,
                source = source,
                elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                success = false,
                fallbackReason = "${error::class.java.simpleName}: ${error.message}",
            )
            diagnosticsState.updateCurrentLoadingAsset(
                assetId = null,
                kind = null,
                trackId = null,
                source = null,
            )
            refreshDiagnosticsSnapshot()
            if (!responseStarted) {
                if (error is UpstreamFetchException) {
                    writeText(output, error.statusCode, "text/plain", error.message.orEmpty())
                } else {
                    writeText(output, 502, "text/plain", "${error::class.java.simpleName}: ${error.message}")
                }
            }
            true
        } finally {
            prepared.callTracker.complete(call)
        }
    }
}
