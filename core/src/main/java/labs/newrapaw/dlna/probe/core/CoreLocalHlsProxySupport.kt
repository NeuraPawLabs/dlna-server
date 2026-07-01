package labs.newrapaw.dlna.probe.core

import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun shouldSuppressRequestFailureLog(error: Throwable): Boolean {
    val socketError = error as? SocketException ?: return false
    val message = socketError.message.orEmpty()
    return message.contains("Broken pipe", ignoreCase = true) ||
        message.contains("Connection reset by peer", ignoreCase = true)
}

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
