package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal class LocalHlsProxyServer(
    private val executor: ExecutorService,
    private val handleSocket: (Socket) -> Unit,
    private val safeLog: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun publicBaseUrl(hostAddress: String): String = "http://$hostAddress:$port"

    fun start() {
        if (running.get()) return
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
        running.set(true)
        executor.execute {
            safeLog("Proxy listening at $baseUrl")
            while (running.get()) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute { handleSocket(socket) }
            }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
