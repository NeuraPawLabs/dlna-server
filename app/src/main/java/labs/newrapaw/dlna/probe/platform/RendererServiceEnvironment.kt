package labs.newrapaw.dlna.probe.platform

import java.net.NetworkInterface
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import java.util.Enumeration
import java.util.UUID

fun resolveRendererServiceIpAddress(
    networkInterfaces: () -> Enumeration<NetworkInterface>? = NetworkInterface::getNetworkInterfaces,
): String = resolveNonLoopbackIpv4Address(networkInterfaces)

fun stableRendererServiceUuid(installationId: String): String =
    UUID.nameUUIDFromBytes("newrapaw-dlna-$installationId".toByteArray(Charsets.UTF_8)).toString()

fun buildRendererServiceDlnaDeviceConfig(
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
