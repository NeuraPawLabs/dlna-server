package labs.newrapaw.dlna.probe.proxy

import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy

internal class LocalHlsProxyPlaybackRouter(
    private val coreProxy: CoreLocalHlsProxy,
    private val localBaseUrl: () -> String,
    private val safeLog: (String) -> Unit,
    private val beforePlaybackSwitch: () -> Unit,
    private val onPlayRequested: (String) -> Unit,
) {
    fun dispatch(sourceUrl: String) {
        safeLog("Remote play request: $sourceUrl")
        beforePlaybackSwitch()
        val session = coreProxy.openSession(sourceUrl, localBaseUrl = localBaseUrl())
        onPlayRequested(session.localManifestUrl)
    }

    fun recoverActivePlaybackSession(baseUrl: String): String? {
        val active = coreProxy.activeSessionInfo(baseUrl) ?: return null
        safeLog("Rebuilding active session: ${active.sourceUrl}")
        return coreProxy.openSession(
            sourceUrl = active.sourceUrl,
            localBaseUrl = baseUrl,
        ).localManifestUrl
    }
}
