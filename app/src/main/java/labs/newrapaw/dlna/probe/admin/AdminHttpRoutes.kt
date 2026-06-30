package labs.newrapaw.dlna.probe.admin

import java.io.OutputStream
import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.core.UpstreamMode
import labs.newrapaw.dlna.probe.core.buildPlaybackDiagnosticsJson
import labs.newrapaw.dlna.probe.core.parseProxyConfig
import labs.newrapaw.dlna.probe.proxy.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.proxy.writeJson
import labs.newrapaw.dlna.probe.proxy.writeText

internal class AdminHttpRoutes(
    private val proxySettingsStore: ProxySettingsStore,
    private val getLogs: () -> List<String>,
    private val diagnosticsSnapshot: () -> PlaybackDiagnosticsSnapshot,
    private val activeSessionInfo: () -> ActiveSessionInfo?,
    private val requestPlayback: (String) -> Unit,
    private val onStopRequested: () -> Unit,
    private val updatePlaybackStatus: (PlaybackDiagnosticsStatus) -> Unit,
    private val onUpdateRequested: (String) -> Unit,
    private val clearActiveSessionCache: () -> Unit,
    private val updatePrefetchConcurrency: (Int) -> Unit,
    private val localPlaybackUrl: () -> String,
    private val safeLog: (String) -> Unit,
) {
    fun handle(method: String, path: String, body: String, output: OutputStream): Boolean =
        when {
            method == "GET" && path == "/" -> respondPage(AdminPage.PLAY, output)
            method == "GET" && path == AdminPage.PLAY.path -> respondPage(AdminPage.PLAY, output)
            method == "GET" && path == AdminPage.CACHE.path -> respondPage(AdminPage.CACHE, output)
            method == "GET" && path == AdminPage.LOGS.path -> respondPage(AdminPage.LOGS, output)
            method == "GET" && path == AdminPage.SETTINGS.path -> respondPage(AdminPage.SETTINGS, output)
            method == "GET" && path.startsWith("/logs") -> respondLogs(output)
            method == "GET" && path == "/diagnostics" -> respondDiagnostics(output)
            method == "GET" && path == "/diagnostics/panel" -> respondDiagnosticsPanel(output)
            method == "POST" && path.startsWith("/control/play") -> respondPlay(body, output)
            method == "POST" && path.startsWith("/control/stop") -> respondStop(output)
            method == "POST" && path.startsWith("/control/update") -> respondUpdate(body, output)
            method == "POST" && path.startsWith("/control/proxy/add") -> respondProxyAdd(body, output)
            method == "POST" && path.startsWith("/control/proxy/select") -> respondProxySelect(body, output)
            method == "POST" && path.startsWith("/control/proxy/delete") -> respondProxyDelete(body, output)
            method == "POST" && path.startsWith("/control/prefetch/config") -> respondPrefetchConfig(body, output)
            method == "POST" && path.startsWith("/control/cache/clear") -> respondCacheClear(output)
            else -> false
        }

    private fun respondPage(page: AdminPage, output: OutputStream): Boolean {
        val settings = proxySettingsStore.load()
        val adminDiagnostics = diagnosticsSnapshot().toAdminPlaybackDiagnosticsSnapshot()
        val bodyHtml = when (page) {
            AdminPage.PLAY -> buildPlayPageContent()
            AdminPage.CACHE -> buildCachePageContent(
                proxySettings = settings,
                playbackDiagnostics = adminDiagnostics,
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
                localPlaybackUrl = localPlaybackUrl(),
                currentNetwork = controlPageCurrentNetwork(settings),
                bodyHtml = bodyHtml,
                pageScript = pageScript,
            ),
        )
        return true
    }

    private fun respondLogs(output: OutputStream): Boolean {
        writeText(output, 200, "text/plain", getLogs().joinToString("\n"))
        return true
    }

    private fun respondDiagnostics(output: OutputStream): Boolean {
        writeText(output, 200, "application/json", buildPlaybackDiagnosticsJson(diagnosticsSnapshot()))
        return true
    }

    private fun respondDiagnosticsPanel(output: OutputStream): Boolean {
        writeText(output, 200, "text/html", buildDiagnosticsPanelHtml(diagnosticsSnapshot().toAdminPlaybackDiagnosticsSnapshot()))
        return true
    }

    private fun respondPlay(body: String, output: OutputStream): Boolean {
        val url = decodeFormUrl(body)
        if (url == null) {
            writeJson(output, 400, false, "Missing URL")
            return true
        }

        requestPlayback(url)
        writeJson(output, 200, true, "Play request sent. You can return to the TV.")
        return true
    }

    private fun respondStop(output: OutputStream): Boolean {
        safeLog("Remote stop request")
        updatePlaybackStatus(PlaybackDiagnosticsStatus.STOPPED)
        onStopRequested()
        writeJson(output, 200, true, "Stop request sent. You can return to the TV.")
        return true
    }

    private fun respondUpdate(body: String, output: OutputStream): Boolean {
        val apkUrl = decodeFormValue(body, "apkUrl")
        if (apkUrl == null) {
            writeJson(output, 400, false, "Missing APK URL")
            return true
        }

        safeLog("Remote update request: $apkUrl")
        onUpdateRequested(apkUrl)
        writeJson(output, 200, true, "Update request sent. Confirm installation on the TV.")
        return true
    }

    private fun respondProxyAdd(body: String, output: OutputStream): Boolean {
        val proxyUrl = decodeFormValue(body, "proxyUrl")
        val config = proxyUrl?.let(::parseProxyConfig)
        if (config == null) {
            writeJson(output, 400, false, "Invalid proxy URL. Use http://host:port, socks5://host:port, or socks5h://host:port.")
            return true
        }

        val next = proxySettingsStore.load().add(config).select(config.id)
        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${config.displayUrl()}")
        writeJson(output, 200, true, "Proxy saved: ${config.displayUrl()}")
        return true
    }

    private fun respondProxySelect(body: String, output: OutputStream): Boolean {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null) {
            writeJson(output, 400, false, "Missing proxyId")
            return true
        }

        val current = proxySettingsStore.load()
        val mode = decodeFormValue(body, "upstreamMode")?.let(::parseUpstreamMode) ?: current.upstreamMode
        val next = current.select(proxyId).withUpstreamMode(mode)
        if (next.selectedProxyId != proxyId) {
            writeJson(output, 400, false, "Unknown proxy")
            return true
        }

        proxySettingsStore.save(next)
        safeLog("Proxy selected: ${next.selectedProxy()?.displayUrl() ?: "Direct"}")
        writeJson(output, 200, true, "Proxy selected")
        return true
    }

    private fun respondProxyDelete(body: String, output: OutputStream): Boolean {
        val proxyId = decodeFormValue(body, "proxyId")
        if (proxyId == null || proxyId == ProxySettingsState.DIRECT_PROXY_ID) {
            writeJson(output, 400, false, "Missing proxyId")
            return true
        }

        val next = proxySettingsStore.load().remove(proxyId)
        proxySettingsStore.save(next)
        safeLog("Proxy deleted: $proxyId")
        writeJson(output, 200, true, "Proxy deleted")
        return true
    }

    private fun respondPrefetchConfig(body: String, output: OutputStream): Boolean {
        val requested = decodeFormValue(body, "prefetchConcurrency")?.toIntOrNull()
            ?: ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY
        val next = proxySettingsStore.load().copy(prefetchConcurrency = requested).normalized()
        proxySettingsStore.save(next)
        updatePrefetchConcurrency(next.prefetchConcurrency)
        safeLog("Prefetch concurrency updated: ${next.prefetchConcurrency}")
        writeJson(output, 200, true, "Prefetch concurrency updated")
        return true
    }

    private fun respondCacheClear(output: OutputStream): Boolean {
        clearActiveSessionCache()
        safeLog("Session cache cleared")
        writeJson(output, 200, true, "Cache cleared")
        return true
    }

    private fun parseUpstreamMode(value: String): UpstreamMode =
        runCatching { UpstreamMode.valueOf(value) }.getOrDefault(UpstreamMode.PROXY_ONLY)
}
