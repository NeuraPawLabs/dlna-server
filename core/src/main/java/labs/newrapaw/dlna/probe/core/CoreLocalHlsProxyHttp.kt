package labs.newrapaw.dlna.probe.core

import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal fun writeText(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
) {
    writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8), method, headers)
}

internal fun writeBytes(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
) {
    writeBytesMeasured(output, status, contentType, body, method, headers)
}

internal fun writeBytesMeasured(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
): ResponseWriteTiming {
    val startedAt = System.nanoTime()
    val reason = reasonPhrase(status)
    output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
    output.write("Content-Type: $contentType\r\n".toByteArray())
    output.write("Content-Length: ${body.size}\r\n".toByteArray())
    headers.forEach { (name, value) ->
        output.write("$name: $value\r\n".toByteArray())
    }
    output.write("Connection: close\r\n\r\n".toByteArray())
    val firstByteAt = System.nanoTime()
    if (!method.equals("HEAD", ignoreCase = true)) {
        output.write(body)
    }
    output.flush()
    return ResponseWriteTiming(
        firstByteMs = TimeUnit.NANOSECONDS.toMillis(firstByteAt - startedAt),
        completeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
    )
}

internal fun writeResponseHeaders(
    output: OutputStream,
    status: Int,
    contentType: String,
    contentLength: Long?,
    headers: Map<String, String> = emptyMap(),
) {
    val reason = reasonPhrase(status)
    output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
    output.write("Content-Type: $contentType\r\n".toByteArray())
    if (contentLength != null) {
        output.write("Content-Length: $contentLength\r\n".toByteArray())
    }
    headers.forEach { (name, value) ->
        output.write("$name: $value\r\n".toByteArray())
    }
    output.write("Connection: close\r\n\r\n".toByteArray())
    output.flush()
}

private fun reasonPhrase(status: Int): String =
    when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        416 -> "Requested Range Not Satisfiable"
        404 -> "Not Found"
        410 -> "Gone"
        422 -> "Unprocessable Entity"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> if (status in 200..299) "OK" else "Error"
    }
