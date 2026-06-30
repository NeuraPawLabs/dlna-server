package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityFullscreenTest {
    @Test
    fun playbackEntersFullscreenAndStopOrErrorExitsFullscreen() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityChrome.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("enterFullscreenPlayback()"))
        assertTrue(activitySource.contains("exitFullscreenPlayback()"))
        assertTrue(activitySource.contains("applyFullscreenChrome("))
        assertTrue(activitySource.contains("restoreWindowedChrome("))
        assertTrue(helperSource.contains("chromeViews.forEach { it.visibility = View.GONE }"))
        assertTrue(helperSource.contains("chromeViews.forEach { it.visibility = View.VISIBLE }"))
        assertTrue(helperSource.contains("View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY"))
    }

    @Test
    fun fullscreenUsesBlackBackground() {
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityChrome.kt")),
            Charsets.UTF_8,
        )

        assertTrue(helperSource.contains("rootView.setBackgroundColor(Color.BLACK)"))
        assertTrue(helperSource.contains("playerView.setBackgroundColor(Color.BLACK)"))
    }

    @Test
    fun backDuringFullscreenRequiresPlaybackExitConfirmation() {
        val activitySource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivity.kt")),
            Charsets.UTF_8,
        )
        val helperSource = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/ui/MainActivityNavigation.kt")),
            Charsets.UTF_8,
        )

        assertTrue(activitySource.contains("installMainActivityBackHandler("))
        assertTrue(helperSource.contains("onBackPressedDispatcher.addCallback"))
        assertTrue(helperSource.contains("if (isFullscreenPlayback())"))
        assertTrue(helperSource.contains("确认退出"))
        assertTrue(helperSource.contains("确认退出播放"))
        assertTrue(helperSource.contains("是否停止播放并返回主页面？"))
        assertTrue(helperSource.contains(".setPositiveButton(\"停止播放\")"))
    }
}
