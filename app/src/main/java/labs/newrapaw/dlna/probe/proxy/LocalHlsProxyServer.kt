package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal class LocalHlsProxyServer(
    private val executor: ExecutorService,
    private val handleSocket: (Socket) -> Unit,
    private val safeLog: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val activeSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun publicBaseUrl(hostAddress: String): String = "http://$hostAddress:$port"

    fun start() {
        if (running.get()) return
        runCatching {
            serverSocket = ServerSocket(0, 50, InetAddress.getByName("0.0.0.0"))
            running.set(true)
            executor.execute {
                safeLog("Proxy listening at $baseUrl")
                while (running.get()) {
                    val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                    activeSockets += socket
                    socket.soTimeout = DEFAULT_SOCKET_TIMEOUT_MS
                    try {
                        executor.execute {
                            try {
                                handleSocket(socket)
                            } finally {
                                activeSockets.remove(socket)
                                runCatching { socket.close() }
                            }
                        }
                    } catch (_: RejectedExecutionException) {
                        safeLog("Proxy overloaded: rejecting socket")
                        socket.use {
                            runCatching {
                                writeText(
                                    output = it.getOutputStream(),
                                    status = 503,
                                    contentType = "text/plain",
                                    body = "Service Unavailable",
                                )
                            }
                        }
                        activeSockets.remove(socket)
                    }
                }
            }
        }.onFailure {
            close()
            throw it
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        activeSockets.forEach { socket -> runCatching { socket.close() } }
        activeSockets.clear()
        serverSocket = null
    }

    companion object {
        internal const val DEFAULT_SOCKET_TIMEOUT_MS = 15_000
    }
}
