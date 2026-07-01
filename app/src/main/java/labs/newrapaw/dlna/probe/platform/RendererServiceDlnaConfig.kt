package labs.newrapaw.dlna.probe.platform

import android.content.Context
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.dlna.SsdpAdvertiser

fun buildRendererServiceDlnaConfigProvider(
    context: Context,
    publicBaseUrl: (String) -> String,
): () -> DlnaDeviceConfig? {
    val installationId = rendererInstallationId(context)
    val rendererUuid = stableRendererServiceUuid(installationId)
    return {
        buildRendererServiceDlnaDeviceConfig(
            localIpAddress = resolveRendererServiceIpAddress(),
            rendererUuid = rendererUuid,
            publicBaseUrl = publicBaseUrl,
        )
    }
}

fun startRendererServiceSsdp(
    context: Context,
    dlnaConfig: () -> DlnaDeviceConfig?,
    appendLog: (String) -> Unit,
): SsdpAdvertiser {
    return SsdpAdvertiser(
        context,
        dlnaConfig,
        appendLog,
    ).also {
        it.start()
        appendLog("DLNA renderer: PawCast")
    }
}
