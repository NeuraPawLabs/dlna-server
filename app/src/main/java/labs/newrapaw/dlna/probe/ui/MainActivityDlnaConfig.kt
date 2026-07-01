package labs.newrapaw.dlna.probe.ui

import androidx.appcompat.app.AppCompatActivity
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.platform.rendererInstallationId

fun buildMainActivityDlnaConfigProvider(
    activity: AppCompatActivity,
    publicBaseUrl: (String) -> String,
): () -> DlnaDeviceConfig? {
    val installationId = rendererInstallationId(activity)
    val rendererUuid = stableRendererUuid(installationId)
    return {
        buildDlnaDeviceConfig(
            localIpAddress = resolveLocalIpAddress(),
            rendererUuid = rendererUuid,
            publicBaseUrl = publicBaseUrl,
        )
    }
}
