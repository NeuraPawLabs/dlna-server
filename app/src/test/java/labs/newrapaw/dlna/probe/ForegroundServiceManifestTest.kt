package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceManifestTest {
    @Test
    fun declaresRendererForegroundService() {
        val manifestPath = Paths.get("src/main/AndroidManifest.xml")
        val manifest = String(Files.readAllBytes(manifestPath), Charsets.UTF_8)

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android:name=\".platform.RendererForegroundService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
    }
}
