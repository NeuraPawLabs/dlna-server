package labs.newrapaw.dlna.probe.platform

import java.net.NetworkInterface
import java.util.Enumeration

internal fun resolveNonLoopbackIpv4Address(
    networkInterfaces: () -> Enumeration<NetworkInterface>?,
): String =
    runCatching {
        networkInterfaces()
            ?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress
            ?: "unknown"
    }.getOrDefault("unknown")
