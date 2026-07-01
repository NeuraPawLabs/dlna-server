package labs.newrapaw.dlna.probe.proxy

import java.io.EOFException
import java.io.InputStream
import java.net.Socket
import java.nio.charset.Charset
import labs.newrapaw.dlna.probe.admin.AdminHttpRoutes

internal class LocalHlsProxyRequestHandler(
    private val adminRoutes: AdminHttpRoutes,
    private val dlnaRoutes: LocalHlsProxyDlnaRoutes,
    private val sessionRelay: LocalHlsProxySessionRelay,
    private val shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
    private val safeLog: (String) -> Unit,
) {
    companion object {
        internal const val MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024
    }

    fun handle(socket: Socket) {
        socket.use {
            val output = it.getOutputStream()
            runCatching {
                val input = it.getInputStream()
                val requestLine = readAsciiLine(input) ?: throw MalformedRequestException("request line missing")
                if (!requestLine.terminated || requestLine.line.isBlank()) {
                    throw MalformedRequestException("request line truncated")
                }
                val parsedRequestLine = parseRequestLine(requestLine.line)
                val method = parsedRequestLine.method
                val path = parsedRequestLine.path
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val headerLine = readAsciiLine(input) ?: throw MalformedRequestException("request headers truncated")
                    if (!headerLine.terminated) {
                        throw MalformedRequestException("request headers truncated")
                    }
                    val line = headerLine.line
                    if (line.isEmpty()) break
                    if (!line.contains(":")) {
                        throw MalformedRequestException("malformed header line")
                    }
                    val name = line.substringBefore(":", "").trim().lowercase()
                    val value = line.substringAfter(":", "").trim()
                    if (name.isEmpty()) {
                        throw MalformedRequestException("malformed header line")
                    }
                    if (name == "content-length" && headers.containsKey(name)) {
                        throw MalformedRequestException("duplicate Content-Length")
                    }
                    headers[name] = value
                }
                val body = readBody(
                    input = input,
                    length = parseContentLength(headers["content-length"]),
                    charset = parseBodyCharset(headers["content-type"]),
                )

                when {
                    adminRoutes.handle(method, path, body, output) -> Unit
                    dlnaRoutes.handle(method, path, headers, body, output) -> Unit
                    sessionRelay.handle(method, path, headers, output) -> Unit
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

    private fun readBody(input: InputStream, length: Int, charset: Charset): String {
        if (length <= 0) return ""

        val bytes = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(bytes, totalRead, length - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        if (totalRead < length) {
            throw MalformedRequestException("request body truncated (${totalRead}/${length})", EOFException())
        }
        return String(bytes, 0, totalRead, charset)
    }
}

private data class ParsedRequestLine(
    val method: String,
    val path: String,
)

private data class AsciiLineReadResult(
    val line: String,
    val terminated: Boolean,
)

private fun readAsciiLine(input: InputStream): AsciiLineReadResult? {
    val bytes = ArrayList<Byte>(128)
    while (true) {
        val next = input.read()
        if (next < 0) {
            return if (bytes.isEmpty()) {
                null
            } else {
                AsciiLineReadResult(
                    line = bytes.toByteArray().toString(Charsets.ISO_8859_1).removeSuffix("\r"),
                    terminated = false,
                )
            }
        }
        if (next == '\n'.code) {
            break
        }
        bytes += next.toByte()
    }
    val raw = bytes.toByteArray().toString(Charsets.ISO_8859_1)
    return AsciiLineReadResult(
        line = raw.removeSuffix("\r"),
        terminated = true,
    )
}

private fun parseRequestLine(line: String): ParsedRequestLine {
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.size != 3) {
        throw MalformedRequestException("malformed request line")
    }
    val method = parts[0]
    val path = parts[1]
    val version = parts[2]
    if (method.isBlank() || path.isBlank() || !version.startsWith("HTTP/")) {
        throw MalformedRequestException("malformed request line")
    }
    return ParsedRequestLine(method = method, path = path)
}

private fun parseContentLength(headerValue: String?): Int {
    if (headerValue == null) return 0
    val value = headerValue.trim()
    val parsed = value.toIntOrNull() ?: throw MalformedRequestException("invalid Content-Length")
    if (parsed < 0) {
        throw MalformedRequestException("invalid Content-Length")
    }
    if (parsed > LocalHlsProxyRequestHandler.MAX_REQUEST_BODY_BYTES) {
        throw MalformedRequestException("request body too large")
    }
    return parsed
}

private fun parseBodyCharset(contentType: String?): Charset =
    contentType
        ?.substringAfter("charset=", missingDelimiterValue = "")
        ?.substringBefore(";")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { charsetName -> runCatching { Charset.forName(charsetName) }.getOrNull() }
        ?: Charsets.UTF_8

private class MalformedRequestException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
