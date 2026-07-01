package labs.newrapaw.dlna.probe.proxy

import java.io.OutputStream
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.dlna.DlnaEventing
import labs.newrapaw.dlna.probe.dlna.DlnaRendererController
import labs.newrapaw.dlna.probe.dlna.buildAvTransportScpdXml
import labs.newrapaw.dlna.probe.dlna.buildConnectionManagerScpdXml
import labs.newrapaw.dlna.probe.dlna.buildDeviceDescriptionXml
import labs.newrapaw.dlna.probe.dlna.buildRenderingControlScpdXml

internal class LocalHlsProxyDlnaRoutes(
    private val dlnaConfig: () -> DlnaDeviceConfig?,
    private val renderer: DlnaRendererController,
    private val eventing: DlnaEventing,
    private val safeLog: (String) -> Unit,
) {
    fun handle(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
    ): Boolean =
        when {
            method == "GET" && path == "/description.xml" -> respondDeviceDescription(output)
            method == "GET" && path == "/upnp/service/AVTransport.xml" -> respondServiceDescription(output, buildAvTransportScpdXml())
            method == "GET" && path == "/upnp/service/RenderingControl.xml" -> respondServiceDescription(output, buildRenderingControlScpdXml())
            method == "GET" && path == "/upnp/service/ConnectionManager.xml" -> respondServiceDescription(output, buildConnectionManagerScpdXml())
            method == "POST" && path.startsWith("/upnp/control/") -> respondControl(path, headers, body, output)
            method == "SUBSCRIBE" && path.startsWith("/upnp/event/") -> respondSubscribe(path, headers, output)
            method == "UNSUBSCRIBE" && path.startsWith("/upnp/event/") -> respondUnsubscribe(path, headers, output)
            else -> false
        }

    private fun respondDeviceDescription(output: OutputStream): Boolean {
        val config = dlnaConfig()
        if (config == null) {
            writeText(output, 503, "text/plain", "DLNA device address is not ready")
            return true
        }

        writeText(output, 200, "text/xml", buildDeviceDescriptionXml(config))
        return true
    }

    private fun respondServiceDescription(output: OutputStream, body: String): Boolean {
        writeText(output, 200, "text/xml", body)
        return true
    }

    private fun respondControl(
        path: String,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
    ): Boolean {
        val serviceName = path.substringAfterLast("/")
        val response = renderer.handleControlRequest(
            serviceName = serviceName,
            soapActionHeader = headers["soapaction"],
            body = body,
        )
        writeText(output, response.statusCode, response.contentType.substringBefore(";"), response.body)
        return true
    }

    private fun respondSubscribe(path: String, headers: Map<String, String>, output: OutputStream): Boolean {
        val serviceName = path.substringAfterLast("/")
        safeLog("[DLNA] Subscribe: $serviceName")
        writeResponse(
            output,
            eventing.subscribe(
                serviceName = serviceName,
                callbackHeader = headers["callback"],
                timeoutHeader = headers["timeout"],
                sidHeader = headers["sid"],
            ),
        )
        return true
    }

    private fun respondUnsubscribe(path: String, headers: Map<String, String>, output: OutputStream): Boolean {
        val serviceName = path.substringAfterLast("/")
        safeLog("[DLNA] Unsubscribe: $serviceName")
        writeResponse(
            output,
            eventing.unsubscribe(
                serviceName = serviceName,
                sidHeader = headers["sid"],
            ),
        )
        return true
    }
}
