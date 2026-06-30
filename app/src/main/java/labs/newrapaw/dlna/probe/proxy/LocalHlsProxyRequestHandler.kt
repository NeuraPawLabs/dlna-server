package labs.newrapaw.dlna.probe.proxy

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import labs.newrapaw.dlna.probe.admin.AdminHttpRoutes

internal class LocalHlsProxyRequestHandler(
    private val adminRoutes: AdminHttpRoutes,
    private val dlnaRoutes: LocalHlsProxyDlnaRoutes,
    private val sessionRelay: LocalHlsProxySessionRelay,
    private val shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
    private val safeLog: (String) -> Unit,
) {
    fun handle(socket: Socket) {
        socket.use {
            val output = it.getOutputStream()
            runCatching {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val requestLine = reader.readLine().orEmpty()
                val method = requestLine.split(" ").getOrNull(0).orEmpty()
                val path = requestLine.split(" ").getOrNull(1).orEmpty()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine().orEmpty()
                    if (line.isEmpty()) break
                    val name = line.substringBefore(":", "").trim().lowercase()
                    val value = line.substringAfter(":", "").trim()
                    if (name.isNotEmpty()) headers[name] = value
                }
                val body = readBody(reader, headers["content-length"]?.toIntOrNull() ?: 0)

                when {
                    adminRoutes.handle(method, path, body, output) -> Unit
                    dlnaRoutes.handle(method, path, headers, body, output) -> Unit
                    sessionRelay.handle(method, path, output) -> Unit
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

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""

        val chars = CharArray(length)
        val read = reader.read(chars, 0, length)
        return if (read > 0) String(chars, 0, read) else ""
    }
}
