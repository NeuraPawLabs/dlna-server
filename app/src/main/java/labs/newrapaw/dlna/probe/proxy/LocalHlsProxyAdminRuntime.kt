package labs.newrapaw.dlna.probe.proxy

import labs.newrapaw.dlna.probe.admin.AdminHttpRoutes
import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot
import labs.newrapaw.dlna.probe.core.ProxySettingsStore

internal class LocalHlsProxyAdminRuntime(
    proxySettingsStore: ProxySettingsStore,
    getLogs: () -> List<String>,
    diagnosticsSnapshot: () -> PlaybackDiagnosticsSnapshot,
    activeSessionInfo: () -> ActiveSessionInfo?,
    requestPlayback: (String) -> Unit,
    onStopRequested: () -> Unit,
    updatePlaybackStatus: (PlaybackDiagnosticsStatus) -> Unit,
    onUpdateRequested: (String) -> Unit,
    clearActiveSessionCache: () -> Unit,
    updatePrefetchConcurrency: (Int) -> Unit,
    localPlaybackUrl: () -> String,
    safeLog: (String) -> Unit,
) {
    val routes = AdminHttpRoutes(
        proxySettingsStore = proxySettingsStore,
        getLogs = getLogs,
        diagnosticsSnapshot = diagnosticsSnapshot,
        activeSessionInfo = activeSessionInfo,
        requestPlayback = requestPlayback,
        onStopRequested = onStopRequested,
        updatePlaybackStatus = updatePlaybackStatus,
        onUpdateRequested = onUpdateRequested,
        clearActiveSessionCache = clearActiveSessionCache,
        updatePrefetchConcurrency = updatePrefetchConcurrency,
        localPlaybackUrl = localPlaybackUrl,
        safeLog = safeLog,
    )
}
