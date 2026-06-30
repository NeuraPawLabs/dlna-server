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
        val requestHandlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsRequestHandler.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsSessionAssetStreamer.kt").any(Files::exists))
        assertFalse(loaderSource.contains("fun tryStreamSessionAsset("))
        assertTrue(requestHandlerSource.contains("private val sessionAssetStreamer: CoreLocalHlsSessionAssetStreamer"))
        assertTrue(requestHandlerSource.contains("sessionAssetStreamer.tryStreamSessionAsset("))
        assertTrue(componentsSource.contains("val sessionAssetStreamer = CoreLocalHlsSessionAssetStreamer("))
        assertTrue(componentsSource.contains("sessionAssetStreamer = sessionAssetStreamer"))
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
    fun coreLocalHlsProxyDelegatesServerLifecycle() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt")
        val componentsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyComponents.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyServer.kt").any(Files::exists))
        assertFalse(source.contains("private val running = AtomicBoolean("))
        assertFalse(source.contains("private var serverSocket: ServerSocket? = null"))
        assertFalse(source.contains("ServerSocket(0, 50, InetAddress.getByName(\"0.0.0.0\"))"))
        assertTrue(source.contains("private val proxyServer = components.proxyServer"))
        assertTrue(componentsSource.contains("val proxyServer = CoreLocalHlsProxyServer("))
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

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSegmentTracker.kt").any(Files::exists))
        assertFalse(stateSource.contains("private val recentSamples = ArrayDeque<SegmentSample>()"))
        assertFalse(stateSource.contains("if (recentSamples.size >= sampleLimit) recentSamples.removeFirst()"))
        assertFalse(stateSource.contains("val lastFive = nextSamples.takeLast(5)"))
        assertFalse(stateSource.contains("val directAverage = nextSamples.filter"))
        assertFalse(stateSource.contains("val proxyAverage = nextSamples.filter"))
        assertTrue(stateSource.contains("private val segmentTracker = PlaybackDiagnosticsSegmentTracker("))
        assertTrue(stateSource.contains("segmentTracker.recordResult("))
        assertTrue(trackerSource.contains("internal class PlaybackDiagnosticsSegmentTracker("))
    }

    @Test
    fun playbackDiagnosticsStateDelegatesSessionSnapshotUpdates() {
        val stateSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt")
        val trackerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionTracker.kt")

        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsSessionTracker.kt").any(Files::exists))
        assertFalse(stateSource.contains("startupGatePhase = phase"))
        assertFalse(stateSource.contains("slotStates = slotStates.sortedBy { it.slotIndex }"))
        assertFalse(stateSource.contains("assetDiagnostics = assetDiagnostics.sortedWith(compareBy<AssetDiagnosticsItem> { it.kind.name }.thenBy { it.assetId })"))
        assertFalse(stateSource.contains("currentLoadingAssetId = assetId"))
        assertTrue(stateSource.contains("private val sessionTracker = PlaybackDiagnosticsSessionTracker()"))
        assertTrue(stateSource.contains("sessionTracker.updateStartupGate("))
        assertTrue(stateSource.contains("sessionTracker.updateSlotDiagnostics("))
        assertTrue(stateSource.contains("sessionTracker.updateAssetDiagnostics("))
        assertTrue(stateSource.contains("sessionTracker.updateCurrentLoadingAsset("))
        assertTrue(trackerSource.contains("internal class PlaybackDiagnosticsSessionTracker"))
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

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("core").resolve(path),
        ).distinct()
}
