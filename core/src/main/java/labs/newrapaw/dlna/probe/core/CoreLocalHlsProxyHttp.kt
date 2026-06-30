package labs.newrapaw.dlna.probe.core

import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal fun writeText(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: String,
    method: String = "GET",
) {
    writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8), method)
}

internal fun writeBytes(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    method: String = "GET",
) {
    writeBytesMeasured(output, status, contentType, body, method)
}

internal fun writeBytesMeasured(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    method: String = "GET",
): ResponseWriteTiming {
    val startedAt = System.nanoTime()
    val reason = if (status in 200..299) "OK" else "Error"
    output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
    output.write("Content-Type: $contentType\r\n".toByteArray())
    output.write("Content-Length: ${body.size}\r\n".toByteArray())
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
) {
    val reason = if (status in 200..299) "OK" else "Error"
    output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
    output.write("Content-Type: $contentType\r\n".toByteArray())
    if (contentLength != null) {
        output.write("Content-Length: $contentLength\r\n".toByteArray())
    }
    output.write("Connection: close\r\n\r\n".toByteArray())
    output.flush()
}
