package labs.newrapaw.dlna.probe.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsRequestHandler(
    private val sessionLocalServer: SessionLocalServer,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val sessionAssetLoader: CoreLocalHlsSessionAssetLoader,
    private val sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer,
    private val sessionPreparer: CoreLocalHlsSessionPreparer,
    private val getActiveSessionShell: () -> PlaybackSession?,
    private val getActivePreparedSession: () -> PreparedSessionPlayback?,
    private val setActivePreparedSession: (PreparedSessionPlayback?) -> Unit,
    private val updatePlaybackPosition: (Long?) -> Unit,
    private val refreshPreparedSessionDiagnostics: () -> Unit,
    private val shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
    private val safeLog: (String) -> Unit,
) {
    fun handleSessionRequest(method: String, path: String, output: OutputStream): Boolean {
        if ((method != "GET" && method != "HEAD") || !path.startsWith("/session/")) {
            return false
        }

        handleSessionRoute(method, path, output)
        return true
    }

    fun handle(socket: Socket) {
        socket.use {
            val output = it.getOutputStream()
            runCatching {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                val method = requestLine.split(" ").getOrNull(0).orEmpty()
                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine().orEmpty()
                    if (line.isEmpty()) break
                    val name = line.substringBefore(":", missingDelimiterValue = "").trim()
                    val value = line.substringAfter(":", missingDelimiterValue = "").trim()
                    if (name.isNotEmpty()) {
                        headers[name.lowercase()] = value
                    }
                }
                when {
                    handleSessionRequest(method, path, output) -> Unit
                    else -> writeText(output, 404, "text/plain", "Not Found")
                }
            }.onFailure { error ->
                if (shouldSuppressRequestFailureLog(error)) {
                    return@onFailure
                }
                val message = "${error::class.java.simpleName}: ${error.message}"
                safeLog("Request failed: $message")
                runCatching { writeText(output, 500, "text/plain", "Internal Server Error: $message") }
            }
        }
    }

    private fun handleSessionRoute(method: String, path: String, output: OutputStream) {
        val session = getActiveSessionShell()
        if (session == null) {
            writeText(output, 404, "text/plain", "No active session", method)
            return
        }
        val sessionId = path.substringAfter("/session/").substringBefore("/")
        if (sessionId != session.sessionId) {
            writeText(output, 404, "text/plain", "Unknown session", method)
            return
        }
        val prepared = sessionPreparer.ensurePreparedSession(
            session = session,
            getActivePreparedSession = getActivePreparedSession,
            setActivePreparedSession = setActivePreparedSession,
        )
        if (prepared == null) {
            writeText(output, 502, "text/plain", "Failed to prepare session", method)
            return
        }
        if (prepared.preparationFailure != null) {
            writeText(output, prepared.preparationFailure.statusCode, "text/plain", prepared.preparationFailure.message, method)
            return
        }
        when {
            path == sessionLocalServer.masterManifestPath(sessionId) ->
                writeText(output, 200, "application/vnd.apple.mpegurl", prepared.masterManifest, method)
            path == sessionLocalServer.videoPlaylistPath(sessionId) ->
                writeText(output, 200, "application/vnd.apple.mpegurl", prepared.videoPlaylist, method)
            path.startsWith("/session/$sessionId/audio/") -> {
                val trackId = path.substringAfterLast("/").substringBeforeLast(".m3u8")
                val playlist = prepared.audioPlaylists[trackId]
                if (playlist == null) writeText(output, 404, "text/plain", "Unknown audio track", method)
                else writeText(output, 200, "application/vnd.apple.mpegurl", playlist, method)
            }
            path.startsWith("/session/$sessionId/subtitle/") -> {
                val trackId = path.substringAfterLast("/").substringBeforeLast(".m3u8")
                val playlist = prepared.subtitlePlaylists[trackId]
                if (playlist == null) writeText(output, 404, "text/plain", "Unknown subtitle track", method)
                else writeText(output, 200, "application/vnd.apple.mpegurl", playlist, method)
            }
            path.startsWith("/session/$sessionId/asset/") -> {
                val assetId = path.substringAfter("/asset/").substringBeforeLast(".")
                val asset = prepared.assetsById[assetId]
                if (asset == null) {
                    writeText(output, 404, "text/plain", "Unknown asset", method)
                    return
                }
                val slotIndex = findSlotIndexForAsset(prepared.session.timeline.slots, asset.assetId)
                slotIndex?.let { requestedSlotIndex ->
                    noteRequestedPlaybackSlot(
                        prepared = prepared,
                        slotIndex = requestedSlotIndex,
                        updatePlaybackPosition = updatePlaybackPosition,
                        refreshPreparedSessionDiagnostics = refreshPreparedSessionDiagnostics,
                    )
                }
                if (method.equals("GET", ignoreCase = true) && sessionAssetStreamer.tryStreamSessionAsset(output, prepared, asset)) {
                    return
                }
                val bytes = sessionAssetLoader.waitForSessionAsset(prepared, asset)
                if (bytes == null) {
                    val runtime = prepared.assetRuntime[asset.assetId]
                    if (runtime?.state == SessionAssetState.FAILED) {
                        diagnosticsState.setLastError("Session asset failed: ${asset.assetId}")
                        writeText(output, 502, "text/plain", "Session asset failed: ${asset.assetId}", method)
                    } else {
                        diagnosticsState.setLastError("Session asset wait timed out: ${asset.assetId}")
                        writeText(output, 504, "text/plain", "Session asset wait timed out: ${asset.assetId}", method)
                    }
                    return
                }
                refreshPreparedSessionDiagnostics()
                writeBytesMeasured(output, 200, guessSegmentContentType(asset.url), bytes, method)
            }
            else -> writeText(output, 404, "text/plain", "Unknown session route", method)
        }
    }
}
