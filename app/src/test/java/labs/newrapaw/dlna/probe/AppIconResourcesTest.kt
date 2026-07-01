package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconResourcesTest {
    @Test
    fun adaptiveIconsDeclareMonochromeDrawable() {
        val launcher = String(
            Files.readAllBytes(Paths.get("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")),
            Charsets.UTF_8,
        )
        val roundLauncher = String(
            Files.readAllBytes(Paths.get("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")),
            Charsets.UTF_8,
        )

        assertTrue(launcher.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />"))
        assertTrue(roundLauncher.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />"))
    }

    @Test
    fun monochromeLauncherDrawableExists() {
        val monochrome = String(
            Files.readAllBytes(Paths.get("src/main/res/drawable/ic_launcher_monochrome.xml")),
            Charsets.UTF_8,
        )

        assertTrue(monochrome.contains("<vector"))
    }
}
