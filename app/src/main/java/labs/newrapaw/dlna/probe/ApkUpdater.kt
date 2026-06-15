package labs.newrapaw.dlna.probe

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkUpdater(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
) {
    fun downloadAndLaunchInstaller(apkUrl: String) {
        Thread {
            runCatching {
                log("Downloading APK: $apkUrl")
                val response = client.newCall(Request.Builder().url(apkUrl).build()).execute()
                response.use {
                    check(it.isSuccessful) { "APK download failed: HTTP ${it.code}" }
                    val bytes = it.body?.bytes() ?: ByteArray(0)
                    check(bytes.isNotEmpty()) { "APK download returned empty body" }

                    val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val apkFile = File(updateDir, "newrapaw-dlna-probe-update.apk")
                    apkFile.writeBytes(bytes)
                    log("APK downloaded: ${apkFile.length()} bytes")
                    launchInstaller(apkFile)
                }
            }.onFailure {
                log("Update failed: ${it.message}")
            }
        }.start()
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        log("Installer launched")
    }
}
