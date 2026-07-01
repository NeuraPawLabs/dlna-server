package labs.newrapaw.dlna.probe.proxy

import java.io.OutputStream

internal class LocalHlsProxySessionRelay(
    private val handleSessionRequest: (String, String, Map<String, String>, OutputStream) -> Boolean,
) {
    fun handle(method: String, path: String, headers: Map<String, String>, output: OutputStream): Boolean =
        handleSessionRequest(method, path, headers, output)
}
