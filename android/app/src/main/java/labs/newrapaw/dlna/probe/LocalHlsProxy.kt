package labs.newrapaw.dlna.probe

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun start() {
        if (running.get()) return
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        running.set(true)
        executor.execute {
            log("Proxy listening at $baseUrl")
            while (running.get()) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute { handle(socket) }
            }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            while (reader.readLine().orEmpty().isNotEmpty()) {
                // Consume headers.
            }

            when {
                path.startsWith("/proxy/hls.m3u8") -> handleManifest(path, it.getOutputStream())
                path.startsWith("/proxy/segment.ts") -> handleSegment(path, it.getOutputStream())
                else -> writeText(it.getOutputStream(), 404, "text/plain", "Not Found")
            }
        }
    }

    private fun handleManifest(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        val response = client.newCall(Request.Builder().url(upstreamUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                writeText(output, it.code, "text/plain", "Upstream manifest failed: ${it.code}")
                return
            }
            val manifest = it.body?.string().orEmpty()
            writeText(output, 200, "application/vnd.apple.mpegurl", rewriteHlsManifest(manifest, upstreamUrl, baseUrl))
        }
    }

    private fun handleSegment(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        val response = client.newCall(Request.Builder().url(upstreamUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                writeText(output, it.code, "text/plain", "Upstream segment failed: ${it.code}")
                return
            }
            val bytes = it.body?.bytes() ?: ByteArray(0)
            writeBytes(output, 200, "video/mp2t", stripPngWrapperFromSegment(bytes))
        }
    }

    private fun extractUrl(path: String): String? {
        val query = path.substringAfter("?", "")
        val params = query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
        return params["u"]?.let(::decodeProxyUrl) ?: params["url"]
    }

    private fun writeText(output: OutputStream, status: Int, contentType: String, body: String) {
        writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(output: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val reason = if (status in 200..299) "OK" else "Error"
        output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        output.write("Content-Length: ${body.size}\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }
}
