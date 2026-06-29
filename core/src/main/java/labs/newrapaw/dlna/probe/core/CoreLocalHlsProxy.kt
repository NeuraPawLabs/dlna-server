package labs.newrapaw.dlna.probe.core

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
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CancellationException

class CoreLocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    private val proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    private val sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val upstreamRaceExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sessionPrefetchExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sessionLocalServer = SessionLocalServer()
    private val manifestPlanner = ManifestPlanner()
    private val sessionAssetStore = SessionAssetStore(sessionAssetRootDir)
    private val diagnosticsState = PlaybackDiagnosticsState()
    private val preparedSessionLock = Any()
    private val sessionManager = PlaybackSessionManager(
        createSessionId = { "session-${System.currentTimeMillis()}" },
        cleanupSession = {
            sessionAssetStore.clearSession(it.sessionId)
            activePreparedSession
                ?.takeIf { prepared -> prepared.session.sessionId == it.sessionId }
                ?.let(::cancelPreparedSession)
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

    fun activeSessionInfo(): ActiveSessionInfo? {
        val prepared = activePreparedSession
        val session = prepared?.session ?: activeSessionShell ?: return null
        return ActiveSessionInfo(
            sessionId = session.sessionId,
            status = session.status,
            sourceUrl = session.sourceUrl,
            localManifestUrl = "$baseUrl${sessionLocalServer.masterManifestPath(session.sessionId)}",
            slotCount = prepared?.session?.timeline?.slots?.size ?: session.timeline.slots.size,
            assetCount = prepared?.assetsById?.size ?: session.timeline.assets.size,
            prepared = prepared != null,
            pendingPrefetchAssetIds = prepared?.prefetchController?.snapshotQueue() ?: emptyList(),
        )
    }

    fun diagnosticsSnapshot(): PlaybackDiagnosticsSnapshot = diagnosticsState.snapshot()

    fun updatePlaybackStatus(status: PlaybackDiagnosticsStatus) {
        diagnosticsState.setPlaybackStatus(status)
    }

    fun updatePlaybackError(message: String?) {
        diagnosticsState.setLastError(message)
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        latestPlayerPositionMs = positionMs
        latestBufferedPositionMs = bufferedPositionMs
        diagnosticsState.updatePlayerTelemetry(
            positionMs = positionMs,
            bufferedPositionMs = bufferedPositionMs,
            isLoading = isLoading,
        )
    }

    fun clearActiveSessionCache() {
        activeSessionShell?.let { sessionAssetStore.clearSession(it.sessionId) }
        activePreparedSession?.let(::cancelPreparedSession)
        activePreparedSession?.session?.let { sessionAssetStore.clearSession(it.sessionId) }
        activePreparedSession = null
        latestPlayerPositionMs = null
        latestBufferedPositionMs = null
        refreshDiagnosticsSnapshot()
    }

    fun updatePrefetchConcurrency(prefetchConcurrency: Int) {
        activePreparedSession?.prefetchController?.updateConcurrency(prefetchConcurrency)
        diagnosticsState.setUpstreamSettings(proxySettingsStore.load())
        refreshDiagnosticsSnapshot()
    }

    fun start() {
        if (running.get()) return
        sessionAssetStore.clearAllSessions()
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
        running.set(true)
        executor.execute {
            safeLog("Core proxy listening at $baseUrl")
            while (running.get()) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute { handle(socket) }
            }
        }
    }

    fun openSession(sourceUrl: String): ActiveSessionInfo {
        val session = sessionManager.startSession(
            sourceUrl = sourceUrl,
            entryManifestUrl = sourceUrl,
            localRootDir = "session-${System.currentTimeMillis()}",
        )
        clearPreviousPlaybackCacheForNewSession()
        activeSessionShell = session
        val localManifestUrl = "$baseUrl${sessionLocalServer.masterManifestPath(session.sessionId)}"
        diagnosticsState.resetForPlayback(
            sourceUrl = session.sourceUrl,
            localProxyUrl = localManifestUrl,
            settings = proxySettingsStore.load(),
        )
        diagnosticsState.setSessionStatus(session.status.name)
        return requireNotNull(activeSessionInfo())
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        activePreparedSession?.let(::cancelPreparedSession)
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
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine().orEmpty()
                    if (line.isEmpty()) break
                    val name = line.substringBefore(":", missingDelimiterValue = "").trim()
                    val value = line.substringAfter(":", missingDelimiterValue = "").trim()
                    if (name.isNotEmpty()) {
                        headers[name.lowercase()] = value
                    }
                }
                when {
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

    private fun clearPreviousPlaybackCacheForNewSession() {
        activeSessionShell?.let { sessionAssetStore.clearSession(it.sessionId) }
        activeSessionShell = null
        activePreparedSession?.let(::cancelPreparedSession)
        activePreparedSession?.session?.let { sessionAssetStore.clearSession(it.sessionId) }
        activePreparedSession = null
        latestPlayerPositionMs = null
        latestBufferedPositionMs = null
    }

    private fun handleSessionRoute(method: String, path: String, output: OutputStream) {
        val session = activeSessionShell
        if (session == null) {
            writeText(output, 404, "text/plain", "No active session", method)
            return
        }
        val sessionId = path.substringAfter("/session/").substringBefore("/")
        if (sessionId != session.sessionId) {
            writeText(output, 404, "text/plain", "Unknown session", method)
            return
        }
        val prepared = ensurePreparedSession(session)
        if (prepared == null) {
            writeText(output, 502, "text/plain", "Failed to prepare session", method)
            return
        }
        if (prepared.preparationFailure != null) {
            writeText(output, prepared.preparationFailure.statusCode, "text/plain", prepared.preparationFailure.message, method)
            return
        }
        when {
            path == sessionLocalServer.masterManifestPath(sessionId) ->
                writeText(output, 200, "application/vnd.apple.mpegurl", prepared.masterManifest, method)
            path == sessionLocalServer.videoPlaylistPath(sessionId) ->
                writeText(output, 200, "application/vnd.apple.mpegurl", prepared.videoPlaylist, method)
            path.startsWith("/session/$sessionId/audio/") -> {
                val trackId = path.substringAfterLast("/").substringBeforeLast(".m3u8")
                val playlist = prepared.audioPlaylists[trackId]
                if (playlist == null) writeText(output, 404, "text/plain", "Unknown audio track", method)
                else writeText(output, 200, "application/vnd.apple.mpegurl", playlist, method)
            }
            path.startsWith("/session/$sessionId/subtitle/") -> {
                val trackId = path.substringAfterLast("/").substringBeforeLast(".m3u8")
                val playlist = prepared.subtitlePlaylists[trackId]
                if (playlist == null) writeText(output, 404, "text/plain", "Unknown subtitle track", method)
                else writeText(output, 200, "application/vnd.apple.mpegurl", playlist, method)
            }
            path.startsWith("/session/$sessionId/asset/") -> {
                val assetId = path.substringAfter("/asset/").substringBeforeLast(".")
                val asset = prepared.assetsById[assetId]
                if (asset == null) {
                    writeText(output, 404, "text/plain", "Unknown asset", method)
                    return
                }
                val slotIndex = findSlotIndexForAsset(prepared.session.timeline.slots, asset.assetId)
                slotIndex?.let { requestedSlotIndex ->
                    noteRequestedPlaybackSlot(prepared, requestedSlotIndex)
                }
                if (method.equals("GET", ignoreCase = true) && tryStreamSessionAsset(output, prepared, asset)) {
                    return
                }
                val bytes = waitForSessionAsset(prepared, asset)
                if (bytes == null) {
                    val runtime = prepared.assetRuntime[asset.assetId]
                    if (runtime?.state == SessionAssetState.FAILED) {
                        diagnosticsState.setLastError("Session asset failed: ${asset.assetId}")
                        writeText(output, 502, "text/plain", "Session asset failed: ${asset.assetId}", method)
                    } else {
                        diagnosticsState.setLastError("Session asset wait timed out: ${asset.assetId}")
                        writeText(output, 504, "text/plain", "Session asset wait timed out: ${asset.assetId}", method)
                    }
                    return
                }
                refreshPreparedSessionDiagnostics()
                writeBytesMeasured(output, 200, guessSegmentContentType(asset.url), bytes, method)
            }
            else -> writeText(output, 404, "text/plain", "Unknown session route", method)
        }
    }

    private fun ensurePreparedSession(session: PlaybackSession): PreparedSessionPlayback? {
        activePreparedSession?.takeIf { it.session.sessionId == session.sessionId }?.let { return it }
        return synchronized(preparedSessionLock) {
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
                    callTracker = SessionCallTracker(),
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
                        callTracker = prepared.callTracker,
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
                        callTracker = SessionCallTracker(),
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
            callTracker = prepared.callTracker,
        )
    }

    private fun tryStreamSessionAsset(
        output: OutputStream,
        prepared: PreparedSessionPlayback,
        asset: SessionAsset,
    ): Boolean {
        if (isWrappedTransportStream(asset.url)) return false
        val settings = proxySettingsStore.load()
        if (settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY) return false
        val runtime = prepared.assetRuntime.getOrPut(asset.assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.resolveAsset(prepared.session.sessionId, asset.assetId)
            if (existing.exists() || runtime.state == SessionAssetState.DOWNLOADING) {
                return false
            }
            runtime.retryCount += 1
            runtime.state = SessionAssetState.DOWNLOADING
            runtime.lastError = null
        }
        val proxy = settings.selectedProxy()
        val source = if (proxy == null) "direct" else "proxy"
        val client = if (proxy == null) directClient() else proxyClient(proxy)
        val call = client.newCall(Request.Builder().url(asset.url).build())
        prepared.callTracker.register(call)
        var responseStarted = false
        val startedAt = System.nanoTime()
        return try {
            diagnosticsState.updateCurrentLoadingAsset(
                assetId = asset.assetId,
                kind = asset.kind.name,
                trackId = asset.trackId,
                source = source,
            )
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val failure = formatUpstreamFailure(
                        statusCode = response.code,
                        statusMessage = response.message,
                        body = response.body?.string().orEmpty(),
                    )
                    throw UpstreamFetchException(response.code, failure)
                }
                val body = response.body ?: throw UpstreamFetchException(502, "empty upstream body")
                val stream = body.byteStream()
                writeResponseHeaders(
                    output = output,
                    status = 200,
                    contentType = guessSegmentContentType(asset.url),
                    contentLength = body.contentLength().takeIf { it >= 0L },
                )
                responseStarted = true
                val buffer = ByteArray(8 * 1024)
                val capture = java.io.ByteArrayOutputStream()
                var firstByteAt: Long? = null
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (firstByteAt == null) firstByteAt = System.nanoTime()
                    output.write(buffer, 0, count)
                    output.flush()
                    capture.write(buffer, 0, count)
                }
                val bytes = if (looksLikeTransportStream(asset.url)) {
                    stripPngWrapperFromSegment(capture.toByteArray())
                } else {
                    capture.toByteArray()
                }
                val file = sessionAssetStore.writeAsset(prepared.session.sessionId, asset.assetId, bytes)
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = file
                    runtime.localSizeBytes = bytes.size.toLong()
                    runtime.lastElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.lastSource = source
                    runtime.upstreamFirstByteMs = firstByteAt?.let { TimeUnit.NANOSECONDS.toMillis(it - startedAt) } ?: 0L
                    runtime.upstreamCompleteMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.diskWriteMs = 0L
                    runtime.lastError = null
                    runtime.lock.notifyAll()
                }
                diagnosticsState.onSegmentResult(asset.url, source, runtime.upstreamCompleteMs ?: 0L, success = true)
                diagnosticsState.updateCurrentLoadingAsset(
                    assetId = null,
                    kind = null,
                    trackId = null,
                    source = null,
                )
                refreshPreparedSessionDiagnostics()
                true
            }
        } catch (error: Throwable) {
            synchronized(runtime.lock) {
                runtime.lastElapsedMs = null
                runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
                runtime.lastSource = source
                runtime.state = if (prepared.callTracker.isCancelled()) SessionAssetState.NOT_STARTED else SessionAssetState.FAILED
                runtime.lock.notifyAll()
            }
            diagnosticsState.onSegmentResult(
                url = asset.url,
                source = source,
                elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                success = false,
                fallbackReason = "${error::class.java.simpleName}: ${error.message}",
            )
            diagnosticsState.updateCurrentLoadingAsset(
                assetId = null,
                kind = null,
                trackId = null,
                source = null,
            )
            refreshPreparedSessionDiagnostics()
            if (!responseStarted) {
                if (error is UpstreamFetchException) {
                    writeText(output, error.statusCode, "text/plain", error.message.orEmpty())
                } else {
                    writeText(output, 502, "text/plain", "${error::class.java.simpleName}: ${error.message}")
                }
            }
            true
        } finally {
            prepared.callTracker.complete(call)
        }
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
        callTracker: SessionCallTracker,
    ): ByteArray {
        val asset = assetsById.getValue(assetId)
        val runtime = assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }
        synchronized(runtime.lock) {
            val existing = sessionAssetStore.resolveAsset(sessionId, assetId)
            if (existing.exists()) {
                runtime.state = SessionAssetState.READY
                runtime.localFile = existing
                runtime.localSizeBytes = existing.length()
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
            if (callTracker.isCancelled()) {
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.NOT_STARTED
                    runtime.lastError = "cancelled"
                    runtime.lock.notifyAll()
                }
                throw SessionCancelledException("session cancelled while loading asset: $assetId")
            }
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
                val fetchResult = fetchSegmentBytesMeasured(asset.url, callTracker)
                val writeStartedAt = System.nanoTime()
                val file = sessionAssetStore.writeAsset(sessionId, assetId, fetchResult.bytes)
                val diskWriteMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - writeStartedAt)
                synchronized(runtime.lock) {
                    runtime.state = SessionAssetState.READY
                    runtime.localFile = file
                    runtime.localSizeBytes = fetchResult.bytes.size.toLong()
                    runtime.lastElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                    runtime.lastSource = fetchResult.source
                    runtime.upstreamFirstByteMs = fetchResult.firstByteMs
                    runtime.upstreamCompleteMs = fetchResult.completeMs
                    runtime.diskWriteMs = diskWriteMs.coerceAtLeast(0L)
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
                return fetchResult.bytes
            }.onFailure { error ->
                lastError = error
                synchronized(runtime.lock) {
                    runtime.lastElapsedMs = null
                    runtime.lastError = "${error::class.java.simpleName}: ${error.message}"
                    runtime.lastSource = "session-local"
                    if (callTracker.isCancelled()) {
                        runtime.state = SessionAssetState.NOT_STARTED
                    } else if (attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                        runtime.state = SessionAssetState.FAILED
                    }
                    runtime.lock.notifyAll()
                }
                if (callTracker.isCancelled() || attempt == SESSION_ASSET_MAX_RETRY_COUNT - 1) {
                    diagnosticsState.updateCurrentLoadingAsset(
                        assetId = null,
                        kind = null,
                        trackId = null,
                        source = null,
                    )
                }
                refreshPreparedSessionDiagnostics()
                if (callTracker.isCancelled()) {
                    throw SessionCancelledException("session cancelled while loading asset: $assetId")
                }
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
                    runtime.localSizeBytes = existing.length()
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

    private fun fetchUpstreamBytes(upstreamUrl: String): ByteArray =
        fetchUpstreamBytesMeasured(upstreamUrl).bytes

    private fun fetchSegmentBytesMeasured(upstreamUrl: String, callTracker: SessionCallTracker? = null): UpstreamFetchResult {
        val result = fetchUpstreamBytesMeasured(upstreamUrl, callTracker)
        return if (looksLikeTransportStream(upstreamUrl)) {
            result.copy(bytes = stripPngWrapperFromSegment(result.bytes))
        } else {
            result
        }
    }

    private fun fetchUpstreamBytesMeasured(upstreamUrl: String, callTracker: SessionCallTracker? = null): UpstreamFetchResult {
        val settings = proxySettingsStore.load()
        diagnosticsState.setUpstreamSettings(settings)
        val proxy = settings.selectedProxy()
        return when {
            proxy == null -> executeUpstreamCall(upstreamUrl, directClient(), "direct", callTracker)
            settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY -> raceUpstreamCalls(upstreamUrl, proxy, callTracker)
            else -> {
                safeLog("Using proxy: ${proxy.displayUrl()}")
                executeUpstreamCall(upstreamUrl, proxyClient(proxy), "proxy", callTracker)
            }
        }
    }

    private fun executeUpstreamCall(
        upstreamUrl: String,
        client: OkHttpClient,
        source: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val call = client.newCall(Request.Builder().url(upstreamUrl).build())
        callTracker?.register(call)
        val startedAt = System.nanoTime()
        return runCatching { executeCallMeasured(call, source) }
            .onSuccess { result ->
                diagnosticsState.onSegmentResult(upstreamUrl, source, result.completeMs, success = true)
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
            .also {
                callTracker?.complete(call)
            }
    }

    private fun executeCallMeasured(call: Call, source: String): UpstreamFetchResult {
        val startedAt = System.nanoTime()
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
            val stream = it.body?.byteStream()
            if (stream == null) {
                return UpstreamFetchResult(
                    source = source,
                    bytes = ByteArray(0),
                    firstByteMs = 0L,
                    completeMs = nanosToMillis(startedAt),
                )
            }
            val buffer = ByteArray(8 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var firstByteAt: Long? = null
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (firstByteAt == null) {
                    firstByteAt = System.nanoTime()
                }
                output.write(buffer, 0, count)
            }
            return UpstreamFetchResult(
                source = source,
                bytes = output.toByteArray(),
                firstByteMs = firstByteAt?.let { TimeUnit.NANOSECONDS.toMillis(it - startedAt) } ?: 0L,
                completeMs = nanosToMillis(startedAt),
            )
        }
    }

    private fun raceUpstreamCalls(
        upstreamUrl: String,
        proxy: ProxyConfig,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val directCall = directClient().newCall(Request.Builder().url(upstreamUrl).build())
        val proxyCall = proxyClient(proxy).newCall(Request.Builder().url(upstreamUrl).build())
        callTracker?.register(directCall)
        callTracker?.register(proxyCall)
        val completion = ExecutorCompletionService<UpstreamRaceResult>(upstreamRaceExecutor)
        val futures = listOf(
            completion.submit(Callable { executeRaceCall("direct", directCall) }),
            completion.submit(Callable { executeRaceCall("proxy", proxyCall) }),
        )
        val failures = mutableListOf<String>()

        repeat(futures.size) {
            val result = completion.take().getOrFailure()
            if (result.fetchResult != null) {
                diagnosticsState.onSegmentResult(upstreamUrl, result.source, result.fetchResult.completeMs, success = true)
                cancelRaceLosers(futures, directCall, proxyCall)
                callTracker?.complete(directCall)
                callTracker?.complete(proxyCall)
                refreshDiagnosticsSnapshot()
                return result.fetchResult
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
        callTracker?.complete(directCall)
        callTracker?.complete(proxyCall)
        refreshDiagnosticsSnapshot()
        throw UpstreamFetchException(502, failures.joinToString("; "))
    }

    private fun executeRaceCall(source: String, call: Call): UpstreamRaceResult =
        run {
            val startedAt = System.nanoTime()
            runCatching {
                UpstreamRaceResult(
                    source = source,
                    fetchResult = executeCallMeasured(call, source),
                    failure = null,
                    elapsedMs = nanosToMillis(startedAt),
                )
            }.getOrElse {
                UpstreamRaceResult(
                    source = source,
                    fetchResult = null,
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
                fetchResult = null,
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

    private fun cancelPreparedSession(prepared: PreparedSessionPlayback) {
        prepared.prefetchController.cancel()
        prepared.callTracker.cancel()
        if (activePreparedSession?.session?.sessionId == prepared.session.sessionId) {
            activePreparedSession = null
        }
    }

    private fun refreshPreparedSessionDiagnostics() {
        val prepared = activePreparedSession ?: return
        val settings = proxySettingsStore.load()
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
        diagnosticsState.updatePrefetchStats(
            prefetchConcurrency = settings.prefetchConcurrency,
            pendingPrefetchCount = prepared.prefetchController.snapshotQueue().size,
            inFlightCount = prepared.prefetchController.snapshotActiveAssetIds().size,
        )
        val readyAssets = prepared.assetRuntime.values.count { it.state == SessionAssetState.READY }
        val readyBytes = prepared.assetRuntime.values.sumOf { it.localSizeBytes ?: 0L }
        diagnosticsState.updateAssetSummary(
            readyAssetCount = readyAssets,
            totalAssetCount = prepared.assetsById.size,
            readyBytes = readyBytes,
        )
        diagnosticsState.clearSlotDiagnostics(
            currentPlaybackSlotIndex = currentSlotIndex,
            bufferedSlotIndex = bufferedSlotIndex,
        )
        diagnosticsState.setSessionStatus(
            when {
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
        if (!file.exists()) return false
        prepared.assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }.apply {
            state = SessionAssetState.READY
            localFile = file
            localSizeBytes = file.length()
        }
        return true
    }

    private fun reprioritizePreparedSessionQueue(
        prepared: PreparedSessionPlayback,
        playHeadSlotIndex: Int,
    ) {
        val scheduledAssetIds = prepared.assetRuntime
            .filterValues {
                it.state == SessionAssetState.READY ||
                    it.state == SessionAssetState.DOWNLOADING
            }
            .keys
        val queue = SessionDownloader.planPlaybackQueue(
            slots = prepared.session.timeline.slots,
            assetsById = prepared.assetsById,
            playHeadSlotIndex = playHeadSlotIndex,
            readyAssetIds = scheduledAssetIds,
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

    private fun writeText(output: OutputStream, status: Int, contentType: String, body: String, method: String = "GET") {
        writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8), method)
    }

    private fun writeBytes(
        output: OutputStream,
        status: Int,
        contentType: String,
        body: ByteArray,
        method: String = "GET",
    ) {
        writeBytesMeasured(output, status, contentType, body, method)
    }

    private fun writeBytesMeasured(
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

    private fun writeResponseHeaders(
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
            path.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
            else -> "video/mp2t"
        }
    }

    private fun looksLikeTransportStream(upstreamUrl: String): Boolean {
        val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
        return path.endsWith(".ts", ignoreCase = true) || path.endsWith(".png", ignoreCase = true)
    }

    private fun isWrappedTransportStream(upstreamUrl: String): Boolean {
        val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
        return path.endsWith(".png", ignoreCase = true)
    }

    private fun nanosToMillis(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

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
        val fetchResult: UpstreamFetchResult?,
        val failure: String?,
        val elapsedMs: Long,
    )

    private data class UpstreamFetchResult(
        val source: String,
        val bytes: ByteArray,
        val firstByteMs: Long,
        val completeMs: Long,
    )

    private data class ResponseWriteTiming(
        val firstByteMs: Long,
        val completeMs: Long,
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
        val callTracker: SessionCallTracker,
        val prefetchController: SessionPrefetchController,
        val preparationFailure: UnsupportedSessionSourceException?,
    )

    private class SessionAssetRuntime(
        var state: SessionAssetState = SessionAssetState.NOT_STARTED,
        var localFile: File? = null,
        var localSizeBytes: Long? = null,
        var lastError: String? = null,
        var lastElapsedMs: Long? = null,
        var lastSource: String? = null,
        var upstreamFirstByteMs: Long? = null,
        var upstreamCompleteMs: Long? = null,
        var diskWriteMs: Long? = null,
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

    private class SessionCallTracker {
        private val cancelled = AtomicBoolean(false)
        private val lock = Object()
        private val activeCalls = linkedSetOf<Call>()

        fun register(call: Call) {
            if (cancelled.get()) {
                call.cancel()
                throw CancellationException("session already cancelled")
            }
            synchronized(lock) {
                if (cancelled.get()) {
                    call.cancel()
                    throw CancellationException("session already cancelled")
                }
                activeCalls += call
            }
        }

        fun complete(call: Call) {
            synchronized(lock) {
                activeCalls.remove(call)
            }
        }

        fun cancel() {
            cancelled.set(true)
            val calls = synchronized(lock) {
                activeCalls.toList().also { activeCalls.clear() }
            }
            calls.forEach { it.cancel() }
        }

        fun isCancelled(): Boolean = cancelled.get()
    }

    private class UpstreamFetchException(
        val statusCode: Int,
        val failure: String,
    ) : RuntimeException(failure)

    private class UnsupportedSessionSourceException(
        val statusCode: Int,
        override val message: String,
    ) : RuntimeException(message)

    private class SessionCancelledException(
        override val message: String,
    ) : CancellationException(message)

    private companion object {
        const val SESSION_ASSET_MAX_RETRY_COUNT = 3
        const val SESSION_ASSET_RETRY_DELAY_MS = 100L
    }
}
