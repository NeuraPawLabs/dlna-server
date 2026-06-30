package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSessionManager
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer

internal class CoreLocalHlsSessionOpener(
    private val sessionManager: PlaybackSessionManager,
    private val playbackRuntime: CoreLocalHlsPlaybackRuntime,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val proxySettingsStore: ProxySettingsStore,
    private val sessionLocalServer: SessionLocalServer,
) {
    fun openSession(sourceUrl: String, baseUrl: String): ActiveSessionInfo {
        val session = sessionManager.startSession(
            sourceUrl = sourceUrl,
            entryManifestUrl = sourceUrl,
            localRootDir = "session-${System.currentTimeMillis()}",
        )
        playbackRuntime.openSession(session)
        val localManifestUrl = "$baseUrl${sessionLocalServer.masterManifestPath(session.sessionId)}"
        diagnosticsState.resetForPlayback(
            sourceUrl = session.sourceUrl,
            localProxyUrl = localManifestUrl,
            settings = proxySettingsStore.load(),
        )
        diagnosticsState.setSessionStatus(session.status.name)
        return requireNotNull(playbackRuntime.activeSessionInfo(baseUrl))
    }
}
