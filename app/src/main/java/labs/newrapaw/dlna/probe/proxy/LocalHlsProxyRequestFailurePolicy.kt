package labs.newrapaw.dlna.probe.proxy

import java.net.SocketException

internal fun shouldSuppressProxyRequestFailureLog(error: Throwable): Boolean {
    val socketError = error as? SocketException ?: return false
    val message = socketError.message.orEmpty()
    return message.contains("Broken pipe", ignoreCase = true) ||
        message.contains("Connection reset by peer", ignoreCase = true)
}
