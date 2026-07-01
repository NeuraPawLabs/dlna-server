package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityManifestTest {
    @Test
    fun declaresTvConfigChangesForMainActivity() {
        val manifest = String(
            Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
            Charsets.UTF_8,
        )

        assertTrue(manifest.contains("android:name=\".ui.MainActivity\""))
        assertTrue(
            manifest.contains(
                "android:configChanges=\"orientation|screenSize|smallestScreenSize|keyboardHidden\"",
            ),
        )
    }

    @Test
    fun declaresWifiFeatureAsOptionalForTvDevices() {
        val manifest = String(
            Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
            Charsets.UTF_8,
        )

        assertTrue(
            manifest.contains(
                "android:name=\"android.hardware.wifi\"",
            ),
        )
        assertTrue(
            manifest.contains(
                "android:name=\"android.hardware.wifi\"\n        android:required=\"false\"",
            ),
        )
    }

    @Test
    fun declaresDataExtractionRulesForAndroid12AndAbove() {
        val manifest = String(
            Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
            Charsets.UTF_8,
        )

        assertTrue(
            manifest.contains(
                "android:dataExtractionRules=\"@xml/data_extraction_rules\"",
            ),
        )
        assertTrue(
            manifest.contains(
                "android:fullBackupContent=\"@xml/backup_rules\"",
            ),
        )
        assertTrue(
            manifest.contains(
                "tools:targetApi=\"24\"",
            ),
        )
    }
}
