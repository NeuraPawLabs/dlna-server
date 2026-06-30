package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureCleanupTest {
    @Test
    fun localHlsProxyDoesNotRetainLegacySessionEngine() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")

        assertFalse(source.contains("private fun ensurePreparedSession("))
        assertFalse(source.contains("private fun preparedLoadAssetById("))
        assertFalse(source.contains("private class SessionPrefetchController("))
        assertFalse(source.contains("private fun executeUpstreamCall("))
        assertFalse(source.contains("private fun raceUpstreamCalls("))
    }

    @Test
    fun localHlsProxyDelegatesServerLifecycle() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")

        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyServer.kt").any(Files::exists))
        assertFalse(source.contains("private val running = AtomicBoolean("))
        assertFalse(source.contains("private var serverSocket: ServerSocket? = null"))
        assertFalse(source.contains("ServerSocket(0, 50, InetAddress.getByName(\"0.0.0.0\"))"))
        assertTrue(source.contains("private val proxyServer = LocalHlsProxyServer("))
    }

    @Test
    fun localHlsProxyDelegatesRequestRouting() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")

        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyRequestHandler.kt").any(Files::exists))
        assertFalse(source.contains("private fun handle(socket: Socket)"))
        assertFalse(source.contains("private fun handleSessionRoute(method: String, path: String, output: OutputStream)"))
        assertTrue(source.contains("private val requestHandler = LocalHlsProxyRequestHandler("))
        assertTrue(source.contains("handleSocket = requestHandler::handle"))
    }

    @Test
    fun localHlsProxyDelegatesAdminAndDlnaRouteHandling() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/AdminHttpRoutes.kt").any(Files::exists))
        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyDlnaRoutes.kt").any(Files::exists))
        assertFalse(source.contains("private fun handlePage("))
        assertFalse(source.contains("private fun handleLogs("))
        assertFalse(source.contains("private fun handleDiagnostics("))
        assertFalse(source.contains("private fun handleDiagnosticsPanel("))
        assertFalse(source.contains("private fun handlePlayRequest("))
        assertFalse(source.contains("private fun handleStopRequest("))
        assertFalse(source.contains("private fun handleUpdateRequest("))
        assertFalse(source.contains("private fun handleProxyAddRequest("))
        assertFalse(source.contains("private fun handleProxySelectRequest("))
        assertFalse(source.contains("private fun handleProxyDeleteRequest("))
        assertFalse(source.contains("private fun handlePrefetchConfigRequest("))
        assertFalse(source.contains("private fun handleCacheClearRequest("))
        assertFalse(source.contains("private fun handleDeviceDescription("))
        assertFalse(source.contains("private fun handleDlnaControl("))
        assertFalse(source.contains("private fun handleEventSubscribe("))
        assertFalse(source.contains("private fun handleEventUnsubscribe("))
        assertTrue(source.contains("private val adminRoutes = AdminHttpRoutes("))
        assertTrue(source.contains("private val dlnaRoutes = LocalHlsProxyDlnaRoutes("))
    }

    @Test
    fun localHlsProxyDelegatesHttpResponseFormatting() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")

        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyHttp.kt").any(Files::exists))
        assertFalse(source.contains("private fun writeText("))
        assertFalse(source.contains("private fun writeJson("))
        assertFalse(source.contains("private fun buildDiagnosticsJson("))
        assertFalse(source.contains("private fun writeResponse("))
        assertFalse(source.contains("private fun writeBytes("))
        assertFalse(source.contains("private fun writeResponseHeaders("))
    }

    @Test
    fun appProxyHttpDelegatesDiagnosticsJsonSerializationToCore() {
        val httpSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyHttp.kt")
        val routesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/AdminHttpRoutes.kt")

        assertTrue(coreSourcePaths("src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnosticsJson.kt").any(Files::exists))
        assertFalse(httpSource.contains("internal fun buildDiagnosticsJson("))
        assertFalse(httpSource.contains("appendJsonField(\"playbackStatus\""))
        assertFalse(httpSource.contains("appendJsonField(\"primaryBottleneck\""))
        assertTrue(routesSource.contains("buildPlaybackDiagnosticsJson("))
    }

    @Test
    fun localHlsProxyRequestHandlerUsesRouteCollaboratorsInsteadOfCallbackBag() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyRequestHandler.kt")

        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxySessionRelay.kt").any(Files::exists))
        assertFalse(source.contains("private val handlePage:"))
        assertFalse(source.contains("private val handleLogs:"))
        assertFalse(source.contains("private val handleDiagnostics:"))
        assertFalse(source.contains("private val handleDlnaControl:"))
        assertFalse(source.contains("private val handlePlayRequest:"))
        assertFalse(source.contains("private val handleProxySelectRequest:"))
        assertFalse(source.contains("private val handlePrefetchConfigRequest:"))
        assertFalse(source.contains("private val coreProxyPort:"))
        assertTrue(source.contains("private val adminRoutes: AdminHttpRoutes"))
        assertTrue(source.contains("private val dlnaRoutes: LocalHlsProxyDlnaRoutes"))
        assertTrue(source.contains("private val sessionRelay: LocalHlsProxySessionRelay"))
    }

    @Test
    fun localHlsProxyEmbedsCoreSessionRoutesWithoutSocketRelay() {
        val proxySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")
        val relaySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxySessionRelay.kt")

        assertFalse(proxySource.contains("coreProxyPort = { coreProxy.port }"))
        assertFalse(relaySource.contains("Socket(\"127.0.0.1\", coreProxyPort())"))
        assertTrue(proxySource.contains("serveHttp = false"))
        assertTrue(proxySource.contains("handleSessionRequest = coreProxy::handleSessionRequest"))
    }

    @Test
    fun appModuleDoesNotKeepDuplicatedPureCoreHelpers() {
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/UpstreamHttp.kt").none(Files::exists))
    }

    @Test
    fun appModuleMovesDlnaProtocolCodeIntoDedicatedPackage() {
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaEventing.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaRendererController.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaXml.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/SsdpAdvertiser.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/DlnaEventing.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/DlnaRendererController.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/DlnaXml.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/SsdpAdvertiser.kt").none(Files::exists))
    }

    @Test
    fun dlnaRendererControllerDelegatesSoapProtocolHelpers() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaRendererController.kt")
        val soapSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaControlSoap.kt")

        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaControlSoap.kt").any(Files::exists))
        assertFalse(source.contains("private data class ParsedSoapAction("))
        assertFalse(source.contains("private fun parseSoapAction("))
        assertFalse(source.contains("private fun findSoapBodyAction("))
        assertFalse(source.contains("private fun parseSoapArgs("))
        assertFalse(source.contains("private fun buildSoapResponse("))
        assertFalse(source.contains("private fun buildSoapFault("))
        assertFalse(source.contains("private fun soapEnvelope("))
        assertFalse(source.contains("private fun serviceTypeFor("))
        assertTrue(source.contains("parseSoapAction("))
        assertTrue(source.contains("buildSoapResponse("))
        assertTrue(source.contains("buildSoapFault("))
        assertTrue(soapSource.contains("data class ParsedSoapAction("))
        assertTrue(soapSource.contains("fun parseSoapAction("))
        assertTrue(soapSource.contains("fun buildSoapResponse("))
        assertTrue(soapSource.contains("fun buildSoapFault("))
    }

    @Test
    fun dlnaRendererControllerDelegatesServiceSpecificActionHandling() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaRendererController.kt")

        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaRendererState.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaAvTransportService.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaRenderingControlService.kt").any(Files::exists))
        assertTrue(dlnaSourcePaths("src/main/java/labs/newrapaw/dlna/probe/dlna/DlnaConnectionManagerService.kt").any(Files::exists))
        assertFalse(source.contains("private var currentUri: String ="))
        assertFalse(source.contains("private var volume: Int = 50"))
        assertFalse(source.contains("private fun handleAvTransport("))
        assertFalse(source.contains("private fun handleRenderingControl("))
        assertFalse(source.contains("private fun handleConnectionManager("))
        assertTrue(source.contains("private val state = DlnaRendererState()"))
        assertTrue(source.contains("private val avTransportService = DlnaAvTransportService("))
        assertTrue(source.contains("private val renderingControlService = DlnaRenderingControlService("))
        assertTrue(source.contains("private val connectionManagerService = DlnaConnectionManagerService()"))
    }

    @Test
    fun appModuleMovesPlatformServicesIntoDedicatedPackage() {
        assertTrue(platformSourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/ApkUpdater.kt").any(Files::exists))
        assertTrue(platformSourcePaths("src/main/java/labs/newrapaw/dlna/probe/platform/RendererForegroundService.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ApkUpdater.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/RendererForegroundService.kt").none(Files::exists))
    }

    @Test
    fun appModuleMovesAdminControlPagesIntoDedicatedPackage() {
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPage.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCommonScript.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCache.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCacheScript.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnostics.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageLogs.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageLogsScript.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPagePlay.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageSettings.kt").any(Files::exists))
        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageShell.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPagePlay.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPageScripts.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt").none(Files::exists))
    }

    @Test
    fun controlPageDelegatesDiagnosticsRendering() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPage.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnostics.kt").any(Files::exists))
        assertFalse(source.contains("fun buildDiagnosticsPanelHtml("))
        assertFalse(source.contains("private fun playbackStatusLabel("))
        assertFalse(source.contains("private fun sessionStatusLabel("))
        assertFalse(source.contains("private fun slotHealthGrid("))
        assertFalse(source.contains("private fun selectedSlotDetail("))
        assertFalse(source.contains("private fun slotAssetDiagnosticsTable("))
        assertFalse(source.contains("private fun buildUpstreamSummary("))
        assertFalse(source.contains("private fun recentSegmentsTable("))
        assertFalse(source.contains("private fun severityBadgeCss("))
        assertFalse(source.contains("fun formatBytes("))
    }

    @Test
    fun diagnosticsPanelDelegatesSlotHealthRendering() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnostics.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnosticsSlots.kt").any(Files::exists))
        assertFalse(source.contains("private fun slotHealthGrid("))
        assertFalse(source.contains("private fun selectedSlotDetail("))
        assertFalse(source.contains("private fun slotHealthCss("))
        assertFalse(source.contains("private fun slotHealthLabel("))
        assertFalse(source.contains("private fun slotDependencySummary("))
        assertFalse(source.contains("private fun slotDependencyLabel("))
        assertFalse(source.contains("private fun slotAssetDiagnosticsTable("))
        assertFalse(source.contains("private fun assetStateLabel("))
    }

    @Test
    fun adminShellDelegatesDiagnosticsStyles() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageShell.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnosticsStyle.kt").any(Files::exists))
        assertFalse(source.contains(".diagnostics-summary {"))
        assertFalse(source.contains(".segment-health-grid {"))
        assertFalse(source.contains(".source-tag.direct {"))
        assertFalse(source.contains(".reason-tag.failed {"))
        assertFalse(source.contains(".result-error {"))
        assertTrue(source.contains("\${buildAdminDiagnosticsStyles()}"))
    }

    @Test
    fun adminShellDelegatesGenericPageStyles() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageShell.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageShellStyle.kt").any(Files::exists))
        assertFalse(source.contains(":root {"))
        assertFalse(source.contains(".shell { min-height: 100vh;"))
        assertFalse(source.contains(".sidebar {"))
        assertFalse(source.contains(".status-panel {"))
        assertFalse(source.contains(".button-link,"))
        assertFalse(source.contains(".log-console {"))
        assertFalse(source.contains("@media (max-width: 900px) {"))
        assertTrue(source.contains("\${buildAdminShellStyles()}"))
    }

    @Test
    fun adminScriptsAreSplitBySharedCacheAndLogsConcerns() {
        val cacheScriptSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCacheScript.kt")
        val logsScriptSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageLogsScript.kt")
        val commonScriptSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCommonScript.kt")

        assertTrue(commonScriptSource.contains("fun buildCommonFormScript()"))
        assertTrue(cacheScriptSource.contains("fun buildCachePageScript()"))
        assertTrue(logsScriptSource.contains("fun buildLogsPageScript()"))
        assertFalse(cacheScriptSource.contains("fun buildLogsPageScript()"))
        assertFalse(logsScriptSource.contains("fun buildCachePageScript()"))
        assertFalse(commonScriptSource.contains("fun buildCachePageScript()"))
        assertFalse(commonScriptSource.contains("fun buildLogsPageScript()"))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageScripts.kt").none(Files::exists))
    }

    @Test
    fun adminHttpRoutingLivesInAdminPackage() {
        val proxySource = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt")
        val handlerSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyRequestHandler.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/AdminHttpRoutes.kt").any(Files::exists))
        assertTrue(proxySource.contains("private val adminRoutes = AdminHttpRoutes("))
        assertTrue(handlerSource.contains("private val adminRoutes: AdminHttpRoutes"))
        assertFalse(handlerSource.contains("private val appRoutes: LocalHlsProxyAppRoutes"))
        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxyAppRoutes.kt").none(Files::exists))
    }

    @Test
    fun adminDiagnosticsViewsUseDedicatedAdminProjection() {
        val routesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/AdminHttpRoutes.kt")
        val cacheSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageCache.kt")
        val diagnosticsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnostics.kt")
        val slotsSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPageDiagnosticsSlots.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/AdminPlaybackDiagnostics.kt").any(Files::exists))
        assertTrue(routesSource.contains("toAdminPlaybackDiagnosticsSnapshot()"))
        assertFalse(cacheSource.contains("labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsSnapshot"))
        assertFalse(diagnosticsSource.contains("labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsSnapshot"))
        assertFalse(diagnosticsSource.contains("labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsStatus"))
        assertFalse(diagnosticsSource.contains("labs.newrapaw.dlna.probe.proxy.DiagnosticsSeverity"))
        assertFalse(diagnosticsSource.contains("labs.newrapaw.dlna.probe.proxy.SegmentSample"))
        assertFalse(slotsSource.contains("labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsSnapshot"))
        assertFalse(slotsSource.contains("labs.newrapaw.dlna.probe.proxy.AssetDiagnosticsItem"))
        assertFalse(slotsSource.contains("labs.newrapaw.dlna.probe.proxy.SlotDiagnosticsState"))
    }

    @Test
    fun adminDiagnosticsProjectionSeparatesModelsFromCoreMapping() {
        val modelSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/AdminPlaybackDiagnostics.kt")
        val mapperSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/AdminPlaybackDiagnosticsMapper.kt")

        assertTrue(adminSourcePaths("src/main/java/labs/newrapaw/dlna/probe/admin/AdminPlaybackDiagnosticsMapper.kt").any(Files::exists))
        assertFalse(modelSource.contains("labs.newrapaw.dlna.probe.core."))
        assertFalse(modelSource.contains("toAdminPlaybackDiagnosticsSnapshot()"))
        assertTrue(mapperSource.contains("toAdminPlaybackDiagnosticsSnapshot()"))
        assertTrue(mapperSource.contains("labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot"))
    }

    @Test
    fun mainActivityRuntimeSharesSingleHttpClientAcrossAppServices() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")

        assertTrue(source.contains("val appHttpClient = OkHttpClient()"))
        assertTrue(source.contains("val updater = ApkUpdater(activity, appHttpClient, logState::append)"))
        assertTrue(source.contains("client = appHttpClient"))
        assertEquals(1, "OkHttpClient\\(".toRegex().findAll(source).count())
    }

    @Test
    fun proxyPlaybackDiagnosticsBridgeKeepsOnlyPlaybackStatusAlias() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/PlaybackDiagnostics.kt")

        assertTrue(source.contains("typealias PlaybackDiagnosticsStatus = labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus"))
        assertFalse(source.contains("typealias DiagnosticsSeverity"))
        assertFalse(source.contains("typealias SlotDiagnosticsState"))
        assertFalse(source.contains("typealias SegmentSample"))
        assertFalse(source.contains("typealias SlotDiagnosticsItem"))
        assertFalse(source.contains("typealias AssetDiagnosticsItem"))
        assertFalse(source.contains("typealias DiagnosticsInsight"))
        assertFalse(source.contains("typealias PlaybackDiagnosticsSnapshot"))
        assertFalse(source.contains("typealias PlaybackDiagnosticsState"))
    }

    @Test
    fun proxyConfigBridgeDropsUnusedConfigAndTypeAliases() {
        val source = sourceText("src/main/java/labs/newrapaw/dlna/probe/proxy/SharedPreferencesProxySettingsStore.kt")

        val adminRoutesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/AdminHttpRoutes.kt")
        val controlPageSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/admin/ControlPage.kt")
        val runtimeSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityRuntime.kt")
        val servicesSource = sourceText("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityServices.kt")

        assertFalse(source.contains("typealias ProxyConfig ="))
        assertFalse(source.contains("typealias ProxyType ="))
        assertFalse(source.contains("typealias UpstreamMode ="))
        assertFalse(source.contains("typealias ProxySettingsState ="))
        assertFalse(source.contains("typealias ProxySettingsStore ="))
        assertFalse(source.contains("typealias InMemoryProxySettingsStore ="))
        assertFalse(source.contains("fun parseProxyConfig(value: String):"))
        assertTrue(adminRoutesSource.contains("import labs.newrapaw.dlna.probe.core.ProxySettingsState"))
        assertTrue(adminRoutesSource.contains("import labs.newrapaw.dlna.probe.core.ProxySettingsStore"))
        assertTrue(adminRoutesSource.contains("import labs.newrapaw.dlna.probe.core.UpstreamMode"))
        assertTrue(adminRoutesSource.contains("import labs.newrapaw.dlna.probe.core.parseProxyConfig"))
        assertTrue(controlPageSource.contains("import labs.newrapaw.dlna.probe.core.ProxySettingsState"))
        assertTrue(controlPageSource.contains("import labs.newrapaw.dlna.probe.core.UpstreamMode"))
        assertTrue(runtimeSource.contains("import labs.newrapaw.dlna.probe.core.ProxySettingsStore"))
        assertTrue(servicesSource.contains("import labs.newrapaw.dlna.probe.core.ProxySettingsStore"))
    }

    @Test
    fun appModuleMovesMainActivityHelpersIntoDedicatedUiPackage() {
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityChrome.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityEnvironment.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityLogState.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityNavigation.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlatform.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackCoordinator.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlaybackRuntime.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityPlayerListener.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivitySsdp.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/PlayerErrorRecovery.kt").any(Files::exists))
        assertTrue(uiSourcePaths("src/main/java/labs/newrapaw/dlna/probe/ui/UiLogBuffer.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityChrome.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityEnvironment.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityLogState.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityNavigation.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityPlatform.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityPlaybackCoordinator.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityPlaybackRuntime.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityPlayerListener.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivityShell.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/MainActivitySsdp.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/PlayerErrorRecovery.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/UiLogBuffer.kt").none(Files::exists))
    }

    @Test
    fun appModuleMovesProxyRuntimeCodeIntoDedicatedProxyPackage() {
        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/LocalHlsProxy.kt").any(Files::exists))
        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/SharedPreferencesProxySettingsStore.kt").any(Files::exists))
        assertTrue(proxySourcePaths("src/main/java/labs/newrapaw/dlna/probe/proxy/PlaybackDiagnostics.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt").none(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt").none(Files::exists))
    }

    @Test
    fun appModuleMovesConfigDefaultsIntoDedicatedConfigPackage() {
        assertTrue(configSourcePaths("src/main/java/labs/newrapaw/dlna/probe/config/ProbeConfig.kt").any(Files::exists))
        assertTrue(sourcePaths("src/main/java/labs/newrapaw/dlna/probe/ProbeConfig.kt").none(Files::exists))
    }

    @Test
    fun proxyPackageDoesNotDependOnUiOrPlatformPackages() {
        val proxyImports = packageImports("src/main/java/labs/newrapaw/dlna/probe/proxy")

        assertTrue(proxyImports.none { it.contains(".ui.") })
        assertTrue(proxyImports.none { it.contains(".platform.") })
    }

    @Test
    fun supportPackagesDoNotDependOnUiPackage() {
        val supportImports = packageImports("src/main/java/labs/newrapaw/dlna/probe/admin") +
            packageImports("src/main/java/labs/newrapaw/dlna/probe/dlna") +
            packageImports("src/main/java/labs/newrapaw/dlna/probe/platform") +
            packageImports("src/main/java/labs/newrapaw/dlna/probe/config")

        assertTrue(supportImports.none { it.contains(".ui.") })
    }

    @Test
    fun uiPackageRemainsTheCompositionRoot() {
        val uiImports = packageImports("src/main/java/labs/newrapaw/dlna/probe/ui")

        assertTrue(uiImports.any { it.contains(".proxy.") })
        assertTrue(uiImports.any { it.contains(".platform.") })
        assertTrue(uiImports.any { it.contains(".dlna.") })
        assertEquals(0, uiImports.count { it.contains(".admin.") })
    }

    private fun sourceText(path: String): String =
        String(Files.readAllBytes(sourcePaths(path).first(Files::exists)), Charsets.UTF_8)

    private fun sourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun packageImports(path: String): List<String> =
        Files.walk(packagePath(path)).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .flatMap { file ->
                    Files.readAllLines(file).stream()
                        .filter { it.startsWith("import labs.newrapaw.dlna.probe.") }
                }
                .toList()
        }

    private fun packagePath(path: String): Path =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).firstOrNull(Files::exists)
            ?: error("Package path not found: $path")

    private fun dlnaSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun platformSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun adminSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun uiSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun proxySourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun configSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("app").resolve(path),
        ).distinct()

    private fun coreSourcePaths(path: String): List<Path> =
        listOf(
            Paths.get(path),
            Paths.get("..").resolve("core").resolve(path),
            Paths.get("core").resolve(path),
        ).distinct()
}
