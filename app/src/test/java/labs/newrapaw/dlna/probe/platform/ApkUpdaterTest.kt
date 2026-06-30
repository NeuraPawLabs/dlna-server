package labs.newrapaw.dlna.probe.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
