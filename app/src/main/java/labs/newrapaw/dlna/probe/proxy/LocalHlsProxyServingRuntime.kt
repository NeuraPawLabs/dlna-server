package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import labs.newrapaw.dlna.probe.admin.AdminHttpRoutes
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy

internal class LocalHlsProxyServingRuntime(
    coreProxy: CoreLocalHlsProxy,
    adminRoutes: AdminHttpRoutes,
    dlnaRoutes: LocalHlsProxyDlnaRoutes,
    shouldSuppressRequestFailureLog: (Throwable) -> Boolean,
    safeLog: (String) -> Unit,
) : Closeable {
    private val sessionRelay = LocalHlsProxySessionRelay(
        handleSessionRequest = coreProxy::handleSessionRequest,
    )
    private val requestHandler = LocalHlsProxyRequestHandler(
        adminRoutes = adminRoutes,
        dlnaRoutes = dlnaRoutes,
        sessionRelay = sessionRelay,
        shouldSuppressRequestFailureLog = shouldSuppressRequestFailureLog,
        safeLog = safeLog,
    )
    private val host = LocalHlsProxyHost(
        coreProxy = coreProxy,
        handleSocket = requestHandler::handle,
        safeLog = safeLog,
    )

    val port: Int
        get() = host.port

    val baseUrl: String
        get() = host.baseUrl

    fun publicBaseUrl(hostAddress: String): String = host.publicBaseUrl(hostAddress)

    fun start() = host.start()

    override fun close() {
        host.close()
    }
}
