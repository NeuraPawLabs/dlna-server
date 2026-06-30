package labs.newrapaw.dlna.probe.ui

import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig

fun buildMainActivityDlnaConfigProvider(
    activity: AppCompatActivity,
    publicBaseUrl: (String) -> String,
): () -> DlnaDeviceConfig? {
    val androidId = Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    val rendererUuid = stableRendererUuid(androidId)
    return {
        buildDlnaDeviceConfig(
            localIpAddress = resolveLocalIpAddress(),
            rendererUuid = rendererUuid,
            publicBaseUrl = publicBaseUrl,
        )
    }
}
