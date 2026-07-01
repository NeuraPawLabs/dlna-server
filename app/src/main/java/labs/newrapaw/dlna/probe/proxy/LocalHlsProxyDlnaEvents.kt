package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import labs.newrapaw.dlna.probe.dlna.DlnaEventNotifyRequest
import labs.newrapaw.dlna.probe.dlna.DlnaEventing
import labs.newrapaw.dlna.probe.dlna.DlnaRendererSnapshot
import okhttp3.OkHttpClient

internal class LocalHlsProxyDlnaEvents(
    client: OkHttpClient,
    private val safeLog: (String) -> Unit,
    currentAvTransportSnapshot: () -> DlnaRendererSnapshot?,
    currentRenderingControlSnapshot: () -> DlnaRendererSnapshot?,
) : Closeable {
    private val eventNotifier = LocalHlsProxyEventNotifier(
        client = client,
        safeLog = safeLog,
        recordDeliveryResult = ::recordEventNotifyDeliveryResult,
    )

    val eventing = DlnaEventing(
        sendNotify = eventNotifier::dispatch,
        currentAvTransportSnapshot = currentAvTransportSnapshot,
        currentRenderingControlSnapshot = currentRenderingControlSnapshot,
    )

    fun publishAvTransport(snapshot: DlnaRendererSnapshot) {
        eventing.publishAvTransport(snapshot)
    }

    fun publishRenderingControl(snapshot: DlnaRendererSnapshot) {
        eventing.publishRenderingControl(snapshot)
    }

    override fun close() {
        eventNotifier.close()
    }

    private fun recordEventNotifyDeliveryResult(
        request: DlnaEventNotifyRequest,
        success: Boolean,
    ) {
        request.headers["SID"]?.let { sid ->
            eventing.recordNotifyDeliveryResult(
                sid = sid,
                success = success,
            )
        }
    }
}
