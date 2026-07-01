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
    fun coreLocalHlsProxyUsesSharedRequestFailurePolicy() {
        val proxySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val supportSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxySupport.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxySupport.kt").any(Files::exists))
        assertFalse(proxySource.contains("private fun shouldSuppressRequestFailureLog("))
        assertTrue(proxySource.contains("shouldSuppressRequestFailureLog = ::shouldSuppressRequestFailureLog"))
        assertTrue(supportSource.contains("fun shouldSuppressRequestFailureLog("))
    }

    @Test
    fun coreLocalHlsProxyComponentsUseSharedBoundedExecutorHelper() {
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")
        val supportSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxySupport.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxySupport.kt").any(Files::exists))
        assertFalse(componentsSource.contains("fun boundedExecutor("))
        assertTrue(componentsSource.contains("boundedExecutor("))
        assertTrue(supportSource.contains("fun boundedExecutor("))
        assertTrue(supportSource.contains("callerRunsOnSaturation"))
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
    fun playbackDiagnosticsUseConsolidatedFileLayout() {
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsModels.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsRules.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsTrackers.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJson.kt").any(Files::exists))

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJsonSections.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentSnapshotUpdater.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentTracker.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentWindow.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionSnapshotUpdater.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionTracker.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntime.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotRuntimeState.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSnapshotUpdater.kt").none(Files::exists))
    }

    @Test
    fun playbackDiagnosticsStateCoLocatesUpdaterHelpers() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")

        assertTrue(stateSource.contains("class PlaybackDiagnosticsState("))
        assertTrue(stateSource.contains("internal class PlaybackDiagnosticsSnapshotUpdater("))
        assertTrue(stateSource.contains("internal class PlaybackDiagnosticsSegmentSnapshotUpdater("))
        assertTrue(stateSource.contains("internal class PlaybackDiagnosticsSessionSnapshotUpdater("))
        assertTrue(stateSource.contains("private fun isRecoverablePlaybackErrorMessage(message: String): Boolean ="))
        assertTrue(stateSource.contains("private fun PlaybackDiagnosticsStatus.clearsRecoverablePlaybackError(): Boolean ="))
        assertTrue(stateSource.contains("private fun isIgnoredSegmentFailureReason(reason: String?): Boolean"))
    }

    @Test
    fun playbackDiagnosticsModelsCoLocateRuntimeState() {
        val modelsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsModels.kt")

        assertTrue(modelsSource.contains("enum class DiagnosticsSeverity"))
        assertTrue(modelsSource.contains("data class PlaybackDiagnosticsSnapshot("))
        assertTrue(modelsSource.contains("internal data class PlaybackDiagnosticsSnapshotRuntimeState("))
    }

    @Test
    fun playbackDiagnosticsSnapshotEmptyUsesModelDefaults() {
        val modelsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsModels.kt")

        assertTrue(modelsSource.contains("fun empty(): PlaybackDiagnosticsSnapshot = PlaybackDiagnosticsSnapshot("))
        assertTrue(modelsSource.contains("playbackStatus = PlaybackDiagnosticsStatus.IDLE"))
        assertTrue(modelsSource.contains("upstreamMode = UpstreamMode.PROXY_ONLY"))
        assertTrue(modelsSource.contains("prefetchConcurrency = ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY"))
        assertFalse(modelsSource.contains("sessionStatus = null"))
        assertFalse(modelsSource.contains("recentSegmentSamples = emptyList()"))
        assertFalse(modelsSource.contains("continuousReadySlotCount = 0"))
        assertFalse(modelsSource.contains("severity = DiagnosticsSeverity.OK"))
        assertFalse(modelsSource.contains("lastFallbackReason = null"))
    }

    @Test
    fun playbackDiagnosticsTrackersCoLocateRuntimeAndTrackerHelpers() {
        val trackersSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsTrackers.kt")

        assertTrue(trackersSource.contains("internal data class PlaybackDiagnosticsSegmentStats("))
        assertTrue(trackersSource.contains("internal class PlaybackDiagnosticsSegmentTracker("))
        assertTrue(trackersSource.contains("internal data class PlaybackDiagnosticsSegmentWindow("))
        assertTrue(trackersSource.contains("internal class PlaybackDiagnosticsSessionTracker"))
        assertTrue(trackersSource.contains("internal class PlaybackDiagnosticsSnapshotRuntime("))
    }

    @Test
    fun playbackDiagnosticsJsonCoLocatesSectionBuilders() {
        val jsonSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJson.kt")

        assertTrue(jsonSource.contains("fun buildPlaybackDiagnosticsJson(snapshot: PlaybackDiagnosticsSnapshot): String"))
        assertTrue(jsonSource.contains("internal fun buildRecentSegmentSamplesJson("))
        assertTrue(jsonSource.contains("internal fun buildSlotStatesJson("))
        assertTrue(jsonSource.contains("internal fun buildAssetDiagnosticsJson("))
        assertTrue(jsonSource.contains("internal fun buildInsightsJson("))
        assertTrue(jsonSource.contains("internal fun buildPrimaryBottleneckJson("))
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
        val supportSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxySupport.kt")

        assertFalse(source.contains("Executors.newCachedThreadPool()"))
        assertFalse(source.contains("ThreadPoolExecutor("))
        assertFalse(source.contains("LinkedBlockingQueue"))
        assertTrue(source.contains("boundedExecutor("))
        assertTrue(supportSource.contains("ThreadPoolExecutor("))
        assertTrue(supportSource.contains("LinkedBlockingQueue"))
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
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreStateTracker.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreTrackerState.kt").none(Files::exists))
        assertTrue(storeSource.contains("private val stateTracker = SessionAssetStoreStateTracker("))
        assertTrue(storeSource.contains("internal class SessionAssetStoreStateTracker("))
        assertTrue(storeSource.contains("private var state = SessionAssetStoreTrackerState()"))
        assertTrue(storeSource.contains("fun writableSessionState("))
        assertTrue(storeSource.contains("fun activeSessionStateOrNull("))
        assertTrue(storeSource.contains("fun closeSession("))
        assertTrue(storeSource.contains("fun closeAllSessions("))
        assertTrue(storeSource.contains("internal data class SessionAssetStoreTrackerState("))
        assertTrue(storeSource.contains("fun withTrackedSession("))
        assertTrue(storeSource.contains("fun withClosedSession("))
    }

    @Test
    fun sessionAssetStoreDelegatesDeletionPlanning() {
        val storeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStore.kt")
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStoreCleanup.kt").none(Files::exists))
        assertTrue(storeSource.contains("private val cleanup = SessionAssetStoreCleanup("))
        assertTrue(storeSource.contains("cleanup.deleteClosedSession(sessionId)"))
        assertTrue(storeSource.contains("cleanup.deleteClosedSessions("))
        assertTrue(storeSource.contains("internal class SessionAssetStoreCleanup("))
        assertTrue(storeSource.contains("fun deleteClosedSession(sessionId: String)"))
        assertTrue(storeSource.contains("fun deleteClosedSessions(sessionIds: Set<String>)"))
        assertTrue(storeSource.contains("rootDir.resolve(sessionId).deleteRecursively()"))
    }

    @Test
    fun sessionTimelineCoLocatesPlaybackTelemetryHelpers() {
        val timelineSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/session/SessionTimeline.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackTelemetryBridge.kt").none(Files::exists))
        assertTrue(timelineSource.contains("data class SessionTimeline("))
        assertTrue(timelineSource.contains("data class PlaybackTelemetrySnapshot("))
        assertTrue(timelineSource.contains("class PlaybackTelemetryBridge("))
        assertTrue(timelineSource.contains("fun snapshot("))
    }

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("core").resolve(path),
        ).distinct()
}
