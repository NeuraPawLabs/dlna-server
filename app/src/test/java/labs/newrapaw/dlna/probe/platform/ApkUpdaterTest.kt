package labs.newrapaw.dlna.probe.platform

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.OkHttpClient

class ApkUpdaterTest {
    @Test
    fun validateApkDownloadAcceptsApkZipWithRootManifest() {
        val apkBytes = zipBytes(
            "AndroidManifest.xml" to "<manifest />".toByteArray(),
            "classes.dex" to byteArrayOf(1, 2, 3),
        )

        assertNull(validateApkDownload(apkBytes))
    }

    @Test
    fun validateApkDownloadRejectsGithubActionsArtifactZip() {
        val artifactBytes = zipBytes(
            "app-debug.apk" to zipBytes("AndroidManifest.xml" to "<manifest />".toByteArray()),
        )

        assertEquals(
            "Downloaded file is a zip archive, not an APK. GitHub Actions artifacts download as zip files; extract the APK first or use a GitHub Release APK asset URL.",
            validateApkDownload(artifactBytes),
        )
    }

    @Test
    fun validateApkDownloadRejectsHtmlDownloadPage() {
        val htmlBytes = "<html>GitHub</html>".toByteArray()

        assertEquals(
            "Downloaded file is not an APK. Check that the URL points directly to an .apk file.",
            validateApkDownload(htmlBytes),
        )
    }

    @Test
    fun validateApkDownloadRejectsEmptyBody() {
        assertEquals("APK download returned empty body", validateApkDownload(ByteArray(0)))
    }

    @Test
    fun apkDownloadClientCapsUnboundedCallTimeout() {
        val client = buildApkDownloadClient(OkHttpClient())

        assertEquals(15_000, client.callTimeoutMillis)
    }

    @Test
    fun apkDownloadClientPreservesShorterExistingTimeout() {
        val client = buildApkDownloadClient(
            OkHttpClient.Builder()
                .callTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )

        assertEquals(2_000, client.callTimeoutMillis)
    }

    @Test
    fun apkUpdaterUsesDedicatedDownloadClient() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/platform/ApkUpdater.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("private val downloadClient = buildApkDownloadClient(client)"))
        assertTrue(source.contains("downloadClient.newCall("))
        assertTrue(source.contains("buildApkDownloadClient("))
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
