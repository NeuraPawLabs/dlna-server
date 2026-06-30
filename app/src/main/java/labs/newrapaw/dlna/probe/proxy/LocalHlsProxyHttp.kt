package labs.newrapaw.dlna.probe.proxy

import java.io.OutputStream
import labs.newrapaw.dlna.probe.core.buildPlaybackDiagnosticsJson
import labs.newrapaw.dlna.probe.dlna.DlnaHttpResponse

internal fun writeText(output: OutputStream, status: Int, contentType: String, body: String) {
    writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8))
}

internal fun writeJson(output: OutputStream, status: Int, ok: Boolean, message: String) {
    val body = """{"ok":$ok,"message":"${escapeJson(message)}"}"""
    writeText(output, status, "application/json", body)
}

internal fun writeResponse(output: OutputStream, response: DlnaHttpResponse) {
    writeBytes(
        output = output,
        status = response.statusCode,
        contentType = response.contentType,
        body = response.body.toByteArray(Charsets.UTF_8),
        extraHeaders = response.headers,
    )
}

private fun escapeJson(value: String): String =
    buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

private fun writeBytes(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    extraHeaders: Map<String, String> = emptyMap(),
) {
    writeResponseHeaders(
        output = output,
        status = status,
        contentType = contentType,
        contentLength = body.size.toLong(),
        extraHeaders = extraHeaders,
    )
    output.write(body)
    output.flush()
}

private fun writeResponseHeaders(
    output: OutputStream,
    status: Int,
    contentType: String,
    contentLength: Long?,
    extraHeaders: Map<String, String> = emptyMap(),
) {
    val reason = if (status in 200..299) "OK" else "Error"
    output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
    output.write("Content-Type: $contentType\r\n".toByteArray())
    if (contentLength != null) {
        output.write("Content-Length: $contentLength\r\n".toByteArray())
    }
    extraHeaders.forEach { (name, value) ->
        output.write("$name: $value\r\n".toByteArray())
    }
    output.write("Connection: close\r\n\r\n".toByteArray())
    output.flush()
}
