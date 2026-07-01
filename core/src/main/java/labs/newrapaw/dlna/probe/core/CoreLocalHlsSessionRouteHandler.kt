package labs.newrapaw.dlna.probe.core

import java.io.OutputStream
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsSessionRouteHandler(
    private val sessionLocalServer: SessionLocalServer,
    private val sessionAssetRouteHandler: CoreLocalHlsSessionAssetRouteHandler,
    private val sessionPreparer: CoreLocalHlsSessionPreparer,
    private val getActiveSessionShell: () -> PlaybackSession?,
    private val isClosedSessionId: (String) -> Boolean,
    private val getActivePreparedSession: () -> PreparedSessionPlayback?,
    private val setActivePreparedSession: (PreparedSessionPlayback?) -> Unit,
) {
    fun handle(
        method: String,
        path: String,
        headers: Map<String, String>,
        output: OutputStream,
    ) {
        val sessionId = path.substringAfter("/session/").substringBefore("/")
        val session = getActiveSessionShell()
        if (session == null) {
            if (isClosedSessionId(sessionId)) {
                writeText(output, 410, "text/plain", "Session Gone", method, headers = CACHE_BYPASS_HEADERS)
            } else {
                writeText(output, 404, "text/plain", "No active session", method)
            }
            return
        }
        if (sessionId != session.sessionId) {
            if (isClosedSessionId(sessionId)) {
                writeText(output, 410, "text/plain", "Session Gone", method, headers = CACHE_BYPASS_HEADERS)
            } else {
                writeText(output, 404, "text/plain", "Unknown session", method)
            }
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
            path.startsWith("/session/$sessionId/video/") -> {
                val trackId = path.substringAfterLast("/").substringBeforeLast(".m3u8")
                val playlist = prepared.videoPlaylists[trackId]
                if (playlist == null) writeText(output, 404, "text/plain", "Unknown video track", method)
                else writeText(output, 200, "application/vnd.apple.mpegurl", playlist, method)
            }
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
            sessionAssetRouteHandler.handle(method, path, headers, output, sessionId, prepared) -> Unit
            else -> writeText(output, 404, "text/plain", "Unknown session route", method)
        }
    }

    private companion object {
        val CACHE_BYPASS_HEADERS = mapOf("Cache-Control" to "no-store")
    }
}
