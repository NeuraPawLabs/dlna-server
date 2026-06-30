package labs.newrapaw.dlna.probe.ui

import androidx.appcompat.app.AppCompatActivity
import labs.newrapaw.dlna.probe.dlna.DlnaDeviceConfig
import labs.newrapaw.dlna.probe.dlna.SsdpAdvertiser

fun startMainActivitySsdp(
    activity: AppCompatActivity,
    dlnaConfig: () -> DlnaDeviceConfig?,
    appendLog: (String) -> Unit,
): SsdpAdvertiser {
    return SsdpAdvertiser(
        activity,
        dlnaConfig,
        appendLog,
    ).also {
        it.start()
        appendLog("DLNA renderer: PawCast")
    }
}
