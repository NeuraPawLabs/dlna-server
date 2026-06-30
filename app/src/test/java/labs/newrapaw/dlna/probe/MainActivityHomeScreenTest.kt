package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHomeScreenTest {
    @Test
    fun homeScreenShowsOnlyCastingStatusAndManagementUrl() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val shellSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityChrome.kt")),
            Charsets.UTF_8,
        )

        assertTrue(shellSource.contains("\"PawCast\""))
        assertTrue(shellSource.contains("\"播放\""))
        assertTrue(shellSource.contains("\"代理\""))
        assertTrue(shellSource.contains("\"日志\""))
        assertTrue(shellSource.contains("\"设置\""))
        assertTrue(shellSource.contains("LinearLayout.HORIZONTAL"))
        assertTrue(shellSource.contains("renderHomeContent("))
        assertTrue(helperSource.contains("\"等待投屏...\""))
        assertTrue(helperSource.contains("\"请在同一网络下的投屏设备中选择本设备\""))
        assertTrue(helperSource.contains("管理页面:"))
        assertFalse(shellSource.contains("\"m3u8 test URL\""))
        assertFalse(shellSource.contains("\"Play Test Stream\""))
        assertFalse(shellSource.contains("logView = TextView"))
    }

    @Test
    fun tvMenuItemsAreFocusableClickableAndSelectable() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("isFocusable = true"))
        assertTrue(source.contains("isClickable = true"))
        assertTrue(source.contains("setOnClickListener"))
        assertTrue(source.contains("setOnFocusChangeListener"))
        assertTrue(source.contains("selectMenuItem"))
        assertTrue(source.contains("if (hasFocus) selectMenuItem(item)"))
    }
}
