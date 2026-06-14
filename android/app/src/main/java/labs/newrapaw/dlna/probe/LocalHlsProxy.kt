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
    private val onPlayRequested: (String) -> Unit = {},
    private val onStopRequested: () -> Unit = {},
    private val onUpdateRequested: (String) -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun publicBaseUrl(hostAddress: String): String = "http://$hostAddress:$port"

    fun start() {
        if (running.get()) return
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
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
                method == "GET" && path == "/" -> handleControlPage(it.getOutputStream())
                method == "POST" && path.startsWith("/control/play") -> handlePlayRequest(body, it.getOutputStream())
                method == "POST" && path.startsWith("/control/stop") -> handleStopRequest(it.getOutputStream())
                method == "POST" && path.startsWith("/control/update") -> handleUpdateRequest(body, it.getOutputStream())
                path.startsWith("/proxy/hls.m3u8") -> handleManifest(path, it.getOutputStream())
                path.startsWith("/proxy/segment.ts") -> handleSegment(path, it.getOutputStream())
                else -> writeText(it.getOutputStream(), 404, "text/plain", "Not Found")
            }
        }
    }

    private fun handleControlPage(output: OutputStream) {
        writeText(
            output,
            200,
            "text/html",
            buildControlPage(
                deviceName = "NewraPaw DLNA Probe",
                status = "Ready",
                localPlaybackUrl = baseUrl,
            ),
        )
    }

    private fun handlePlayRequest(body: String, output: OutputStream) {
        val url = decodeFormUrl(body)
        if (url == null) {
            writeText(output, 400, "text/html", "Missing URL")
            return
        }

        log("Remote play request: $url")
        onPlayRequested(url)
        writeText(output, 200, "text/html", "Play request sent. You can return to the TV.")
    }

    private fun handleStopRequest(output: OutputStream) {
        log("Remote stop request")
        onStopRequested()
        writeText(output, 200, "text/html", "Stop request sent. You can return to the TV.")
    }

    private fun handleUpdateRequest(body: String, output: OutputStream) {
        val apkUrl = decodeFormValue(body, "apkUrl")
        if (apkUrl == null) {
            writeText(output, 400, "text/html", "Missing APK URL")
            return
        }

        log("Remote update request: $apkUrl")
        onUpdateRequested(apkUrl)
        writeText(output, 200, "text/html", "Update request sent. Confirm installation on the TV.")
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

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""

        val chars = CharArray(length)
        val read = reader.read(chars, 0, length)
        return if (read > 0) String(chars, 0, read) else ""
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
