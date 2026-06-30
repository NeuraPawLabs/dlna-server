package labs.newrapaw.dlna.probe.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

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
                    validateApkDownload(bytes)?.let { message -> error(message) }

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

internal fun validateApkDownload(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return "APK download returned empty body"

    return try {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var sawZipEntry = false
            while (true) {
                val entry = zip.nextEntry ?: break
                sawZipEntry = true
                if (entry.name == "AndroidManifest.xml") return null
            }
            if (sawZipEntry) {
                "Downloaded file is a zip archive, not an APK. GitHub Actions artifacts download as zip files; extract the APK first or use a GitHub Release APK asset URL."
            } else {
                "Downloaded file is not an APK. Check that the URL points directly to an .apk file."
            }
        }
    } catch (_: ZipException) {
        "Downloaded file is not an APK. Check that the URL points directly to an .apk file."
    }
}
