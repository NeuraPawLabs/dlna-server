package labs.newrapaw.dlna.probe.ui

import java.net.NetworkInterface
import java.util.UUID
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig

fun resolveLocalIpAddress(): String =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
        ?.hostAddress
        ?: "unknown"

fun buildPublicControlUrl(
    localIpAddress: String,
    publicBaseUrl: (String) -> String,
): String =
    localIpAddress.takeIf { it != "unknown" }?.let(publicBaseUrl) ?: "unknown"

fun stableRendererUuid(androidId: String): String =
    UUID.nameUUIDFromBytes("newrapaw-dlna-$androidId".toByteArray(Charsets.UTF_8)).toString()

fun buildDlnaDeviceConfig(
    localIpAddress: String,
    rendererUuid: String,
    publicBaseUrl: (String) -> String,
): DlnaDeviceConfig? {
    val ip = localIpAddress.takeIf { it != "unknown" } ?: return null
    return DlnaDeviceConfig(
        baseUrl = publicBaseUrl(ip),
        deviceName = "PawCast",
        uuid = rendererUuid,
    )
}
