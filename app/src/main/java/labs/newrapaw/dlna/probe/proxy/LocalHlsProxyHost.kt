package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy

internal class LocalHlsProxyHost(
    private val coreProxy: CoreLocalHlsProxy,
    handleSocket: (java.net.Socket) -> Unit,
    safeLog: (String) -> Unit,
) : Closeable {
    private val executor: ExecutorService = boundedExecutor(
        maxThreads = APP_PROXY_MAX_THREADS,
        queueCapacity = APP_PROXY_QUEUE_CAPACITY,
    )
    private val proxyServer = LocalHlsProxyServer(
        executor = executor,
        handleSocket = handleSocket,
        safeLog = safeLog,
    )

    val port: Int
        get() = proxyServer.port

    val baseUrl: String
        get() = proxyServer.baseUrl

    fun publicBaseUrl(hostAddress: String): String = proxyServer.publicBaseUrl(hostAddress)

    fun start() {
        runCatching {
            coreProxy.start()
            proxyServer.start()
        }.onFailure {
            runCatching { proxyServer.close() }
            runCatching { coreProxy.close() }
            throw it
        }
    }

    override fun close() {
        proxyServer.close()
        coreProxy.close()
        executor.shutdownNow()
    }

    private companion object {
        const val APP_PROXY_MAX_THREADS = 8
        const val APP_PROXY_QUEUE_CAPACITY = 64

        fun boundedExecutor(
            maxThreads: Int,
            queueCapacity: Int,
            callerRunsOnSaturation: Boolean = false,
        ): ThreadPoolExecutor =
            ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(queueCapacity),
                if (callerRunsOnSaturation) {
                    ThreadPoolExecutor.CallerRunsPolicy()
                } else {
                    ThreadPoolExecutor.AbortPolicy()
                },
            ).apply {
                allowCoreThreadTimeOut(true)
            }
    }
}
