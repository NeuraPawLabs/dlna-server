package labs.newrapaw.dlna.probe

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class LocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    private val getLogs: () -> List<String> = { emptyList() },
    private val proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    private val segmentCache: HlsSegmentCache? = null,
    private val dlnaConfig: () -> DlnaDeviceConfig? = { null },
    private val onPlayRequested: (String) -> Unit = {},
    private val onStopRequested: () -> Unit = {},
    private val onPauseRequested: () -> Unit = {},
    private val onUpdateRequested: (String) -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val prefetchExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val upstreamRaceExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val renderer = DlnaRendererController(
        log = log,
        onPlayRequested = onPlayRequested,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
    )
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
            safeLog("Proxy listening at $baseUrl")
            while (running.get()) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute { handle(socket) }
            }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        upstreamRaceExecutor.shutdownNow()
        prefetchExecutor.shutdownNow()
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
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
                    method == "GET" && path == "/" -> handleControlPage(output)
                    method == "GET" && path.startsWith("/logs") -> handleLogs(output)
                    method == "GET" && path == "/description.xml" -> handleDeviceDescription(output)
                    method == "GET" && path == "/upnp/service/AVTransport.xml" -> writeText(output, 200, "text/xml", buildAvTransportScpdXml())
                    method == "GET" && path == "/upnp/service/RenderingControl.xml" -> writeText(output, 200, "text/xml", buildRenderingControlScpdXml())
                    method == "GET" && path == "/upnp/service/ConnectionManager.xml" -> writeText(output, 200, "text/xml", buildConnectionManagerScpdXml())
                    method == "POST" && path.startsWith("/upnp/control/") -> handleDlnaControl(path, headers, body, output)
                    method == "SUBSCRIBE" && path.startsWith("/upnp/event/") -> handleEventSubscribe(path, output)
                    method == "UNSUBSCRIBE" && path.startsWith("/upnp/event/") -> handleEventUnsubscribe(path, output)
                    method == "POST" && path.startsWith("/control/play") -> handlePlayRequest(body, output)
                    method == "POST" && path.startsWith("/control/stop") -> handleStopRequest(output)
                    method == "POST" && path.startsWith("/control/update") -> handleUpdateRequest(body, output)
                    method == "POST" && path.startsWith("/control/proxy/add") -> handleProxyAddRequest(body, output)
                    method == "POST" && path.startsWith("/control/proxy/select") -> handleProxySelectRequest(body, output)
                    method == "POST" && path.startsWith("/control/proxy/delete") -> handleProxyDeleteRequest(body, output)
                    method == "POST" && path.startsWith("/control/cache/clear") -> handleCacheClearRequest(output)
                    path.startsWith("/proxy/hls.m3u8") -> handleManifest(path, output)
                    path.startsWith("/proxy/segment.ts") -> handleSegment(path, output)
                    else -> writeText(output, 404, "text/plain", "Not Found")
                }
            }.onFailure { error ->
                val message = "${error::class.java.simpleName}: ${error.message}"
                safeLog("Request failed: $message")
                runCatching { writeText(output, 500, "text/plain", "Internal Server Error: $message") }
            }
        }
    }

    private fun handleControlPage(output: OutputStream) {
        writeText(
            output,
            200,
            "text/html",
            buildControlPage(
                deviceName = "PawCast",
                status = "Ready",
                localPlaybackUrl = baseUrl,
                proxySettings = proxySettingsStore.load(),
                cacheStats = cacheStats(),
                logs = getLogs(),
            ),
        )
    }

    private fun handleLogs(output: OutputStream) {
        writeText(output, 200, "text/plain", getLogs().joinToString("\n"))
    }

    private fun handleDeviceDescription(output: OutputStream) {
        val config = dlnaConfig()
        if (config == null) {
            writeText(output, 503, "text/plain", "DLNA device address is not ready")
            return
        }

        writeText(output, 200, "text/xml", buildDeviceDescriptionXml(config))
    }

    private fun handleDlnaControl(
        path: String,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
    ) {
        val serviceName = path.substringAfterLast("/")
        val response = renderer.handleControlRequest(
            serviceName = serviceName,
            soapActionHeader = headers["soapaction"],
            body = body,
        )
        writeText(output, response.statusCode, response.contentType.substringBefore(";"), response.body)
    }

    private fun handleEventSubscribe(path: String, output: OutputStream) {
        safeLog("[DLNA] Subscribe: ${path.substringAfterLast("/")}")
        writeResponse(output, buildEventSubscribeResponse())
    }

    private fun handleEventUnsubscribe(path: String, output: OutputStream) {
        safeLog("[DLNA] Unsubscribe: ${path.substringAfterLast("/")}")
        writeResponse(output, buildEventUnsubscribeResponse())
    }

    private fun handlePlayRequest(body: String, output: OutputStream) {
        val url = decodeFormUrl(body)
        if (url == null) {
            writeText(output, 400, "text/html", "Missing URL")
            return
        }

        safeLog("Remote play request: $url")
        onPlayRequested(url)
        writeText(output, 200, "text/html", "Play request sent. You can return to the TV.")
    }

    private fun handleStopRequest(output: OutputStream) {
        safeLog("Remote stop request")
        onStopRequested()
        writeText(output, 200, "text/html", "Stop request sent. You can return to the TV.")
    }

    private fun handleUpdateRequest(body: String, output: OutputStream) {
        val apkUrl = decodeFormValue(body, "apkUrl")
        if (apkUrl == null) {
            writeText(output, 400, "text/html", "Missing APK URL")
            return
        }

        safeLog("Remote update request: $apkUrl")
        onUpdateRequested(apkUrl)
        writeText(output, 200, "text/html", "Update request sent. Confirm installation on the TV.")
    }

    private fun handleProxyAddRequest(body: String, output: OutputStream) {
        val proxyUrl = decodeFormValue(body, "proxyUrl")
        val config = proxyUrl?.let(::parseProxyConfig)
        if (config == null) {
            writeText(output, 400, "text/html", "Invalid proxy URL. Use http://host:port, socks5://host:port, or socks5h://host:port.")
            return
        }

        val next = proxySettingsStore.load().add(config).select(config.id)
        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${config.displayUrl()}")
        writeText(output, 200, "text/html", "Proxy saved: ${config.displayUrl()}")
    }

    private fun handleProxySelectRequest(body: String, output: OutputStream) {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null) {
            writeText(output, 400, "text/html", "Missing proxyId")
            return
        }

        val current = proxySettingsStore.load()
        val mode = decodeFormValue(body, "upstreamMode")?.let(::parseUpstreamMode) ?: current.upstreamMode
        val next = current.select(proxyId).withUpstreamMode(mode)
        if (next.selectedProxyId != proxyId) {
            writeText(output, 400, "text/html", "Unknown proxy")
            return
        }

        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${next.selectedProxy()?.displayUrl() ?: "Direct"}")
        writeText(output, 200, "text/html", "Proxy selected")
    }

    private fun parseUpstreamMode(value: String): UpstreamMode =
        runCatching { UpstreamMode.valueOf(value) }.getOrDefault(UpstreamMode.PROXY_ONLY)

    private fun handleProxyDeleteRequest(body: String, output: OutputStream) {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null || proxyId == ProxySettingsState.DIRECT_PROXY_ID) {
            writeText(output, 400, "text/html", "Missing proxyId")
            return
        }

        val next = proxySettingsStore.load().remove(proxyId)
        proxySettingsStore.save(next)
        safeLog("Proxy deleted: $proxyId")
        writeText(output, 200, "text/html", "Proxy deleted")
    }

    private fun handleCacheClearRequest(output: OutputStream) {
        segmentCache?.clear()
        safeLog("HLS cache cleared")
        writeText(output, 200, "text/html", "Cache cleared")
    }

    private fun handleManifest(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        safeLog("Proxy manifest: $upstreamUrl")
        runCatching {
            fetchUpstreamBytes(upstreamUrl).toString(Charsets.UTF_8)
        }.onSuccess { manifest ->
            scheduleSegmentPrefetch(manifest, upstreamUrl)
            writeText(output, 200, "application/vnd.apple.mpegurl", rewriteHlsManifest(manifest, upstreamUrl, baseUrl))
        }.onFailure { error ->
            if (error is UpstreamFetchException) {
                safeLog("Proxy manifest failed: ${error.failure}")
                writeText(output, error.statusCode, "text/plain", "Upstream manifest failed: ${error.failure}")
            } else {
                throw error
            }
        }
    }

    private fun handleSegment(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        runCatching {
            cachedOrFetchSegment(upstreamUrl)
        }.onSuccess { bytes ->
            writeBytes(output, 200, "video/mp2t", bytes)
        }.onFailure { error ->
            if (error is UpstreamFetchException) {
                safeLog("Proxy segment failed: ${error.failure} $upstreamUrl")
                writeText(output, error.statusCode, "text/plain", "Upstream segment failed: ${error.failure}")
            } else {
                throw error
            }
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

    private fun cachedOrFetchSegment(upstreamUrl: String): ByteArray =
        segmentCache?.getOrFetch(upstreamUrl) { fetchSegmentBytes(upstreamUrl) }
            ?: fetchSegmentBytes(upstreamUrl)

    private fun fetchSegmentBytes(upstreamUrl: String): ByteArray {
        val bytes = fetchUpstreamBytes(upstreamUrl)
        return stripPngWrapperFromSegment(bytes)
    }

    private fun fetchUpstreamBytes(upstreamUrl: String): ByteArray {
        val settings = proxySettingsStore.load()
        val proxy = settings.selectedProxy()
        return when {
            proxy == null -> executeUpstreamCall(upstreamUrl, directClient(), "direct")
            settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY -> raceUpstreamCalls(upstreamUrl, proxy)
            else -> {
                safeLog("Using proxy: ${proxy.displayUrl()}")
                executeUpstreamCall(upstreamUrl, proxyClient(proxy), "proxy")
            }
        }
    }

    private fun executeUpstreamCall(upstreamUrl: String, client: OkHttpClient, source: String): ByteArray {
        val call = client.newCall(Request.Builder().url(upstreamUrl).build())
        return runCatching { executeCall(call) }
            .getOrElse { error ->
                if (error is UpstreamFetchException) throw error
                throw UpstreamFetchException(502, "$source: ${error::class.java.simpleName}: ${error.message}")
            }
    }

    private fun executeCall(call: Call): ByteArray {
        val response = call.execute()
        response.use {
            if (!it.isSuccessful) {
                val failure = formatUpstreamFailure(
                    statusCode = it.code,
                    statusMessage = it.message,
                    body = it.body?.string().orEmpty(),
                )
                throw UpstreamFetchException(it.code, failure)
            }
            return it.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun raceUpstreamCalls(upstreamUrl: String, proxy: ProxyConfig): ByteArray {
        val directCall = directClient().newCall(Request.Builder().url(upstreamUrl).build())
        val proxyCall = proxyClient(proxy).newCall(Request.Builder().url(upstreamUrl).build())
        val completion = ExecutorCompletionService<UpstreamRaceResult>(upstreamRaceExecutor)
        val futures = listOf(
            completion.submit(Callable { executeRaceCall("direct", directCall) }),
            completion.submit(Callable { executeRaceCall("proxy", proxyCall) }),
        )
        val failures = mutableListOf<String>()

        repeat(futures.size) {
            val result = completion.take().getOrFailure()
            if (result.bytes != null) {
                safeLog("Upstream race winner: ${result.source}")
                cancelRaceLosers(futures, directCall, proxyCall)
                return result.bytes
            }
            failures.add("${result.source}: ${result.failure}")
        }

        throw UpstreamFetchException(502, failures.joinToString("; "))
    }

    private fun executeRaceCall(source: String, call: Call): UpstreamRaceResult =
        runCatching {
            UpstreamRaceResult(source = source, bytes = executeCall(call), failure = null)
        }.getOrElse {
            UpstreamRaceResult(source = source, bytes = null, failure = "${it::class.java.simpleName}: ${it.message}")
        }

    private fun cancelRaceLosers(
        futures: List<Future<UpstreamRaceResult>>,
        directCall: Call,
        proxyCall: Call,
    ) {
        futures.forEach { it.cancel(true) }
        directCall.cancel()
        proxyCall.cancel()
    }

    private fun Future<UpstreamRaceResult>.getOrFailure(): UpstreamRaceResult =
        try {
            get()
        } catch (error: ExecutionException) {
            UpstreamRaceResult(
                source = "unknown",
                bytes = null,
                failure = "${error.cause?.javaClass?.simpleName ?: error::class.java.simpleName}: ${error.cause?.message ?: error.message}",
            )
        }

    private fun directClient(): OkHttpClient =
        client.newBuilder()
            .proxy(Proxy.NO_PROXY)
            .build()

    private fun proxyClient(proxy: ProxyConfig): OkHttpClient =
        client.newBuilder()
            .proxy(proxy.toJavaProxy())
            .build()

    private fun scheduleSegmentPrefetch(manifest: String, manifestUrl: String) {
        val cache = segmentCache ?: return
        extractHlsSegmentUrls(manifest, manifestUrl)
            .take(PREFETCH_SEGMENT_COUNT)
            .forEach { segmentUrl ->
                cache.prefetch(segmentUrl, prefetchExecutor) {
                    safeLog("Prefetch segment: $segmentUrl")
                    fetchSegmentBytes(segmentUrl)
                }
            }
    }

    private fun cacheStats(): HlsSegmentCacheStats =
        segmentCache?.stats() ?: HlsSegmentCacheStats(entries = 0, sizeBytes = 0, hits = 0, misses = 0, inFlight = 0)

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""

        val chars = CharArray(length)
        val read = reader.read(chars, 0, length)
        return if (read > 0) String(chars, 0, read) else ""
    }

    private fun writeText(output: OutputStream, status: Int, contentType: String, body: String) {
        writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private fun writeResponse(output: OutputStream, response: DlnaHttpResponse) {
        writeBytes(
            output = output,
            status = response.statusCode,
            contentType = response.contentType,
            body = response.body.toByteArray(Charsets.UTF_8),
            extraHeaders = response.headers,
        )
    }

    private fun writeBytes(
        output: OutputStream,
        status: Int,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val reason = if (status in 200..299) "OK" else "Error"
        output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        output.write("Content-Length: ${body.size}\r\n".toByteArray())
        extraHeaders.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray())
        }
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }

    private fun safeLog(message: String) {
        runCatching { log(message) }
    }

    private data class UpstreamRaceResult(
        val source: String,
        val bytes: ByteArray?,
        val failure: String?,
    )

    private class UpstreamFetchException(
        val statusCode: Int,
        val failure: String,
    ) : RuntimeException(failure)

    private companion object {
        const val PREFETCH_SEGMENT_COUNT = 4
    }
}
