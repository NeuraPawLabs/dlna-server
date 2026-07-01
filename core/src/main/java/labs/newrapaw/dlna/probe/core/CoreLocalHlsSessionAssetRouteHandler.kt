package labs.newrapaw.dlna.probe.core

import java.io.OutputStream
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetState

internal class CoreLocalHlsSessionAssetRouteHandler(
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val sessionAssetLoader: CoreLocalHlsSessionAssetLoader,
    private val sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer,
    private val updatePlaybackPosition: (Long?) -> Unit,
    private val refreshPreparedSessionDiagnostics: () -> Unit,
) {
    fun handle(
        method: String,
        path: String,
        headers: Map<String, String>,
        output: OutputStream,
        sessionId: String,
        prepared: PreparedSessionPlayback,
    ): Boolean {
        if (!path.startsWith("/session/$sessionId/asset/")) {
            return false
        }
        val assetId = path.substringAfter("/asset/").substringBeforeLast(".")
        val asset = prepared.assetsById[assetId]
        if (asset == null) {
            writeText(output, 404, "text/plain", "Unknown asset", method)
            return true
        }
        val slotIndex = findSlotIndexForAsset(prepared.session.timeline.slots, asset.assetId)
        val rangeRequest = parseByteRange(headers["range"])
        if (rangeRequest is ParsedByteRangeHeader.Absent &&
            method.equals("GET", ignoreCase = true) &&
            sessionAssetStreamer.tryStreamSessionAsset(output, prepared, asset)
        ) {
            slotIndex?.let { requestedSlotIndex ->
                noteRequestedPlaybackSlot(
                    prepared = prepared,
                    slotIndex = requestedSlotIndex,
                    updatePlaybackPosition = updatePlaybackPosition,
                    refreshPreparedSessionDiagnostics = refreshPreparedSessionDiagnostics,
                )
            }
            return true
        }
        slotIndex?.let { requestedSlotIndex ->
            noteRequestedPlaybackSlot(
                prepared = prepared,
                slotIndex = requestedSlotIndex,
                updatePlaybackPosition = updatePlaybackPosition,
                refreshPreparedSessionDiagnostics = refreshPreparedSessionDiagnostics,
            )
        }
        val bytes = sessionAssetLoader.waitForSessionAsset(prepared, asset)
        if (bytes == null) {
            writeAssetLoadFailure(
                output = output,
                method = method,
                prepared = prepared,
                asset = asset,
            )
            return true
        }
        refreshPreparedSessionDiagnostics()
        writeAssetResponse(
            output = output,
            method = method,
            asset = asset,
            bytes = bytes,
            rangeRequest = rangeRequest,
        )
        return true
    }

    private fun writeAssetLoadFailure(
        output: OutputStream,
        method: String,
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ) {
        val runtime = prepared.assetRuntime[asset.assetId]
        if (prepared.callTracker.isCancelled() || runtime?.state == SessionAssetState.NOT_STARTED) {
            diagnosticsState.setLastError("Session gone while loading asset: ${asset.assetId}")
            writeText(output, 410, "text/plain", "Session Gone", method, headers = CACHE_BYPASS_HEADERS)
        } else if (runtime?.state == SessionAssetState.FAILED) {
            diagnosticsState.setLastError("Session asset failed: ${asset.assetId}")
            writeText(output, 502, "text/plain", "Session asset failed: ${asset.assetId}", method)
        } else {
            diagnosticsState.setLastError("Session asset wait timed out: ${asset.assetId}")
            writeText(output, 504, "text/plain", "Session asset wait timed out: ${asset.assetId}", method)
        }
    }

    private fun writeAssetResponse(
        output: OutputStream,
        method: String,
        asset: SessionAsset,
        bytes: ByteArray,
        rangeRequest: ParsedByteRangeHeader,
    ) {
        when {
            rangeRequest is ParsedByteRangeHeader.Absent -> {
                writeBytesMeasured(output, 200, guessSegmentContentType(asset.url), bytes, method)
            }
            rangeRequest is ParsedByteRangeHeader.Unsupported -> {
                writeText(
                    output,
                    416,
                    "text/plain",
                    "Requested Range Not Satisfiable",
                    method,
                    headers = unsatisfiedRangeHeaders(bytes.size),
                )
            }
            else -> {
                val response = (rangeRequest as ParsedByteRangeHeader.Single).range.resolve(bytes.size.toLong())
                if (!response.satisfiable) {
                    writeText(
                        output,
                        416,
                        "text/plain",
                        "Requested Range Not Satisfiable",
                        method,
                        headers = unsatisfiedRangeHeaders(bytes.size),
                    )
                } else {
                    val sliced = bytes.copyOfRange(response.start.toInt(), response.endInclusive.toInt() + 1)
                    writeBytesMeasured(
                        output,
                        206,
                        guessSegmentContentType(asset.url),
                        sliced,
                        method,
                        headers = mapOf(
                            "Accept-Ranges" to "bytes",
                            "Content-Range" to "bytes ${response.start}-${response.endInclusive}/${bytes.size}",
                        ),
                    )
                }
            }
        }
    }

    private fun unsatisfiedRangeHeaders(size: Int): Map<String, String> =
        mapOf(
            "Accept-Ranges" to "bytes",
            "Content-Range" to "bytes */$size",
        )

    private companion object {
        val CACHE_BYPASS_HEADERS = mapOf("Cache-Control" to "no-store")
    }
}
