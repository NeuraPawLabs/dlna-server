package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityUiTextTest {
    @Test
    fun shellUsesStringResourcesForTitleAndManagementUrl() {
        val shellSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityShell.kt")),
            Charsets.UTF_8,
        )

        assertTrue(shellSource.contains("context.getString(R.string.main_activity_title)"))
        assertTrue(shellSource.contains("context.getString(R.string.main_activity_management_url, publicControlUrl)"))
        assertFalse(shellSource.contains("text = \"PawCast\""))
        assertFalse(shellSource.contains("text = \"管理页面: \$publicControlUrl\""))
    }

    @Test
    fun activityUsesStringResourceWhenRefreshingManagementUrl() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("R.string.main_activity_management_url"))
        assertFalse(activitySource.contains("runtime.shell.managementUrlView.text = \"管理页面:"))
    }

    @Test
    fun stringResourcesDefineMainActivityChromeText() {
        val stringsSource = String(
            Files.readAllBytes(Paths.get("src/main/res/values/strings.xml")),
            Charsets.UTF_8,
        )

        assertTrue(stringsSource.contains("name=\"main_activity_title\""))
        assertTrue(stringsSource.contains("name=\"main_activity_management_url\""))
    }
}
