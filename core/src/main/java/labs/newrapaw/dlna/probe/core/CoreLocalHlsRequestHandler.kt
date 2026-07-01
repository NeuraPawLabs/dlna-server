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
    diagnosticsState: PlaybackDiagnosticsState,
    sessionAssetLoader: CoreLocalHlsSessionAssetLoader,
    sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer,
    sessionPreparer: CoreLocalHlsSessionPreparer,
    getActiveSessionShell: () -> PlaybackSession?,
    isClosedSessionId: (String) -> Boolean,
    getActivePreparedSession: () -> PreparedSessionPlayback?,
    setActivePreparedSession: (PreparedSessionPlayback?) -> Unit,
    updatePlaybackPosition: (Long?) -> Unit,
    refreshPreparedSessionDiagnostics: () -> Unit,
    private val shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
    private val safeLog: (String) -> Unit,
) {
    private val sessionAssetRouteHandler = CoreLocalHlsSessionAssetRouteHandler(
        diagnosticsState = diagnosticsState,
        sessionAssetLoader = sessionAssetLoader,
        sessionAssetStreamer = sessionAssetStreamer,
        updatePlaybackPosition = updatePlaybackPosition,
        refreshPreparedSessionDiagnostics = refreshPreparedSessionDiagnostics,
    )
    private val sessionRouteHandler = CoreLocalHlsSessionRouteHandler(
        sessionLocalServer = sessionLocalServer,
        sessionAssetRouteHandler = sessionAssetRouteHandler,
        sessionPreparer = sessionPreparer,
        getActiveSessionShell = getActiveSessionShell,
        isClosedSessionId = isClosedSessionId,
        getActivePreparedSession = getActivePreparedSession,
        setActivePreparedSession = setActivePreparedSession,
    )

    fun handleSessionRequest(method: String, path: String, headers: Map<String, String> = emptyMap(), output: OutputStream): Boolean {
        if ((method != "GET" && method != "HEAD") || !path.startsWith("/session/")) {
            return false
        }

        sessionRouteHandler.handle(method, path, headers, output)
        return true
    }

    fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = DEFAULT_SOCKET_TIMEOUT_MS
            val output = it.getOutputStream()
            runCatching {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val requestLine = reader.readLine() ?: throw MalformedRequestException("request line missing")
                if (requestLine.isBlank()) {
                    throw MalformedRequestException("request line missing")
                }
                val parsedRequestLine = parseRequestLine(requestLine)
                val method = parsedRequestLine.method
                val path = parsedRequestLine.path
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: throw MalformedRequestException("request headers truncated")
                    if (line.isEmpty()) break
                    if (!line.contains(":")) {
                        throw MalformedRequestException("malformed header line")
                    }
                    val name = line.substringBefore(":", missingDelimiterValue = "").trim()
                    val value = line.substringAfter(":", missingDelimiterValue = "").trim()
                    if (name.isEmpty()) {
                        throw MalformedRequestException("malformed header line")
                    }
                    val normalizedName = name.lowercase()
                    if (normalizedName == "content-length" && headers.containsKey(normalizedName)) {
                        throw MalformedRequestException("duplicate Content-Length")
                    }
                    headers[normalizedName] = value
                }
                parseContentLength(headers["content-length"])
                when {
                    handleSessionRequest(method, path, headers, output) -> Unit
                    else -> writeText(output, 404, "text/plain", "Not Found")
                }
            }.onFailure { error ->
                if (shouldSuppressRequestFailureLog(error)) {
                    return@onFailure
                }
                val message = "${error::class.java.simpleName}: ${error.message}"
                val statusCode = if (error is MalformedRequestException) 400 else 500
                val statusText = if (statusCode == 400) "Bad Request" else "Internal Server Error"
                safeLog("Request failed: $message")
                runCatching { writeText(output, statusCode, "text/plain", "$statusText: $message") }
            }
        }
    }

    companion object {
        internal const val DEFAULT_SOCKET_TIMEOUT_MS = 15_000
    }
}
