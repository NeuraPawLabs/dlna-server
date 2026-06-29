package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import labs.newrapaw.dlna.probe.core.session.ManifestPlanner
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionManager
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge
import labs.newrapaw.dlna.probe.core.session.PlannedTrackManifest
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionDownloader
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer
import labs.newrapaw.dlna.probe.core.session.SessionTimeline
import labs.newrapaw.dlna.probe.core.session.SessionTrackManifest
import labs.newrapaw.dlna.probe.core.session.TimelineSlot
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    private val getLogs: () -> List<String> = { emptyList() },
    private val proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    private val sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
    private val dlnaConfig: () -> DlnaDeviceConfig? = { null },
    private val onPlayRequested: (String) -> Unit = {},
    private val onStopRequested: () -> Unit = {},
    private val onPauseRequested: () -> Unit = {},
    private val onUpdateRequested: (String) -> Unit = {},
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val coreProxy = CoreLocalHlsProxy(
        client = client,
        log = log,
        proxySettingsStore = proxySettingsStore,
        sessionAssetRootDir = sessionAssetRootDir,
    )
    private val upstreamRaceExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sessionPrefetchExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sessionLocalServer = SessionLocalServer()
    private val manifestPlanner = ManifestPlanner()
    private val sessionAssetStore = SessionAssetStore(sessionAssetRootDir)
    private val sessionPreparationLock = Any()
    private val renderer = DlnaRendererController(
        log = log,
        onPlayRequested = ::dispatchPlaybackRequest,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
    )
    private val diagnosticsState = PlaybackDiagnosticsState()
    private val sessionManager = PlaybackSessionManager(
        createSessionId = { "session-${System.currentTimeMillis()}" },
        cleanupSession = {
            sessionAssetStore.clearSession(it.sessionId)
            if (activePreparedSession?.session?.sessionId == it.sessionId) {
                activePreparedSession = null
            }
        },
    )
    private var serverSocket: ServerSocket? = null
    private var activeSessionShell: PlaybackSession? = null
    private var activePreparedSession: PreparedSessionPlayback? = null
    private var latestPlayerPositionMs: Long? = null
    private var latestBufferedPositionMs: Long? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun publicBaseUrl(hostAddress: String): String = "http://$hostAddress:$port"

    fun activeSessionInfo(): labs.newrapaw.dlna.probe.core.ActiveSessionInfo? = coreProxy.activeSessionInfo()

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        coreProxy.updatePlaybackStatus(status)
    }

    fun updatePlaybackError(message: String?) {
        coreProxy.updatePlaybackError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        coreProxy.updatePlayerTelemetry(
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs,
            isLoading = isLoading,
        )
    }

    fun start() {
        if (running.get()) return
        coreProxy.start()
        sessionAssetStore.clearAllSessions()
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
        coreProxy.close()
        activePreparedSession?.prefetchController?.cancel()
        activeSessionShell?.let { sessionAssetStore.clearSession(it.sessionId) }
        activePreparedSession?.session?.takeIf { it.sessionId != activeSessionShell?.sessionId }?.let { sessionAssetStore.clearSession(it.sessionId) }
        sessionAssetStore.clearAllSessions()
        activePreparedSession = null
        activeSessionShell = null
        upstreamRaceExecutor.shutdownNow()
        sessionPrefetchExecutor.shutdownNow()
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
                    method == "GET" && path == "/" -> handlePage(AdminPage.PLAY, output)
                    method == "GET" && path == AdminPage.PLAY.path -> handlePage(AdminPage.PLAY, output)
                    method == "GET" && path == AdminPage.CACHE.path -> handlePage(AdminPage.CACHE, output)
                    method == "GET" && path == AdminPage.LOGS.path -> handlePage(AdminPage.LOGS, output)
                    method == "GET" && path == AdminPage.SETTINGS.path -> handlePage(AdminPage.SETTINGS, output)
                    method == "GET" && path.startsWith("/logs") -> handleLogs(output)
                    method == "GET" && path == "/diagnostics" -> handleDiagnostics(output)
                    method == "GET" && path == "/diagnostics/panel" -> handleDiagnosticsPanel(output)
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
                    method == "POST" && path.startsWith("/control/prefetch/config") -> handlePrefetchConfigRequest(body, output)
                    method == "POST" && path.startsWith("/control/cache/clear") -> handleCacheClearRequest(output)
                    (method == "GET" || method == "HEAD") && path.startsWith("/session/") -> handleSessionRoute(method, path, output)
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

    private fun handlePage(page: AdminPage, output: OutputStream) {
        val settings = proxySettingsStore.load()
        val bodyHtml = when (page) {
            AdminPage.PLAY -> buildPlayPageContent()
            AdminPage.CACHE -> buildCachePageContent(
                proxySettings = settings,
                playbackDiagnostics = coreProxy.diagnosticsSnapshot(),
                activeSession = activeSessionInfo(),
            )
            AdminPage.LOGS -> buildLogsPageContent(getLogs())
            AdminPage.SETTINGS -> buildSettingsPageContent(settings)
        }
        val pageScript = when (page) {
            AdminPage.PLAY -> ""
            AdminPage.CACHE -> buildCachePageScript()
            AdminPage.LOGS -> buildLogsPageScript()
            AdminPage.SETTINGS -> ""
        }
        writeText(
            output,
            200,
            "text/html",
            buildAdminShell(
                page = page,
                deviceName = "PawCast",
                status = "Ready",
                localPlaybackUrl = baseUrl,
                currentNetwork = controlPageCurrentNetwork(settings),
                bodyHtml = bodyHtml,
                pageScript = pageScript,
            ),
        )
    }

    private fun handleLogs(output: OutputStream) {
        writeText(output, 200, "text/plain", getLogs().joinToString("\n"))
    }

    private fun handleDiagnostics(output: OutputStream) {
        writeText(output, 200, "application/json", buildDiagnosticsJson(coreProxy.diagnosticsSnapshot()))
    }

    private fun handleDiagnosticsPanel(output: OutputStream) {
        writeText(output, 200, "text/html", buildDiagnosticsPanelHtml(coreProxy.diagnosticsSnapshot()))
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
            writeJson(output, 400, false, "Missing URL")
            return
        }

        dispatchPlaybackRequest(url)
        writeJson(output, 200, true, "Play request sent. You can return to the TV.")
    }

    private fun dispatchPlaybackRequest(sourceUrl: String) {
        safeLog("Remote play request: $sourceUrl")
        val session = coreProxy.openSession(sourceUrl)
        onPlayRequested(session.localManifestUrl)
    }

    private fun clearPreviousPlaybackCacheForNewSession() {
        activeSessionShell?.let { sessionAssetStore.clearSession(it.sessionId) }
        activeSessionShell = null
        activePreparedSession?.prefetchController?.cancel()
        activePreparedSession?.session?.let { sessionAssetStore.clearSession(it.sessionId) }
        activePreparedSession = null
        latestPlayerPositionMs = null
        latestBufferedPositionMs = null
    }

    private fun handleStopRequest(output: OutputStream) {
        safeLog("Remote stop request")
        coreProxy.updatePlaybackStatus(PlaybackDiagnosticsStatus.STOPPED)
        onStopRequested()
        writeJson(output, 200, true, "Stop request sent. You can return to the TV.")
    }

    private fun handleUpdateRequest(body: String, output: OutputStream) {
        val apkUrl = decodeFormValue(body, "apkUrl")
        if (apkUrl == null) {
            writeJson(output, 400, false, "Missing APK URL")
            return
        }

        safeLog("Remote update request: $apkUrl")
        onUpdateRequested(apkUrl)
        writeJson(output, 200, true, "Update request sent. Confirm installation on the TV.")
    }

    private fun handleProxyAddRequest(body: String, output: OutputStream) {
        val proxyUrl = decodeFormValue(body, "proxyUrl")
        val config = proxyUrl?.let(::parseProxyConfig)
        if (config == null) {
            writeJson(output, 400, false, "Invalid proxy URL. Use http://host:port, socks5://host:port, or socks5h://host:port.")
            return
        }

        val next = proxySettingsStore.load().add(config).select(config.id)
        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${config.displayUrl()}")
        writeJson(output, 200, true, "Proxy saved: ${config.displayUrl()}")
    }

    private fun handleProxySelectRequest(body: String, output: OutputStream) {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null) {
            writeJson(output, 400, false, "Missing proxyId")
            return
        }

        val current = proxySettingsStore.load()
        val mode = decodeFormValue(body, "upstreamMode")?.let(::parseUpstreamMode) ?: current.upstreamMode
        val next = current.select(proxyId).withUpstreamMode(mode)
        if (next.selectedProxyId != proxyId) {
            writeJson(output, 400, false, "Unknown proxy")
            return
        }

        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${next.selectedProxy()?.displayUrl() ?: "Direct"}")
        writeJson(output, 200, true, "Proxy selected")
    }

    private fun parseUpstreamMode(value: String): UpstreamMode =
        runCatching { UpstreamMode.valueOf(value) }.getOrDefault(UpstreamMode.PROXY_ONLY)

    private fun handleProxyDeleteRequest(body: String, output: OutputStream) {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null || proxyId == ProxySettingsState.DIRECT_PROXY_ID) {
            writeJson(output, 400, false, "Missing proxyId")
            return
        }

        val next = proxySettingsStore.load().remove(proxyId)
        proxySettingsStore.save(next)
        safeLog("Proxy deleted: $proxyId")
        writeJson(output, 200, true, "Proxy deleted")
    }

    private fun handleCacheClearRequest(output: OutputStream) {
        coreProxy.clearActiveSessionCache()
        safeLog("Session cache cleared")
        writeJson(output, 200, true, "Cache cleared")
    }

    private fun handleSessionRoute(method: String, path: String, output: OutputStream) {
        val response = client.newCall(
            Request.Builder()
                .url("${coreProxy.baseUrl}$path")
                .method(method, null)
                .build(),
        ).execute()
        response.use {
            val contentType = it.header("Content-Type") ?: "application/octet-stream"
            val contentLength = it.body?.contentLength()?.takeIf { length -> length >= 0L }
            val extraHeaders = it.headers.names()
                .asSequence()
                .filterNot { name ->
                    name.equals("Content-Type", ignoreCase = true) ||
                        name.equals("Content-Length", ignoreCase = true) ||
                        name.equals("Connection", ignoreCase = true)
                }
                .associateWith { name -> it.header(name).orEmpty() }
            writeResponseHeaders(
                output = output,
                status = it.code,
                contentType = contentType,
                contentLength = contentLength,
                extraHeaders = extraHeaders,
            )
            if (!method.equals("HEAD", ignoreCase = true)) {
                val input = it.body?.byteStream()
                if (input != null) {
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                }
            }
        }
    }

    private fun handlePrefetchConfigRequest(body: String, output: OutputStream) {
        val requested = decodeFormValue(body, "prefetchConcurrency")?.toIntOrNull()
            ?: ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY
        val next = proxySettingsStore.load().copy(prefetchConcurrency = requested).normalized()
        proxySettingsStore.save(next)
        coreProxy.updatePrefetchConcurrency(next.prefetchConcurrency)
        safeLog("Prefetch concurrency updated: ${next.prefetchConcurrency}")
        writeJson(output, 200, true, "Prefetch concurrency updated")
    }

    private fun fetchSegmentBytes(upstreamUrl: String): ByteArray {
        val bytes = fetchUpstreamBytes(upstreamUrl)
        return if (looksLikeTransportStream(upstreamUrl)) stripPngWrapperFromSegment(bytes) else bytes
    }

    private fun ensurePreparedSession(session: PlaybackSession): PreparedSessionPlayback? {
        activePreparedSession?.takeIf { it.session.sessionId == session.sessionId }?.let { return it }
        return synchronized(sessionPreparationLock) {
            activePreparedSession?.takeIf { it.session.sessionId == session.sessionId }?.let { return@synchronized it }
            runCatching {
                diagnosticsState.setSessionStatus(PlaybackSessionStatus.PREPARING.name)
                val sourceManifest = fetchUpstreamBytes(session.sourceUrl).toString(Charsets.UTF_8)
                val master = parseSingleVariantMasterManifest(sourceManifest, session.sourceUrl)
                if (looksLikeMasterPlaylist(sourceManifest) && master == null) {
                    throw UnsupportedSessionSourceException(
                        statusCode = 422,
                        message = "Unsupported master playlist: multiple variants are not supported in session mode",
                    )
                }
                diagnosticsState.updateStartupGate(
                    phase = "启动预热",
                    ready = false,
                    detail = "正在构建会话资源清单",
                )
                diagnosticsState.setSessionStatus(PlaybackSessionStatus.PRIMING.name)
                val videoManifestUrl: String
                val videoManifestBody: String
                val audioTracks: List<PlannedTrackManifest>
                val subtitleTracks: List<PlannedTrackManifest>
                if (master != null) {
                    videoManifestUrl = master.variantUrl
                    videoManifestBody = fetchUpstreamBytes(videoManifestUrl).toString(Charsets.UTF_8)
                    audioTracks = master.audioTracks.mapIndexed { index, track ->
                        val trackId = buildSessionTrackId("audio", track.name, track.language, index)
                        PlannedTrackManifest(
                            trackId = trackId,
                            kind = SessionAssetKind.AUDIO_SEGMENT,
                            manifestUrl = track.uri,
                            manifestBody = fetchUpstreamBytes(track.uri).toString(Charsets.UTF_8),
                        )
                    }
                    subtitleTracks = master.subtitleTracks.mapIndexed { index, track ->
                        val trackId = buildSessionTrackId("subtitle", track.name, track.language, index)
                        PlannedTrackManifest(
                            trackId = trackId,
                            kind = SessionAssetKind.SUBTITLE_SEGMENT,
                            manifestUrl = track.uri,
                            manifestBody = fetchUpstreamBytes(track.uri).toString(Charsets.UTF_8),
                        )
                    }
                } else {
                    videoManifestUrl = session.sourceUrl
                    videoManifestBody = sourceManifest
                    audioTracks = emptyList()
                    subtitleTracks = emptyList()
                }

                val plan = manifestPlanner.plan(
                    manifestUrl = videoManifestUrl,
                    videoManifest = videoManifestBody,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                )
                val preparedSession = session.copy(
                    status = PlaybackSessionStatus.READY,
                    timeline = SessionTimeline(slots = plan.slots, assets = plan.assets),
                )
                val assetsById = plan.assets.associateBy { it.assetId }
                val prepared = PreparedSessionPlayback(
                    session = preparedSession,
                    masterManifest = sessionLocalServer.buildMasterManifest(
                        sessionId = session.sessionId,
                        audioTracks = audioTracks.mapIndexed { index, track ->
                            SessionTrackManifest(
                                trackId = track.trackId,
                                name = master?.audioTracks?.getOrNull(index)?.name ?: track.trackId,
                                language = master?.audioTracks?.getOrNull(index)?.language,
                                kind = SessionAssetKind.AUDIO_SEGMENT,
                                playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.AUDIO_SEGMENT, track.trackId),
                            )
                        },
                        subtitleTracks = subtitleTracks.mapIndexed { index, track ->
                            SessionTrackManifest(
                                trackId = track.trackId,
                                name = master?.subtitleTracks?.getOrNull(index)?.name ?: track.trackId,
                                language = master?.subtitleTracks?.getOrNull(index)?.language,
                                kind = SessionAssetKind.SUBTITLE_SEGMENT,
                                playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.SUBTITLE_SEGMENT, track.trackId),
                            )
                        },
                    ),
                    videoPlaylist = sessionLocalServer.buildMediaPlaylist(
                        sessionId = session.sessionId,
                        trackId = "video-main",
                        kind = SessionAssetKind.VIDEO_SEGMENT,
                        slots = plan.slots,
                        assetsById = assetsById,
                    ),
                    audioPlaylists = audioTracks.associate { track ->
                        track.trackId to sessionLocalServer.buildMediaPlaylist(
                            sessionId = session.sessionId,
                            trackId = track.trackId,
                            kind = SessionAssetKind.AUDIO_SEGMENT,
                            slots = plan.slots,
                            assetsById = assetsById,
                        )
                    },
                    subtitlePlaylists = subtitleTracks.associate { track ->
                        track.trackId to sessionLocalServer.buildMediaPlaylist(
                            sessionId = session.sessionId,
                            trackId = track.trackId,
                            kind = SessionAssetKind.SUBTITLE_SEGMENT,
                            slots = plan.slots,
                            assetsById = assetsById,
                        )
                    },
                    assetsById = assetsById,
                    assetRuntime = plan.assets.associate { it.assetId to SessionAssetRuntime() }.toMutableMap(),
                    telemetryBridge = PlaybackTelemetryBridge(plan.slots),
                    prefetchController = SessionPrefetchController(
                        queue = buildSessionPrefetchQueue(plan.assets),
                        executor = sessionPrefetchExecutor,
                        initialConcurrency = proxySettingsStore.load().prefetchConcurrency,
                        loadAsset = {},
                    ),
                    preparationFailure = null,
                )
                prepared.prefetchController.replaceLoadAsset { assetId ->
                    preparedLoadAssetById(
                        sessionId = prepared.session.sessionId,
                        assetsById = prepared.assetsById,
                        assetRuntime = prepared.assetRuntime,
                        assetId = assetId,
                    )
                }
                activePreparedSession = prepared
                prepared.prefetchController.start()
                diagnosticsState.setSessionStatus(prepared.session.status.name)
                refreshPreparedSessionDiagnostics()
                prepared
            }.recover { error ->
                if (error is UnsupportedSessionSourceException) {
                    PreparedSessionPlayback(
                        session = session.copy(status = PlaybackSessionStatus.FAILED),
                        masterManifest = "",
                        videoPlaylist = "",
                        audioPlaylists = emptyMap(),
                        subtitlePlaylists = emptyMap(),
                        assetsById = emptyMap(),
                        assetRuntime = mutableMapOf(),
                        telemetryBridge = PlaybackTelemetryBridge(emptyList()),
                        prefetchController = SessionPrefetchController(
                            queue = ArrayDeque(),
                            executor = sessionPrefetchExecutor,
                            initialConcurrency = 1,
                            loadAsset = {},
                        ),
                        preparationFailure = error,
                    ).also {
                        activePreparedSession = it
                        diagnosticsState.setSessionStatus(PlaybackSessionStatus.FAILED.name)
                        diagnosticsState.setLastError(error.message)
                    }
                } else {
                    throw error
                }
            }.getOrElse { error ->
                safeLog("Session prepare failed: ${error::class.java.simpleName}: ${error.message}")
                null
            }
        }
    }

    private fun loadSessionAsset(
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): ByteArray {
        return preparedLoadAssetById(
            sessionId = prepared.session.sessionId,
            assetsById = prepared.assetsById,
            assetRuntime = prepared.assetRuntime,
            assetId = asset.assetId,
        )
    }

    private fun waitForSessionAsset(
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): ByteArray? {
        if (isPreparedAssetReady(prepared, asset.assetId)) {
            return loadSessionAsset(prepared, asset)
        }
        val runtime = prepared.assetRuntime.getOrPut(asset.assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            if (runtime.state == SessionAssetState.FAILED) {
                return null
            }
        }
        return runCatching {
            loadSessionAsset(prepared, asset)
        }.getOrElse {
            val updatedRuntime = prepared.assetRuntime[asset.assetId]
            if (updatedRuntime?.state == SessionAssetState.FAILED) {
                null
            } else {
                throw it
            }
        }
    }

    private fun ensureStartupAssetsReady(prepared: PreparedSessionPlayback) {
        val startupAssets = prepared.assetsById.values
            .filter { it.requiredForStartup }
            .sortedWith(compareBy<SessionAsset> { it.sequence ?: Int.MAX_VALUE }.thenBy { it.assetId })
        val missingStartup = startupAssets.count { asset -> !isPreparedAssetReady(prepared, asset.assetId) }
        diagnosticsState.updateStartupGate(
            phase = "启动预热",
            ready = missingStartup == 0,
            detail = if (missingStartup == 0) "启动资源已就绪" else "仍缺少 $missingStartup 个启动资源",
        )
        startupAssets.forEach { asset ->
            if (!isPreparedAssetReady(prepared, asset.assetId)) {
                prepared.prefetchController.enqueueFront(asset.assetId)
            }
        }
    }

    private fun preparedLoadAssetById(
        sessionId: String,
        assetsById: Map<String, SessionAsset>,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
    ): ByteArray {
        val asset = assetsById.getValue(assetId)
        val runtime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.resolveAsset(sessionId, assetId)
            if (existing.exists()) {
                runtime.state = SessionAssetState.READY
                runtime.localFile = existing
                runtime.lastSource = "session-local"
                return existing.readBytes()
            }
            if (runtime.state == SessionAssetState.DOWNLOADING) {
                return waitForPreparedAssetInFlight(sessionId, assetRuntime, assetId, runtime)
                    ?: throw IllegalStateException("asset load did not complete: $assetId")
            }
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
        }
        var lastError: Throwable? = null
        repeat(SESSION_ASSET_MAX_RETRY_COUNT) { attempt ->
            runCatching {
                val startedAt = System.nanoTime()
                synchronized(runtime.lock) {
                    runtime.retryCount += 1
                    runtime.state = SessionAssetState.DOWNLOADING
                    runtime.lastError = null
                }
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = asset.assetId,
                    kind = asset.kind.name,
                    trackId = asset.trackId,
                    source = "session-local",
                )
                val bytes = fetchSegmentBytes(asset.url)
                val file = sessionAssetStore.writeAsset(sessionId, assetId, bytes)
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = file
                    runtime.lastElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.lastSource = "session-local"
                    runtime.lastError = null
                    runtime.lock.notifyAll()
                }
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = null,
                    kind = null,
                    trackId = null,
                    source = null,
                )
                refreshPreparedSessionDiagnostics()
                return bytes
            }.onFailure { error ->
                lastError = error
                synchronized(runtime.lock) {
                    runtime.lastElapsedMs = null
                    runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
                    runtime.lastSource = "session-local"
                    if (attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                        runtime.state = SessionAssetState.FAILED
                    }
                    runtime.lock.notifyAll()
                }
                if (attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                    diagnosticsState.updateCurrentLoadingAsset(
                        assetId = null,
                        kind = null,
                        trackId = null,
                        source = null,
                    )
                }
                refreshPreparedSessionDiagnostics()
                if (attempt < SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                    Thread.sleep(SESSION_ASSET_RETRY_DELAY_MS)
                }
            }
        }
        throw lastError ?: IllegalStateException("asset load failed without exception: $assetId")
    }

    private fun waitForPreparedAssetInFlight(
        sessionId: String,
        assetRuntime: MutableMap<String, SessionAssetRuntime>,
        assetId: String,
        runtime: SessionAssetRuntime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() },
    ): ByteArray? {
        while (true) {
            val state = synchronized(runtime.lock) {
                val existing = sessionAssetStore.resolveAsset(sessionId, assetId)
                if (existing.exists()) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = existing
                    return existing.readBytes()
                }
                val currentState = runtime.state
                if (currentState == SessionAssetState.FAILED || currentState == SessionAssetState.NOT_STARTED) {
                    return null
                }
                runCatching { runtime.lock.wait(50L) }
                currentState
            }
            if (state == SessionAssetState.FAILED || state == SessionAssetState.NOT_STARTED) {
                return null
            }
        }
    }

    private fun fetchUpstreamBytes(upstreamUrl: String): ByteArray {
        val settings = proxySettingsStore.load()
        diagnosticsState.setUpstreamSettings(settings)
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
        val startedAt = System.nanoTime()
        return runCatching { executeCall(call) }
            .onSuccess { bytes ->
                val elapsedMs = nanosToMillis(startedAt)
                diagnosticsState.onSegmentResult(upstreamUrl, source, elapsedMs, success = true)
                refreshDiagnosticsSnapshot()
            }
            .getOrElse { error ->
                val elapsedMs = nanosToMillis(startedAt)
                diagnosticsState.onSegmentResult(
                    url = upstreamUrl,
                    source = source,
                    elapsedMs = elapsedMs,
                    success = false,
                    fallbackReason = "${error::class.java.simpleName}: ${error.message}",
                )
                refreshDiagnosticsSnapshot()
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
                diagnosticsState.onSegmentResult(upstreamUrl, result.source, result.elapsedMs, success = true)
                cancelRaceLosers(futures, directCall, proxyCall)
                refreshDiagnosticsSnapshot()
                return result.bytes
            }
            failures.add("${result.source}: ${result.failure}")
        }

        diagnosticsState.onSegmentResult(
            url = upstreamUrl,
            source = "race",
            elapsedMs = -1,
            success = false,
            fallbackReason = failures.joinToString("; "),
        )
        refreshDiagnosticsSnapshot()
        throw UpstreamFetchException(502, failures.joinToString("; "))
    }

    private fun executeRaceCall(source: String, call: Call): UpstreamRaceResult =
        run {
            val startedAt = System.nanoTime()
            runCatching {
                UpstreamRaceResult(
                    source = source,
                    bytes = executeCall(call),
                    failure = null,
                    elapsedMs = nanosToMillis(startedAt),
                )
            }.getOrElse {
                UpstreamRaceResult(
                    source = source,
                    bytes = null,
                    failure = "${it::class.java.simpleName}: ${it.message}",
                    elapsedMs = nanosToMillis(startedAt),
                )
            }
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
                elapsedMs = -1,
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

    private fun refreshDiagnosticsSnapshot() {
        refreshPreparedSessionDiagnostics()
    }

    private fun refreshPreparedSessionDiagnostics() {
        val prepared = activePreparedSession ?: return
        val baseSlotStates = prepared.session.timeline.slots.map { slot ->
            val hardDependencyIds = buildList {
                slot.prerequisiteAssetIds.forEach(::add)
                slot.videoAssetId?.let(::add)
                slot.audioAssetIds.forEach(::add)
            }
            val blockedKinds = hardDependencyIds
                .filterNot { isPreparedAssetReady(prepared, it) }
                .mapNotNull { prepared.assetsById[it]?.kind }
                .distinct()
            val degradedKinds = slot.subtitleAssetIds
                .filterNot { isPreparedAssetReady(prepared, it) }
                .mapNotNull { prepared.assetsById[it]?.kind }
                .distinct()
            val state = when {
                blockedKinds.isNotEmpty() -> SlotDiagnosticsState.BLOCKED
                degradedKinds.isNotEmpty() -> SlotDiagnosticsState.DEGRADED
                else -> SlotDiagnosticsState.READY
            }
            SlotDiagnosticsItem(
                slotIndex = slot.slotIndex,
                startMs = slot.startMs,
                endMs = slot.endMs,
                state = state,
                videoReady = slot.videoAssetId?.let { isPreparedAssetReady(prepared, it) } ?: false,
                audioReady = slot.audioAssetIds.all { isPreparedAssetReady(prepared, it) },
                subtitleReady = slot.subtitleAssetIds.all { isPreparedAssetReady(prepared, it) },
                blockedAssetKinds = blockedKinds,
                degradedAssetKinds = degradedKinds,
                videoAssetIdRef = slot.videoAssetId,
                audioAssetIdRefs = slot.audioAssetIds,
                subtitleAssetIdRefs = slot.subtitleAssetIds,
                prerequisiteAssetIdRefs = buildList {
                    addAll(slot.prerequisiteAssetIds)
                    slot.audioPrerequisiteAssetIds.values.forEach(::addAll)
                    slot.subtitlePrerequisiteAssetIds.values.forEach(::addAll)
                }.distinct(),
            )
        }
        val telemetry = if (latestPlayerPositionMs != null && latestBufferedPositionMs != null) {
            prepared.telemetryBridge.snapshot(
                currentPositionMs = latestPlayerPositionMs ?: 0L,
                bufferedPositionMs = latestBufferedPositionMs ?: 0L,
                isLoading = diagnosticsState.snapshot().playerIsLoading ?: false,
            )
        } else {
            null
        }
        val currentSlotIndex = telemetry?.playHeadSlotIndex
        val bufferedSlotIndex = telemetry?.bufferHeadSlotIndex
        currentSlotIndex?.let { reprioritizePreparedSessionQueue(prepared, it) }
        val slotStates = baseSlotStates.map { item ->
            if (item.slotIndex == currentSlotIndex && item.state != SlotDiagnosticsState.BLOCKED) {
                item.copy(state = SlotDiagnosticsState.PLAYING)
            } else {
                item
            }
        }
        val playableWindow = computeContinuousPlayableWindow(slotStates, currentSlotIndex)
        diagnosticsState.updateSlotDiagnostics(
            slotStates = slotStates,
            currentPlaybackSlotIndex = currentSlotIndex,
            bufferedSlotIndex = bufferedSlotIndex,
            currentPlaybackSlotReady = currentSlotIndex?.let { index ->
                slotStates.firstOrNull { it.slotIndex == index }?.state != SlotDiagnosticsState.BLOCKED
            },
            continuousReadySlotCount = playableWindow.first,
            continuousReadySlotDurationMs = playableWindow.second,
        )
        diagnosticsState.updatePrefetchStats(
            prefetchConcurrency = proxySettingsStore.load().prefetchConcurrency,
            pendingPrefetchCount = prepared.prefetchController.snapshotQueue().size,
            inFlightCount = prepared.prefetchController.snapshotActiveAssetIds().size,
        )
        diagnosticsState.updateAssetDiagnostics(
            prepared.assetsById.values.map { asset ->
                val runtime = prepared.assetRuntime[asset.assetId]
                val file = sessionAssetStore.resolveAsset(prepared.session.sessionId, asset.assetId)
                AssetDiagnosticsItem(
                    assetId = asset.assetId,
                    kind = asset.kind,
                    trackId = asset.trackId,
                    state = runtime?.state ?: if (file.exists()) SessionAssetState.READY else SessionAssetState.NOT_STARTED,
                    localReady = file.exists(),
                    sizeBytes = file.takeIf { it.exists() }?.length(),
                    lastElapsedMs = runtime?.lastElapsedMs,
                    lastSource = runtime?.lastSource,
                    retryCount = runtime?.retryCount ?: 0,
                    failureReason = runtime?.lastError,
                )
            },
        )
        diagnosticsState.setSessionStatus(
            when {
                currentSlotIndex != null && slotStates.firstOrNull { it.slotIndex == currentSlotIndex }?.state == SlotDiagnosticsState.BLOCKED ->
                    PlaybackSessionStatus.STALLED.name
                slotStates.any { it.state == SlotDiagnosticsState.DEGRADED } ->
                    PlaybackSessionStatus.DEGRADED.name
                currentSlotIndex != null ->
                    PlaybackSessionStatus.PLAYING.name
                else ->
                    prepared.session.status.name
            },
        )
    }

    private fun isPreparedAssetReady(prepared: PreparedSessionPlayback, assetId: String): Boolean {
        val runtime = prepared.assetRuntime[assetId]
        if (runtime?.state == SessionAssetState.READY) return true
        val file = sessionAssetStore.resolveAsset(prepared.session.sessionId, assetId)
        if (file.exists()) {
            prepared.assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }.apply {
                state = SessionAssetState.READY
                localFile = file
            }
            return true
        }
        return false
    }

    private fun computeContinuousPlayableWindow(
        slotStates: List<SlotDiagnosticsItem>,
        currentSlotIndex: Int?,
    ): Pair<Int, Long> {
        if (currentSlotIndex == null) return 0 to 0L
        var count = 0
        var duration = 0L
        slotStates.asSequence()
            .filter { it.slotIndex >= currentSlotIndex }
            .takeWhile { it.state != SlotDiagnosticsState.BLOCKED }
            .forEach { item ->
                count += 1
                duration += (item.endMs - item.startMs).coerceAtLeast(0L)
            }
        return count to duration
    }

    private fun reprioritizePreparedSessionQueue(
        prepared: PreparedSessionPlayback,
        playHeadSlotIndex: Int,
    ) {
        val readyAssetIds = prepared.assetRuntime
            .filterValues {
                it.state == SessionAssetState.READY ||
                    it.state == SessionAssetState.DOWNLOADING ||
                    it.state == SessionAssetState.QUEUED
            }
            .keys
        val queue = SessionDownloader.planPlaybackQueue(
            slots = prepared.session.timeline.slots,
            assetsById = prepared.assetsById,
            playHeadSlotIndex = playHeadSlotIndex,
            readyAssetIds = readyAssetIds,
        )
        prepared.prefetchController.replaceQueue(queue)
    }

    private fun noteRequestedPlaybackSlot(
        prepared: PreparedSessionPlayback,
        slotIndex: Int,
    ) {
        latestPlayerPositionMs = prepared.session.timeline.slots
            .firstOrNull { it.slotIndex == slotIndex }
            ?.startMs
        latestBufferedPositionMs = latestPlayerPositionMs
        reprioritizePreparedSessionQueue(prepared, slotIndex)
        refreshPreparedSessionDiagnostics()
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

    private fun writeJson(output: OutputStream, status: Int, ok: Boolean, message: String) {
        val body = """{"ok":$ok,"message":"${escapeJson(message)}"}"""
        writeText(output, status, "application/json", body)
    }

    private fun buildDiagnosticsJson(snapshot: PlaybackDiagnosticsSnapshot): String =
        buildString {
            append('{')
            appendJsonField("playbackStatus", snapshot.playbackStatus.name)
            append(',')
            appendJsonField("sessionStatus", snapshot.sessionStatus)
            append(',')
            appendJsonField("sessionStartedAtMs", snapshot.sessionStartedAtMs)
            append(',')
            appendJsonField("sourceUrl", snapshot.sourceUrl)
            append(',')
            appendJsonField("localProxyUrl", snapshot.localProxyUrl)
            append(',')
            appendJsonField("lastUpdatedAtMs", snapshot.lastUpdatedAtMs)
            append(',')
            appendJsonField("upstreamMode", snapshot.upstreamMode.name)
            append(',')
            appendJsonField("activeProxy", snapshot.activeProxy)
            append(',')
            appendJsonField("lastError", snapshot.lastError)
            append(',')
            appendJsonField("lastRequestedSegment", snapshot.lastRequestedSegment)
            append(',')
            appendJsonField("lastSucceededSegment", snapshot.lastSucceededSegment)
            append(',')
            appendJsonField("lastFailedSegment", snapshot.lastFailedSegment)
            append(',')
            appendJsonField("consecutiveFailures", snapshot.consecutiveFailures)
            append(',')
            appendJsonField("recentSegmentSamples", buildJsonArray(snapshot.recentSegmentSamples) { sample ->
                buildString {
                    append('{')
                    appendJsonField("url", sample.url)
                    append(',')
                    appendJsonField("source", sample.source)
                    append(',')
                    appendJsonField("elapsedMs", sample.elapsedMs)
                    append(',')
                    appendJsonField("success", sample.success)
                    append(',')
                    appendJsonField("reason", sample.reason)
                    append('}')
                }
            }, isRawJson = true)
            append(',')
            appendJsonField("prefetchConcurrency", snapshot.prefetchConcurrency)
            append(',')
            appendJsonField("pendingPrefetchCount", snapshot.pendingPrefetchCount)
            append(',')
            appendJsonField("currentLoadingAssetId", snapshot.currentLoadingAssetId)
            append(',')
            appendJsonField("currentLoadingAssetKind", snapshot.currentLoadingAssetKind)
            append(',')
            appendJsonField("currentLoadingTrackId", snapshot.currentLoadingTrackId)
            append(',')
            appendJsonField("currentLoadingSource", snapshot.currentLoadingSource)
            append(',')
            appendJsonField("slotStates", buildJsonArray(snapshot.slotStates) { item ->
                buildString {
                    append('{')
                    appendJsonField("slotIndex", item.slotIndex)
                    append(',')
                    appendJsonField("startMs", item.startMs)
                    append(',')
                    appendJsonField("endMs", item.endMs)
                    append(',')
                    appendJsonField("state", item.state.name)
                    append(',')
                    appendJsonField("videoReady", item.videoReady)
                    append(',')
                    appendJsonField("audioReady", item.audioReady)
                    append(',')
                    appendJsonField("subtitleReady", item.subtitleReady)
                    append(',')
                    appendJsonField(
                        "blockedAssetKinds",
                        buildJsonArray(item.blockedAssetKinds) { kind -> "\"${escapeJson(kind.name)}\"" },
                        isRawJson = true,
                    )
                    append(',')
                    appendJsonField(
                        "degradedAssetKinds",
                        buildJsonArray(item.degradedAssetKinds) { kind -> "\"${escapeJson(kind.name)}\"" },
                        isRawJson = true,
                    )
                    append('}')
                }
            }, isRawJson = true)
            append(',')
            appendJsonField("assetDiagnostics", buildJsonArray(snapshot.assetDiagnostics) { item ->
                buildString {
                    append('{')
                    appendJsonField("assetId", item.assetId)
                    append(',')
                    appendJsonField("kind", item.kind.name)
                    append(',')
                    appendJsonField("trackId", item.trackId)
                    append(',')
                    appendJsonField("state", item.state.name)
                    append(',')
                    appendJsonField("localReady", item.localReady)
                    append(',')
                    appendJsonField("sizeBytes", item.sizeBytes)
                    append(',')
                    appendJsonField("lastElapsedMs", item.lastElapsedMs)
                    append(',')
                    appendJsonField("lastSource", item.lastSource)
                    append(',')
                    appendJsonField("retryCount", item.retryCount)
                    append(',')
                    appendJsonField("failureReason", item.failureReason)
                    append('}')
                }
            }, isRawJson = true)
            append(',')
            appendJsonField("currentPlaybackSlotIndex", snapshot.currentPlaybackSlotIndex)
            append(',')
            appendJsonField("currentPlaybackSlotReady", snapshot.currentPlaybackSlotReady)
            append(',')
            appendJsonField("bufferedSlotIndex", snapshot.bufferedSlotIndex)
            append(',')
            appendJsonField("startupGatePhase", snapshot.startupGatePhase)
            append(',')
            appendJsonField("startupGateReady", snapshot.startupGateReady)
            append(',')
            appendJsonField("startupGateDetail", snapshot.startupGateDetail)
            append(',')
            appendJsonField("currentStallReason", snapshot.currentStallReason)
            append(',')
            appendJsonField("playerPositionMs", snapshot.playerPositionMs)
            append(',')
            appendJsonField("playerBufferedPositionMs", snapshot.playerBufferedPositionMs)
            append(',')
            appendJsonField("playerIsLoading", snapshot.playerIsLoading)
            append(',')
            appendJsonField("continuousReadySlotCount", snapshot.continuousReadySlotCount)
            append(',')
            appendJsonField("continuousReadySlotDurationMs", snapshot.continuousReadySlotDurationMs)
            append(',')
            appendJsonField("sessionReadyAssetCount", snapshot.sessionReadyAssetCount)
            append(',')
            appendJsonField("sessionTotalAssetCount", snapshot.sessionTotalAssetCount)
            append(',')
            appendJsonField("sessionReadyBytes", snapshot.sessionReadyBytes)
            append(',')
            appendJsonField("inFlightCount", snapshot.inFlightCount)
            append(',')
            appendJsonField("directWinCount", snapshot.directWinCount)
            append(',')
            appendJsonField("proxyWinCount", snapshot.proxyWinCount)
            append(',')
            appendJsonField("directAverageElapsedMs", snapshot.directAverageElapsedMs)
            append(',')
            appendJsonField("proxyAverageElapsedMs", snapshot.proxyAverageElapsedMs)
            append(',')
            appendJsonField("lastFiveAverageElapsedMs", snapshot.lastFiveAverageElapsedMs)
            append(',')
            appendJsonField("lastFiveFailureCount", snapshot.lastFiveFailureCount)
            append(',')
            appendJsonField("lastTwentyAverageElapsedMs", snapshot.lastTwentyAverageElapsedMs)
            append(',')
            appendJsonField("lastTwentyFailureCount", snapshot.lastTwentyFailureCount)
            append(',')
            appendJsonField("severity", snapshot.severity.name)
            append(',')
            appendJsonField("isStale", snapshot.isStale)
            append(',')
            appendJsonField("insights", buildJsonArray(snapshot.insights) { insight ->
                buildString {
                    append('{')
                    appendJsonField("code", insight.code)
                    append(',')
                    appendJsonField("message", insight.message)
                    append(',')
                    appendJsonField("detail", insight.detail)
                    append('}')
                }
            }, isRawJson = true)
            append(',')
            appendJsonField("primaryBottleneck", snapshot.primaryBottleneck?.let { insight ->
                buildString {
                    append('{')
                    appendJsonField("code", insight.code)
                    append(',')
                    appendJsonField("message", insight.message)
                    append(',')
                    appendJsonField("detail", insight.detail)
                    append('}')
                }
            }, isRawJson = true)
            append(',')
            appendJsonField("timeoutCount", snapshot.timeoutCount)
            append(',')
            appendJsonField("fallbackCount", snapshot.fallbackCount)
            append(',')
            appendJsonField("lastFallbackReason", snapshot.lastFallbackReason)
            append('}')
        }

    private fun <T> buildJsonArray(items: List<T>, itemBuilder: (T) -> String): String =
        items.joinToString(prefix = "[", postfix = "]", separator = ",", transform = itemBuilder)

    private fun StringBuilder.appendJsonField(name: String, value: String?, isRawJson: Boolean = false) {
        append('"')
        append(escapeJson(name))
        append("\":")
        when {
            value == null -> append("null")
            isRawJson -> append(value)
            else -> {
                append('"')
                append(escapeJson(value))
                append('"')
            }
        }
    }

    private fun StringBuilder.appendJsonField(name: String, value: Number?) {
        append('"')
        append(escapeJson(name))
        append("\":")
        append(value ?: "null")
    }

    private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
        append('"')
        append(escapeJson(name))
        append("\":")
        append(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Boolean?) {
        append('"')
        append(escapeJson(name))
        append("\":")
        append(value ?: "null")
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

    private fun safeLog(message: String) {
        runCatching { log(message) }
    }

    private fun shouldSuppressRequestFailureLog(error: Throwable): Boolean {
        val socketError = error as? SocketException ?: return false
        val message = socketError.message.orEmpty()
        return message.contains("Broken pipe", ignoreCase = true)
    }

    private fun guessSegmentContentType(upstreamUrl: String): String {
        val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
        return when {
            path.endsWith(".m4s", ignoreCase = true) || path.endsWith(".mp4", ignoreCase = true) || path.endsWith(".cmfv", ignoreCase = true) -> "video/mp4"
            path.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            path.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> "video/mp2t"
        }
    }

    private fun looksLikeTransportStream(upstreamUrl: String): Boolean {
        val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
        return path.endsWith(".ts", ignoreCase = true) || path.endsWith(".png", ignoreCase = true)
    }

    private fun nanosToMillis(startedAt: Long): Long =
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

    private fun buildSessionTrackId(
        prefix: String,
        name: String?,
        language: String?,
        index: Int,
    ): String {
        val normalizedName = (name ?: "$prefix-$index")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "$prefix-$index" }
        val normalizedLanguage = language
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
        return listOf(prefix, normalizedName, normalizedLanguage)
            .filterNotNull()
            .joinToString("-")
    }

    private fun findSlotIndexForAsset(slots: List<TimelineSlot>, assetId: String): Int? =
        slots.firstOrNull { slot ->
            slot.videoAssetId == assetId ||
                assetId in slot.audioAssetIds ||
                assetId in slot.subtitleAssetIds ||
                assetId in slot.prerequisiteAssetIds ||
                slot.audioPrerequisiteAssetIds.values.any { assetId in it } ||
                slot.subtitlePrerequisiteAssetIds.values.any { assetId in it }
        }?.slotIndex

    private fun buildSessionPrefetchQueue(assets: List<SessionAsset>): ArrayDeque<String> {
        val startup = SessionDownloader.planStartupQueue(assets)
        val remaining = assets
            .filterNot { asset -> startup.any { it.assetId == asset.assetId } }
            .sortedWith(compareBy<SessionAsset> { it.sequence ?: Int.MAX_VALUE }.thenBy { it.assetId })
        return ArrayDeque((startup + remaining).map { it.assetId })
    }

    private data class UpstreamRaceResult(
        val source: String,
        val bytes: ByteArray?,
        val failure: String?,
        val elapsedMs: Long,
    )

    private data class PreparedSessionPlayback(
        val session: PlaybackSession,
        val masterManifest: String,
        val videoPlaylist: String,
        val audioPlaylists: Map<String, String>,
        val subtitlePlaylists: Map<String, String>,
        val assetsById: Map<String, SessionAsset>,
        val assetRuntime: MutableMap<String, SessionAssetRuntime>,
        val telemetryBridge: PlaybackTelemetryBridge,
        val prefetchController: SessionPrefetchController,
        val preparationFailure: UnsupportedSessionSourceException?,
    )

    private class SessionAssetRuntime(
        var state: SessionAssetState = SessionAssetState.NOT_STARTED,
        var localFile: File? = null,
        var lastError: String? = null,
        var lastElapsedMs: Long? = null,
        var lastSource: String? = null,
        var retryCount: Int = 0,
        val lock: Object = Object(),
    )

    private class SessionPrefetchController(
        private val queue: ArrayDeque<String>,
        private val executor: ExecutorService,
        initialConcurrency: Int,
        private var loadAsset: (String) -> Unit,
    ) {
        private val running = AtomicBoolean(true)
        private val coordinatorStarted = AtomicBoolean(false)
        private val desiredConcurrency = AtomicInteger(initialConcurrency.coerceIn(1, ProxySettingsState.MAX_PREFETCH_CONCURRENCY))
        private val activeCount = AtomicInteger(0)
        private val activeAssetIds = linkedSetOf<String>()
        private val lock = Object()

        fun replaceLoadAsset(next: (String) -> Unit) {
            loadAsset = next
        }

        fun start() {
            if (!coordinatorStarted.compareAndSet(false, true)) return
            executor.execute {
                while (running.get()) {
                    while (running.get() && activeCount.get() < desiredConcurrency.get()) {
                        val assetId = synchronized(lock) {
                            if (queue.isEmpty()) null else queue.removeFirst()
                        } ?: break
                        activeCount.incrementAndGet()
                        synchronized(lock) { activeAssetIds += assetId }
                        executor.execute {
                            try {
                                loadAsset(assetId)
                            } finally {
                                activeCount.decrementAndGet()
                                synchronized(lock) {
                                    activeAssetIds.remove(assetId)
                                    lock.notifyAll()
                                }
                            }
                        }
                    }
                    synchronized(lock) {
                        if (!running.get()) return@execute
                        if (queue.isEmpty() && activeCount.get() == 0) return@execute
                        runCatching { lock.wait(100L) }
                    }
                }
            }
        }

        fun updateConcurrency(value: Int) {
            desiredConcurrency.set(value.coerceIn(1, ProxySettingsState.MAX_PREFETCH_CONCURRENCY))
            synchronized(lock) { lock.notifyAll() }
        }

        fun replaceQueue(assetIds: List<String>) {
            synchronized(lock) {
                queue.clear()
                queue.addAll(assetIds)
                lock.notifyAll()
            }
        }

        fun enqueueFront(assetId: String) {
            synchronized(lock) {
                if (assetId in activeAssetIds || assetId in queue) return
                queue.addFirst(assetId)
                lock.notifyAll()
            }
        }

        fun snapshotQueue(): List<String> = synchronized(lock) { queue.toList() }

        fun snapshotActiveAssetIds(): List<String> = synchronized(lock) { activeAssetIds.toList() }

        fun snapshotPlannedAssetIds(): List<String> = synchronized(lock) { (activeAssetIds.toList() + queue.toList()).distinct() }

        fun cancel() {
            running.set(false)
            synchronized(lock) { lock.notifyAll() }
        }
    }

    private class UpstreamFetchException(
        val statusCode: Int,
        val failure: String,
    ) : RuntimeException(failure)

    private class UnsupportedSessionSourceException(
        val statusCode: Int,
        override val message: String,
    ) : RuntimeException(message)

    private companion object {
        const val PREFETCH_SEGMENT_COUNT = 4
        const val SESSION_ASSET_MAX_RETRY_COUNT = 3
        const val SESSION_ASSET_RETRY_DELAY_MS = 100L
    }
}
