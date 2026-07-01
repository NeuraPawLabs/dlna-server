package labs.newrapaw.dlna.probe.ui

import java.net.NetworkInterface
import java.util.Enumeration
import java.util.UUID
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.platform.resolveNonLoopbackIpv4Address

fun resolveLocalIpAddress(
    networkInterfaces: () -> Enumeration<NetworkInterface>? = NetworkInterface::getNetworkInterfaces,
): String = resolveNonLoopbackIpv4Address(networkInterfaces)

fun buildPublicControlUrl(
    localIpAddress: String,
    publicBaseUrl: (String) -> String,
): String =
    localIpAddress.takeIf { it != "unknown" }?.let(publicBaseUrl) ?: "unknown"

fun stableRendererUuid(installationId: String): String =
    UUID.nameUUIDFromBytes("newrapaw-dlna-$installationId".toByteArray(Charsets.UTF_8)).toString()

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
