package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHomeScreenTest {
    @Test
    fun homeScreenShowsOnlyCastingStatusAndManagementUrl() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("\"NewraPaw DLNA TV\""))
        assertTrue(source.contains("\"等待投屏...\""))
        assertTrue(source.contains("\"请在同一网络下的投屏设备中选择本设备\""))
        assertTrue(source.contains("管理页面:"))
        assertFalse(source.contains("\"m3u8 test URL\""))
        assertFalse(source.contains("\"Play Test Stream\""))
        assertFalse(source.contains("logView = TextView"))
    }
}
