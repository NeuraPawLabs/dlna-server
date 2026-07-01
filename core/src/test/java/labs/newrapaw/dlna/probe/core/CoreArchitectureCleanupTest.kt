package labs.newrapaw.dlna.probe.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreArchitectureCleanupTest {
    @Test
    fun coreLocalHlsProxyDelegatesSessionConcurrencyHelpers() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionPrefetchController.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionCallTracker.kt").any(Files::exists))
        assertFalse(source.contains("private class SessionPrefetchController("))
        assertFalse(source.contains("private class SessionCallTracker"))
    }

    @Test
    fun coreLocalHlsProxyDelegatesRuntimeModelsAndExceptions() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyModels.kt").any(Files::exists))
        assertFalse(source.contains("private data class PreparedSessionPlayback("))
        assertFalse(source.contains("private class SessionAssetRuntime("))
        assertFalse(source.contains("private class UpstreamFetchException("))
        assertFalse(source.contains("private class UnsupportedSessionSourceException("))
        assertFalse(source.contains("private class SessionCancelledException("))
        assertTrue(source.contains("private val components = CoreLocalHlsProxyComponents("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesHttpResponseWriting() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyHttp.kt").any(Files::exists))
        assertFalse(source.contains("private fun writeText("))
        assertFalse(source.contains("private fun writeBytes("))
        assertFalse(source.contains("private fun writeBytesMeasured("))
        assertFalse(source.contains("private fun writeResponseHeaders("))
        assertTrue(componentsSource.contains("val requestHandler = CoreLocalHlsRequestHandler("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesMediaAndSessionHelperFunctions() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyMedia.kt").any(Files::exists))
        assertFalse(source.contains("private fun guessSegmentContentType("))
        assertFalse(source.contains("private fun looksLikeTransportStream("))
        assertFalse(source.contains("private fun isWrappedTransportStream("))
        assertFalse(source.contains("private fun buildSessionTrackId("))
        assertFalse(source.contains("private fun findSlotIndexForAsset("))
        assertFalse(source.contains("private fun buildSessionPrefetchQueue("))
        assertTrue(componentsSource.contains("val requestHandler = CoreLocalHlsRequestHandler("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesUpstreamRequestExecution() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamClient.kt").any(Files::exists))
        assertFalse(source.contains("private fun fetchUpstreamBytes("))
        assertFalse(source.contains("private fun fetchSegmentBytesMeasured("))
        assertFalse(source.contains("private fun fetchUpstreamBytesMeasured("))
        assertFalse(source.contains("private fun executeUpstreamCall("))
        assertFalse(source.contains("private fun executeCallMeasured("))
        assertFalse(source.contains("private fun raceUpstreamCalls("))
        assertFalse(source.contains("private fun executeRaceCall("))
        assertFalse(source.contains("private fun cancelRaceLosers("))
        assertFalse(source.contains("private fun Future<UpstreamRaceResult>.getOrFailure():"))
        assertFalse(source.contains("private fun directClient():"))
        assertFalse(source.contains("private fun proxyClient("))
        assertFalse(source.contains("private fun nanosToMillis("))
        assertTrue(componentsSource.contains("val upstreamClient = CoreLocalHlsUpstreamClient("))
    }

    @Test
    fun coreUpstreamClientDelegatesRaceStrategy() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamClient.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamRaceClient.kt").any(Files::exists))
        assertFalse(source.contains("private fun raceUpstreamCalls("))
        assertFalse(source.contains("private fun executeRaceCall("))
        assertFalse(source.contains("private fun cancelRaceLosers("))
        assertFalse(source.contains("private fun Future<UpstreamRaceResult>.getOrFailure():"))
        assertTrue(componentsSource.contains("val upstreamRaceClient = CoreLocalHlsUpstreamRaceClient("))
        assertTrue(componentsSource.contains("upstreamRaceClient = upstreamRaceClient"))
    }

    @Test
    fun coreUpstreamClientDelegatesCallExecutionAndClientSelection() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamClient.kt")
        val helperSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamCallExecutor.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsUpstreamCallExecutor.kt").any(Files::exists))
        assertFalse(source.contains("private fun executeUpstreamCall("))
        assertFalse(source.contains("private fun executeCallMeasured("))
        assertFalse(source.contains("private fun directClient():"))
        assertFalse(source.contains("private fun proxyClient("))
        assertFalse(source.contains("private fun nanosToMillis("))
        assertTrue(source.contains("private val callExecutor = CoreLocalHlsUpstreamCallExecutor("))
        assertTrue(helperSource.contains("internal class CoreLocalHlsUpstreamCallExecutor("))
        assertTrue(helperSource.contains("fun openStreamingCall("))
        assertTrue(helperSource.contains("fun executeUpstreamCall("))
        assertTrue(helperSource.contains("fun newDirectCall("))
        assertTrue(helperSource.contains("fun newProxyCall("))
        assertTrue(helperSource.contains("fun executeCallMeasured("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesPreparedSessionStateHelpers() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPreparedSession.kt").any(Files::exists))
        assertFalse(source.contains("private fun ensureStartupAssetsReady("))
        assertFalse(source.contains("private fun refreshPreparedSessionDiagnostics("))
        assertFalse(source.contains("private fun isPreparedAssetReady("))
        assertFalse(source.contains("private fun reprioritizePreparedSessionQueue("))
        assertFalse(source.contains("private fun noteRequestedPlaybackSlot("))
        assertTrue(componentsSource.contains("refreshPreparedSessionDiagnostics = diagnosticsCoordinator::refreshSnapshot"))
        assertTrue(componentsSource.contains("val requestHandler = CoreLocalHlsRequestHandler("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesSessionAssetLoading() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetLoader.kt").any(Files::exists))
        assertFalse(source.contains("private fun loadSessionAsset("))
        assertFalse(source.contains("private fun tryStreamSessionAsset("))
        assertFalse(source.contains("private fun waitForSessionAsset("))
        assertFalse(source.contains("private fun preparedLoadAssetById("))
        assertFalse(source.contains("private fun waitForPreparedAssetInFlight("))
        assertTrue(componentsSource.contains("val sessionAssetLoader = CoreLocalHlsSessionAssetLoader("))
        assertTrue(componentsSource.contains("sessionAssetLoader = sessionAssetLoader"))
    }

    @Test
    fun coreSessionAssetLoaderDelegatesStreamingResponsePath() {
        val loaderSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetLoader.kt")
        val assetRouteHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetRouteHandler.kt")
        val routeHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionRouteHandler.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetStreamer.kt").any(Files::exists))
        assertFalse(loaderSource.contains("fun tryStreamSessionAsset("))
        assertFalse(routeHandlerSource.contains("private val sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer"))
        assertFalse(routeHandlerSource.contains("sessionAssetStreamer.tryStreamSessionAsset("))
        assertTrue(assetRouteHandlerSource.contains("private val sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer"))
        assertTrue(assetRouteHandlerSource.contains("sessionAssetStreamer.tryStreamSessionAsset("))
        assertTrue(componentsSource.contains("val sessionAssetStreamer = CoreLocalHlsSessionAssetStreamer("))
        assertTrue(componentsSource.contains("sessionAssetStreamer = sessionAssetStreamer"))
    }

    @Test
    fun coreSessionAssetLoaderDelegatesRuntimeStateMachine() {
        val loaderSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetLoader.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetRuntimeCoordinator.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetRuntimeCoordinator.kt").any(Files::exists))
        assertFalse(loaderSource.contains("private fun waitForPreparedAssetInFlight("))
        assertFalse(loaderSource.contains("runtime.state = SessionAssetState.DOWNLOADING"))
        assertFalse(loaderSource.contains("runtime.state = SessionAssetState.READY"))
        assertFalse(loaderSource.contains("runtime.state = SessionAssetState.FAILED"))
        assertFalse(loaderSource.contains("runtime.state = SessionAssetState.NOT_STARTED"))
        assertTrue(loaderSource.contains("private val runtimeCoordinator = CoreLocalHlsSessionAssetRuntimeCoordinator("))
        assertTrue(runtimeSource.contains("internal class CoreLocalHlsSessionAssetRuntimeCoordinator("))
        assertTrue(runtimeSource.contains("fun acquire("))
        assertTrue(runtimeSource.contains("fun markCancelled("))
        assertTrue(runtimeSource.contains("fun markAttemptStarting("))
        assertTrue(runtimeSource.contains("fun markReady("))
        assertTrue(runtimeSource.contains("fun markFailure("))
        assertTrue(runtimeSource.contains("fun markInterruptedDuringBackoff("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesSessionPreparation() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionPreparer.kt").any(Files::exists))
        assertFalse(source.contains("private fun ensurePreparedSession("))
        assertTrue(componentsSource.contains("val sessionPreparer = CoreLocalHlsSessionPreparer("))
        assertTrue(componentsSource.contains("sessionPreparer = sessionPreparer"))
    }

    @Test
    fun coreSessionPreparerDelegatesManifestResolution() {
        val preparerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionPreparer.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionManifestResolver.kt").any(Files::exists))
        assertFalse(preparerSource.contains("parseSingleVariantMasterManifest("))
        assertFalse(preparerSource.contains("looksLikeMasterPlaylist("))
        assertFalse(preparerSource.contains("buildSessionTrackId("))
        assertFalse(preparerSource.contains("upstreamClient.fetchUpstreamBytes("))
        assertTrue(componentsSource.contains("val sessionManifestResolver = CoreLocalHlsSessionManifestResolver("))
        assertTrue(componentsSource.contains("sessionManifestResolver = sessionManifestResolver"))
    }

    @Test
    fun coreSessionPreparerDelegatesPreparedSessionAssembly() {
        val preparerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionPreparer.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPreparedSessionBuilder.kt").any(Files::exists))
        assertFalse(preparerSource.contains("PreparedSessionPlayback("))
        assertFalse(preparerSource.contains("PlaybackTelemetryBridge("))
        assertFalse(preparerSource.contains("SessionPrefetchController("))
        assertFalse(preparerSource.contains("sessionLocalServer.buildMasterManifest("))
        assertFalse(preparerSource.contains("sessionLocalServer.buildMediaPlaylist("))
        assertTrue(componentsSource.contains("val preparedSessionBuilder = CoreLocalHlsPreparedSessionBuilder("))
        assertTrue(componentsSource.contains("preparedSessionBuilder = preparedSessionBuilder"))
    }

    @Test
    fun coreLocalHlsProxyDelegatesHttpRequestHandling() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestHandler.kt").any(Files::exists))
        assertFalse(source.contains("private fun handle(socket: Socket)"))
        assertFalse(source.contains("private fun handleSessionRoute("))
        assertTrue(componentsSource.contains("handleSocket = requestHandler::handle"))
    }

    @Test
    fun coreRequestHandlerDelegatesProtocolParsingHelpers() {
        val requestHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestHandler.kt")
        val parsingSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestParsing.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestParsing.kt").any(Files::exists))
        assertFalse(requestHandlerSource.contains("private data class ParsedRequestLine("))
        assertFalse(requestHandlerSource.contains("private class MalformedRequestException("))
        assertFalse(requestHandlerSource.contains("private fun parseRequestLine("))
        assertFalse(requestHandlerSource.contains("private fun parseContentLength("))
        assertFalse(requestHandlerSource.contains("private data class RequestedByteRange("))
        assertFalse(requestHandlerSource.contains("private data class ResolvedByteRange("))
        assertFalse(requestHandlerSource.contains("private sealed class ParsedByteRangeHeader"))
        assertFalse(requestHandlerSource.contains("private fun parseByteRange("))
        assertTrue(parsingSource.contains("data class ParsedRequestLine("))
        assertTrue(parsingSource.contains("class MalformedRequestException("))
        assertTrue(parsingSource.contains("fun parseRequestLine("))
        assertTrue(parsingSource.contains("fun parseContentLength("))
        assertTrue(parsingSource.contains("data class RequestedByteRange("))
        assertTrue(parsingSource.contains("sealed class ParsedByteRangeHeader"))
        assertTrue(parsingSource.contains("fun parseByteRange("))
    }

    @Test
    fun coreRequestHandlerDelegatesSessionRouteDispatch() {
        val requestHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestHandler.kt")
        val routeHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionRouteHandler.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionRouteHandler.kt").any(Files::exists))
        assertFalse(requestHandlerSource.contains("private fun handleSessionRoute("))
        assertFalse(requestHandlerSource.contains("path == sessionLocalServer.masterManifestPath(sessionId)"))
        assertFalse(requestHandlerSource.contains("path.startsWith(\"/session/\$sessionId/asset/\")"))
        assertTrue(requestHandlerSource.contains("private val sessionRouteHandler = CoreLocalHlsSessionRouteHandler("))
        assertTrue(requestHandlerSource.contains("sessionRouteHandler.handle("))
        assertTrue(routeHandlerSource.contains("class CoreLocalHlsSessionRouteHandler("))
        assertTrue(routeHandlerSource.contains("fun handle("))
        assertTrue(routeHandlerSource.contains("path == sessionLocalServer.masterManifestPath(sessionId)"))
    }

    @Test
    fun coreSessionRouteHandlerDelegatesAssetRouteHandling() {
        val routeHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionRouteHandler.kt")
        val assetRouteHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetRouteHandler.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetRouteHandler.kt").any(Files::exists))
        assertFalse(routeHandlerSource.contains("path.startsWith(\"/session/\$sessionId/asset/\")"))
        assertFalse(routeHandlerSource.contains("sessionAssetStreamer.tryStreamSessionAsset("))
        assertFalse(routeHandlerSource.contains("sessionAssetLoader.waitForSessionAsset("))
        assertTrue(routeHandlerSource.contains("private val sessionAssetRouteHandler: CoreLocalHlsSessionAssetRouteHandler"))
        assertTrue(routeHandlerSource.contains("sessionAssetRouteHandler.handle("))
        assertTrue(assetRouteHandlerSource.contains("class CoreLocalHlsSessionAssetRouteHandler("))
        assertTrue(assetRouteHandlerSource.contains("path.startsWith(\"/session/\$sessionId/asset/\")"))
        assertTrue(assetRouteHandlerSource.contains("sessionAssetStreamer.tryStreamSessionAsset("))
        assertTrue(assetRouteHandlerSource.contains("sessionAssetLoader.waitForSessionAsset("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesPlaybackRuntimeState() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPlaybackRuntime.kt").any(Files::exists))
        assertFalse(source.contains("private var activeSessionShell: PlaybackSession?"))
        assertFalse(source.contains("private var activePreparedSession: PreparedSessionPlayback?"))
        assertFalse(source.contains("private fun clearPreviousPlaybackCacheForNewSession()"))
        assertFalse(source.contains("private fun cancelPreparedSession(prepared: PreparedSessionPlayback)"))
        assertTrue(source.contains("private val playbackRuntime = components.playbackRuntime"))
        assertTrue(componentsSource.contains("val playbackRuntime = CoreLocalHlsPlaybackRuntime("))
    }

    @Test
    fun coreLocalHlsPlaybackRuntimeUsesSingleStateModel() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPlaybackRuntime.kt")
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPlaybackState.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsPlaybackState.kt").any(Files::exists))
        assertFalse(source.contains("private var activeSessionShell: PlaybackSession?"))
        assertFalse(source.contains("private var activePreparedSession: PreparedSessionPlayback?"))
        assertFalse(source.contains("private var latestPlayerPositionMs: Long?"))
        assertFalse(source.contains("private var latestBufferedPositionMs: Long?"))
        assertTrue(source.contains("private var state = CoreLocalHlsPlaybackState()"))
        assertTrue(stateSource.contains("data class CoreLocalHlsPlaybackState("))
        assertTrue(stateSource.contains("fun toSnapshot(): CoreLocalHlsPlaybackSnapshot"))
    }

    @Test
    fun coreLocalHlsProxyDelegatesServerLifecycle() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyServer.kt").any(Files::exists))
        assertFalse(source.contains("private val running = AtomicBoolean("))
        assertFalse(source.contains("private var serverSocket: ServerSocket? = null"))
        assertFalse(source.contains("ServerSocket(0, 50, InetAddress.getByName(\"0.0.0.0\"))"))
        assertTrue(source.contains("private val proxyServer = components.proxyServer"))
        assertTrue(componentsSource.contains("private val executor: ExecutorService? = if (serveHttp)"))
        assertTrue(componentsSource.contains("val proxyServer = executor?.let"))
        assertFalse(componentsSource.contains("val proxyServer = CoreLocalHlsProxyServer("))
    }

    @Test
    fun coreLocalHlsProxyComponentsGracefullyAwaitExecutorShutdownBeforeForceClose() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(source.contains("shutdownGracefully("))
        assertTrue(source.contains("executor?.let(::shutdownGracefully)"))
        assertTrue(source.contains("upstreamRaceExecutor.let(::shutdownGracefully)"))
        assertTrue(source.contains("sessionPrefetchExecutor.let(::shutdownGracefully)"))
        assertTrue(source.contains("executor.shutdown()"))
        assertTrue(source.contains("executor.awaitTermination("))
        assertTrue(source.contains("executor.shutdownNow()"))
    }

    @Test
    fun coreLocalHlsProxyDelegatesSessionOpeningFlow() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionOpener.kt").any(Files::exists))
        assertFalse(source.contains("sessionManager.startSession("))
        assertFalse(source.contains("diagnosticsState.resetForPlayback("))
        assertFalse(source.contains("diagnosticsState.setSessionStatus(session.status.name)"))
        assertTrue(source.contains("private val sessionOpener = components.sessionOpener"))
        assertTrue(componentsSource.contains("val sessionOpener = CoreLocalHlsSessionOpener("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesDiagnosticsCoordination() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsDiagnosticsCoordinator.kt").any(Files::exists))
        assertFalse(source.contains("diagnosticsState.setPlaybackStatus("))
        assertFalse(source.contains("diagnosticsState.setLastError("))
        assertFalse(source.contains("diagnosticsState.updatePlayerTelemetry("))
        assertFalse(source.contains("diagnosticsState.setUpstreamSettings("))
        assertFalse(source.contains("private fun refreshDiagnosticsSnapshot()"))
        assertTrue(source.contains("private val diagnosticsCoordinator = components.diagnosticsCoordinator"))
        assertTrue(componentsSource.contains("val diagnosticsCoordinator = CoreLocalHlsDiagnosticsCoordinator("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesDerivedRuleEvaluation() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsRules.kt").any(Files::exists))
        assertFalse(source.contains("private fun diagnosticsInsights("))
        assertFalse(source.contains("private fun diagnosticsSeverity("))
        assertFalse(source.contains("private fun primaryBottleneck("))
        assertFalse(source.contains("private fun sessionAssetTimeoutInsight("))
        assertFalse(source.contains("private fun sessionAssetFailureInsight("))
        assertFalse(source.contains("private fun buildCurrentSlotStallReason("))
        assertFalse(source.contains("private fun blockedAssetLabel("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSnapshotModels() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val modelsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsModels.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsModels.kt").any(Files::exists))
        assertFalse(stateSource.contains("enum class DiagnosticsSeverity"))
        assertFalse(stateSource.contains("enum class SlotDiagnosticsState"))
        assertFalse(stateSource.contains("data class SegmentSample("))
        assertFalse(stateSource.contains("data class SlotDiagnosticsItem("))
        assertFalse(stateSource.contains("data class AssetDiagnosticsItem("))
        assertFalse(stateSource.contains("data class DiagnosticsInsight("))
        assertFalse(stateSource.contains("data class PlaybackDiagnosticsSnapshot("))
        assertTrue(modelsSource.contains("enum class DiagnosticsSeverity"))
        assertTrue(modelsSource.contains("data class PlaybackDiagnosticsSnapshot("))
        assertTrue(stateSource.contains("class PlaybackDiagnosticsState("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSegmentWindowTracking() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val trackerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentTracker.kt")
        val updaterSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentSnapshotUpdater.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentTracker.kt").any(Files::exists))
        assertFalse(stateSource.contains("private val recentSamples = ArrayDeque<SegmentSample>()"))
        assertFalse(stateSource.contains("if (recentSamples.size >= sampleLimit) recentSamples.removeFirst()"))
        assertFalse(stateSource.contains("val lastFive = nextSamples.takeLast(5)"))
        assertFalse(stateSource.contains("val directAverage = nextSamples.filter"))
        assertFalse(stateSource.contains("val proxyAverage = nextSamples.filter"))
        assertTrue(stateSource.contains("private val segmentTracker = PlaybackDiagnosticsSegmentTracker("))
        assertTrue(stateSource.contains("private val segmentSnapshotUpdater = PlaybackDiagnosticsSegmentSnapshotUpdater("))
        assertTrue(updaterSource.contains("segmentTracker.recordResult("))
        assertTrue(trackerSource.contains("internal class PlaybackDiagnosticsSegmentTracker("))
    }

    @Test
    fun playbackDiagnosticsSegmentTrackerUsesWindowStateModel() {
        val trackerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentTracker.kt")
        val windowSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentWindow.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentWindow.kt").any(Files::exists))
        assertFalse(trackerSource.contains("private val recentSamples = ArrayDeque<SegmentSample>()"))
        assertTrue(trackerSource.contains("private var window = PlaybackDiagnosticsSegmentWindow.empty("))
        assertTrue(trackerSource.contains("window = window.record("))
        assertTrue(windowSource.contains("data class PlaybackDiagnosticsSegmentWindow("))
        assertTrue(windowSource.contains("fun record("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSegmentSnapshotWrites() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val helperSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentSnapshotUpdater.kt"

        assertTrue(sourcePaths(helperSourcePath).any(Files::exists))
        assertTrue(stateSource.contains("private val segmentSnapshotUpdater = PlaybackDiagnosticsSegmentSnapshotUpdater("))
        assertFalse(stateSource.contains("snapshotRuntime.current().copy(lastRequestedSegment = url)"))
        assertFalse(stateSource.contains("if (!success && isIgnoredSegmentFailureReason(fallbackReason))"))
        assertFalse(stateSource.contains("lastSucceededSegment = if (success) url else snapshot.lastSucceededSegment"))
        assertFalse(stateSource.contains("lastFailedSegment = if (success) snapshot.lastFailedSegment else url"))
        assertFalse(stateSource.contains("lastFallbackReason = fallbackReason ?: snapshot.lastFallbackReason"))
        assertFalse(stateSource.contains("lastError = if (success) snapshot.lastError else fallbackReason ?: \"segment fetch failed\""))
        assertFalse(stateSource.contains("private fun isIgnoredSegmentFailureReason(reason: String?): Boolean"))
        val helperSource = sourceText(helperSourcePath)
        assertTrue(helperSource.contains("internal class PlaybackDiagnosticsSegmentSnapshotUpdater("))
        assertTrue(helperSource.contains("fun onSegmentRequested(url: String)"))
        assertTrue(helperSource.contains("fun onSegmentResult("))
        assertTrue(helperSource.contains("private fun isIgnoredSegmentFailureReason(reason: String?): Boolean"))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSessionSnapshotUpdates() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val trackerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionTracker.kt")
        val updaterSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionSnapshotUpdater.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionTracker.kt").any(Files::exists))
        assertFalse(stateSource.contains("startupGatePhase = phase"))
        assertFalse(stateSource.contains("slotStates = slotStates.sortedBy { it.slotIndex }"))
        assertFalse(stateSource.contains("assetDiagnostics = assetDiagnostics.sortedWith(compareBy<AssetDiagnosticsItem> { it.kind.name }.thenBy { it.assetId })"))
        assertFalse(stateSource.contains("currentLoadingAssetId = assetId"))
        assertTrue(stateSource.contains("private val sessionTracker = PlaybackDiagnosticsSessionTracker()"))
        assertTrue(stateSource.contains("private val sessionSnapshotUpdater = PlaybackDiagnosticsSessionSnapshotUpdater("))
        assertTrue(updaterSource.contains("sessionTracker.updateStartupGate("))
        assertTrue(updaterSource.contains("sessionTracker.updateSlotDiagnostics("))
        assertTrue(updaterSource.contains("sessionTracker.updateAssetDiagnostics("))
        assertTrue(updaterSource.contains("sessionTracker.updateCurrentLoadingAsset("))
        assertTrue(trackerSource.contains("internal class PlaybackDiagnosticsSessionTracker"))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSessionSnapshotWrites() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val helperSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionSnapshotUpdater.kt"

        assertTrue(sourcePaths(helperSourcePath).any(Files::exists))
        assertTrue(stateSource.contains("private val sessionSnapshotUpdater = PlaybackDiagnosticsSessionSnapshotUpdater("))
        assertFalse(stateSource.contains("snapshotRuntime.touch("))
        assertFalse(stateSource.contains("allowDerivedThrottle = false"))
        val helperSource = sourceText(helperSourcePath)
        assertTrue(helperSource.contains("internal class PlaybackDiagnosticsSessionSnapshotUpdater("))
        assertTrue(helperSource.contains("fun updateStartupGate("))
        assertTrue(helperSource.contains("fun updateSlotDiagnostics("))
        assertTrue(helperSource.contains("fun updateAssetDiagnostics("))
        assertTrue(helperSource.contains("fun updateAssetSummary("))
        assertTrue(helperSource.contains("fun clearSlotDiagnostics("))
        assertTrue(helperSource.contains("fun updateCurrentLoadingAsset("))
        assertTrue(helperSource.contains("fun clearPreparedSessionDiagnostics("))
        assertTrue(helperSource.contains("sessionTracker.updateStartupGate("))
        assertTrue(helperSource.contains("sessionTracker.clearPreparedSessionDiagnostics("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSnapshotCacheLifecycle() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntime.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntime.kt").any(Files::exists))
        assertFalse(stateSource.contains("private var cachedSnapshot: PlaybackDiagnosticsSnapshot? = null"))
        assertFalse(stateSource.contains("private var cachedSnapshotVersion = -1L"))
        assertFalse(stateSource.contains("private var snapshotDirty = true"))
        assertFalse(stateSource.contains("private var snapshotVersion = 0L"))
        assertFalse(stateSource.contains("private var lastDerivedAtMs: Long? = null"))
        assertFalse(stateSource.contains("private var canThrottleDerivedSnapshot = false"))
        assertFalse(stateSource.contains("private val staleThresholdMs = 5_000L"))
        assertFalse(stateSource.contains("private fun touch("))
        assertFalse(stateSource.contains("private fun setRawSnapshot("))
        assertFalse(stateSource.contains("private fun shouldThrottleDerivedSnapshot("))
        assertFalse(stateSource.contains("private fun cachedSnapshotForCurrentState("))
        assertTrue(stateSource.contains("private val snapshotRuntime = PlaybackDiagnosticsSnapshotRuntime("))
        assertTrue(runtimeSource.contains("internal class PlaybackDiagnosticsSnapshotRuntime("))
        assertTrue(runtimeSource.contains("private var state = PlaybackDiagnosticsSnapshotRuntimeState()"))
        assertTrue(runtimeSource.contains("fun current(): PlaybackDiagnosticsSnapshot"))
        assertTrue(runtimeSource.contains("fun playerIsLoading(): Boolean?"))
        assertTrue(runtimeSource.contains("fun reset("))
        assertTrue(runtimeSource.contains("fun touch("))
        assertTrue(runtimeSource.contains("fun snapshot(): PlaybackDiagnosticsSnapshot"))
    }

    @Test
    fun playbackDiagnosticsSnapshotRuntimeUsesSingleStateModel() {
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntime.kt")
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntimeState.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntimeState.kt").any(Files::exists))
        assertFalse(runtimeSource.contains("private var snapshot = PlaybackDiagnosticsSnapshot.empty()"))
        assertFalse(runtimeSource.contains("private var cachedSnapshot: PlaybackDiagnosticsSnapshot? = null"))
        assertFalse(runtimeSource.contains("private var cachedSnapshotVersion = -1L"))
        assertFalse(runtimeSource.contains("private var snapshotDirty = true"))
        assertFalse(runtimeSource.contains("private var snapshotVersion = 0L"))
        assertFalse(runtimeSource.contains("private var lastDerivedAtMs: Long? = null"))
        assertFalse(runtimeSource.contains("private var canThrottleDerivedSnapshot = false"))
        assertTrue(runtimeSource.contains("private var state = PlaybackDiagnosticsSnapshotRuntimeState()"))
        assertTrue(stateSource.contains("internal data class PlaybackDiagnosticsSnapshotRuntimeState("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesBasicSnapshotWrites() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val helperSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotUpdater.kt"

        assertTrue(sourcePaths(helperSourcePath).any(Files::exists))
        assertTrue(stateSource.contains("private val snapshotUpdater = PlaybackDiagnosticsSnapshotUpdater("))
        assertFalse(stateSource.contains("segmentTracker.reset()"))
        assertFalse(stateSource.contains("val currentTimeMs = nowMs()"))
        assertFalse(stateSource.contains("snapshot.lastError?.takeUnless(::isRecoverablePlaybackErrorMessage)"))
        assertFalse(stateSource.contains("message.isNullOrBlank() -> snapshot.playbackStatus"))
        assertFalse(stateSource.contains("activeProxy = settings.selectedProxy()?.displayUrl()"))
        assertFalse(stateSource.contains("playerPositionMs = positionMs"))
        assertFalse(stateSource.contains("pendingPrefetchCount = pendingPrefetchCount"))
        assertFalse(stateSource.contains("private fun isRecoverablePlaybackErrorMessage(message: String): Boolean ="))
        assertFalse(stateSource.contains("private fun PlaybackDiagnosticsStatus.clearsRecoverablePlaybackError(): Boolean ="))
        val helperSource = sourceText(helperSourcePath)
        assertTrue(helperSource.contains("internal class PlaybackDiagnosticsSnapshotUpdater("))
        assertTrue(helperSource.contains("fun resetForPlayback("))
        assertTrue(helperSource.contains("fun setPlaybackStatus("))
        assertTrue(helperSource.contains("fun setSessionStatus("))
        assertTrue(helperSource.contains("fun setLastError("))
        assertTrue(helperSource.contains("fun setUpstreamSettings("))
        assertTrue(helperSource.contains("fun updatePrefetchStats("))
        assertTrue(helperSource.contains("fun updatePlayerTelemetry("))
    }

    @Test
    fun playbackDiagnosticsJsonDelegatesNestedSectionBuilders() {
        val jsonSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJson.kt")
        val helperSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJsonSections.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJsonSections.kt").any(Files::exists))
        assertFalse(jsonSource.contains("appendJsonField(\"recentSegmentSamples\", buildJsonArray(snapshot.recentSegmentSamples)"))
        assertFalse(jsonSource.contains("appendJsonField(\"slotStates\", buildJsonArray(snapshot.slotStates)"))
        assertFalse(jsonSource.contains("appendJsonField(\"assetDiagnostics\", buildJsonArray(snapshot.assetDiagnostics)"))
        assertFalse(jsonSource.contains("appendJsonField(\"insights\", buildJsonArray(snapshot.insights)"))
        assertFalse(jsonSource.contains("appendJsonField(\"primaryBottleneck\", snapshot.primaryBottleneck?.let"))
        assertTrue(helperSource.contains("internal fun buildRecentSegmentSamplesJson("))
        assertTrue(helperSource.contains("internal fun buildSlotStatesJson("))
        assertTrue(helperSource.contains("internal fun buildAssetDiagnosticsJson("))
        assertTrue(helperSource.contains("internal fun buildInsightsJson("))
        assertTrue(helperSource.contains("internal fun buildPrimaryBottleneckJson("))
    }

    @Test
    fun coreLocalHlsProxyDelegatesCollaboratorAssembly() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt").any(Files::exists))
        assertFalse(source.contains("Executors.newCachedThreadPool()"))
        assertFalse(source.contains("SessionLocalServer()"))
        assertFalse(source.contains("ManifestPlanner()"))
        assertFalse(source.contains("SessionAssetStore(sessionAssetRootDir)"))
        assertFalse(source.contains("PlaybackDiagnosticsState()"))
        assertTrue(source.contains("private val components = CoreLocalHlsProxyComponents("))
    }

    @Test
    fun coreComponentsUseBoundedExecutorsInsteadOfCachedPools() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertFalse(source.contains("Executors.newCachedThreadPool()"))
        assertTrue(source.contains("ThreadPoolExecutor("))
        assertTrue(source.contains("LinkedBlockingQueue"))
    }

    @Test
    fun manifestPlannerDelegatesTrackPlanParsingHelpers() {
        val plannerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/ManifestPlanner.kt")
        val parserSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/session/ManifestTrackPlanParser.kt"

        assertTrue(sourcePaths(parserSourcePath).any(Files::exists))
        assertFalse(plannerSource.contains("private data class MediaEntry("))
        assertFalse(plannerSource.contains("private data class TrackPlan("))
        assertFalse(plannerSource.contains("private data class ParsedKey("))
        assertFalse(plannerSource.contains("private fun parseMediaEntries("))
        assertFalse(plannerSource.contains("private fun parseTrackPlan("))
        assertFalse(plannerSource.contains("private fun parseMapUri("))
        assertFalse(plannerSource.contains("private fun parseKey("))
        val parserSource = sourceText(parserSourcePath)
        assertTrue(parserSource.contains("internal data class MediaEntry("))
        assertTrue(parserSource.contains("internal data class TrackPlan("))
        assertTrue(parserSource.contains("internal data class ParsedKey("))
        assertTrue(parserSource.contains("internal fun parseMediaEntries("))
        assertTrue(parserSource.contains("internal fun parseTrackPlan("))
        assertTrue(parserSource.contains("internal fun parseMapUri("))
        assertTrue(parserSource.contains("internal fun parseKey("))
    }

    @Test
    fun sessionLocalServerDelegatesPlaylistRenderingHelpers() {
        val localServerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionLocalServer.kt")
        val helperSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/session/SessionPlaylistRendering.kt"

        assertTrue(sourcePaths(helperSourcePath).any(Files::exists))
        assertFalse(localServerSource.contains("private fun StringBuilder.appendMapTag("))
        assertFalse(localServerSource.contains("private fun StringBuilder.appendKeyTag("))
        assertFalse(localServerSource.contains("private fun prerequisiteIdsForSlot("))
        assertFalse(localServerSource.contains("private fun sessionAssetPath("))
        assertFalse(localServerSource.contains("private fun targetDurationSeconds("))
        assertFalse(localServerSource.contains("private fun escape("))
        assertFalse(localServerSource.contains("private fun assetPathSuffix("))
        assertFalse(localServerSource.contains("private fun inferSegmentExtension("))
        val helperSource = sourceText(helperSourcePath)
        assertTrue(helperSource.contains("internal fun StringBuilder.appendMapTag("))
        assertTrue(helperSource.contains("internal fun StringBuilder.appendKeyTag("))
        assertTrue(helperSource.contains("internal fun prerequisiteIdsForSlot("))
        assertTrue(helperSource.contains("internal fun sessionAssetPath("))
        assertTrue(helperSource.contains("internal fun targetDurationSeconds("))
        assertTrue(helperSource.contains("internal fun escape("))
        assertTrue(helperSource.contains("internal fun assetPathSuffix("))
        assertTrue(helperSource.contains("internal fun inferSegmentExtension("))
    }

    @Test
    fun sessionAssetStoreDelegatesSessionStateTracking() {
        val storeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStore.kt")
        val trackerSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreStateTracker.kt"
        val stateSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreTrackerState.kt"

        assertTrue(sourcePaths(trackerSourcePath).any(Files::exists))
        assertTrue(sourcePaths(stateSourcePath).any(Files::exists))
        assertFalse(storeSource.contains("private val lock = Any()"))
        assertFalse(storeSource.contains("private val sessionStates = mutableMapOf<String, SessionState>()"))
        assertFalse(storeSource.contains("private val closedSessionIds = linkedSetOf<String>()"))
        assertFalse(storeSource.contains("private fun sessionState("))
        assertFalse(storeSource.contains("private fun sessionStateOrNull("))
        assertFalse(storeSource.contains("private fun rememberClosedSessionLocked("))
        assertFalse(storeSource.contains("private class SessionState"))
        assertTrue(storeSource.contains("private val stateTracker = SessionAssetStoreStateTracker("))
        val trackerSource = sourceText(trackerSourcePath)
        val stateSource = sourceText(stateSourcePath)
        assertTrue(trackerSource.contains("internal class SessionAssetStoreStateTracker("))
        assertTrue(trackerSource.contains("private var state = SessionAssetStoreTrackerState()"))
        assertTrue(trackerSource.contains("fun writableSessionState("))
        assertTrue(trackerSource.contains("fun activeSessionStateOrNull("))
        assertTrue(trackerSource.contains("fun closeSession("))
        assertTrue(trackerSource.contains("fun closeAllSessions("))
        assertTrue(stateSource.contains("data class SessionAssetStoreTrackerState("))
        assertTrue(stateSource.contains("fun withTrackedSession("))
        assertTrue(stateSource.contains("fun withClosedSession("))
    }

    @Test
    fun sessionAssetStoreDelegatesDeletionPlanning() {
        val storeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStore.kt")
        val cleanupSourcePath = "src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreCleanup.kt"

        assertTrue(sourcePaths(cleanupSourcePath).any(Files::exists))
        assertFalse(storeSource.contains("rootDir.resolve(sessionId).deleteRecursively()"))
        assertFalse(storeSource.contains("trackedSessionIds.forEach { sessionId ->"))
        assertTrue(storeSource.contains("private val cleanup = SessionAssetStoreCleanup("))
        assertTrue(storeSource.contains("cleanup.deleteClosedSession(sessionId)"))
        assertTrue(storeSource.contains("cleanup.deleteClosedSessions("))
        val cleanupSource = sourceText(cleanupSourcePath)
        assertTrue(cleanupSource.contains("internal class SessionAssetStoreCleanup("))
        assertTrue(cleanupSource.contains("fun deleteClosedSession(sessionId: String)"))
        assertTrue(cleanupSource.contains("fun deleteClosedSessions(sessionIds: Set<String>)"))
        assertTrue(cleanupSource.contains("rootDir.resolve(sessionId).deleteRecursively()"))
    }

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("core").resolve(path),
        ).distinct()
}
