package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.dlna.DlnaRendererController
import labs.newrapaw.dlna.probe.dlna.DlnaRendererSnapshot
import okhttp3.OkHttpClient

internal class LocalHlsProxyDlnaRuntime(
    client: OkHttpClient,
    dlnaConfig: () -> DlnaDeviceConfig?,
    log: (String) -> Unit,
    safeLog: (String) -> Unit,
    onPlayRequested: (String) -> Unit,
    onStopRequested: () -> Unit,
    onPauseRequested: () -> Unit,
    onSeekRequested: (Long) -> Unit,
) : Closeable {
    private val snapshotProvider = LocalHlsProxyDlnaSnapshotProvider()
    private val dlnaEvents = LocalHlsProxyDlnaEvents(
        client = client,
        safeLog = safeLog,
        currentAvTransportSnapshot = snapshotProvider::snapshot,
        currentRenderingControlSnapshot = snapshotProvider::snapshot,
    )
    private val renderer = DlnaRendererController(
        log = log,
        onPlayRequested = onPlayRequested,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
        onSeekRequested = onSeekRequested,
        onAvTransportStateChanged = dlnaEvents::publishAvTransport,
        onRenderingControlStateChanged = dlnaEvents::publishRenderingControl,
    )

    val routes = LocalHlsProxyDlnaRoutes(
        dlnaConfig = dlnaConfig,
        renderer = renderer,
        eventing = dlnaEvents.eventing,
        safeLog = safeLog,
    )

    init {
        snapshotProvider.attach(renderer)
    }

    fun syncPlayerState(
        transportState: String,
        transportStatus: String,
        positionMs: Long?,
        durationMs: Long? = null,
    ) {
        renderer.syncPlayerState(
            transportState = transportState,
            transportStatus = transportStatus,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    fun syncPlayerPosition(positionMs: Long) {
        renderer.syncPlayerPosition(positionMs)
    }

    override fun close() {
        dlnaEvents.close()
    }
}

private class LocalHlsProxyDlnaSnapshotProvider {
    private var renderer: DlnaRendererController? = null

    fun attach(renderer: DlnaRendererController) {
        this.renderer = renderer
    }

    fun snapshot(): DlnaRendererSnapshot? = renderer?.snapshot()
}
