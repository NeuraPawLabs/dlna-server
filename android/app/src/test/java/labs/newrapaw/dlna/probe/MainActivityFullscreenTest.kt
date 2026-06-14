package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityFullscreenTest {
    @Test
    fun playbackEntersFullscreenAndStopOrErrorExitsFullscreen() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("enterFullscreenPlayback()"))
        assertTrue(source.contains("exitFullscreenPlayback()"))
        assertTrue(source.contains("chromeViews.forEach { it.visibility = View.GONE }"))
        assertTrue(source.contains("chromeViews.forEach { it.visibility = View.VISIBLE }"))
        assertTrue(source.contains("View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY"))
    }

    @Test
    fun fullscreenUsesBlackBackground() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("rootView.setBackgroundColor(Color.BLACK)"))
        assertTrue(source.contains("playerView.setBackgroundColor(Color.BLACK)"))
    }

    @Test
    fun backDuringFullscreenRequiresPlaybackExitConfirmation() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertTrue(source.contains("onBackPressedDispatcher.addCallback"))
        assertTrue(source.contains("handleBackPressed()"))
        assertTrue(source.contains("if (isFullscreenPlayback)"))
        assertTrue(source.contains("showPlaybackExitConfirmation()"))
        assertTrue(source.contains("showExitConfirmation()"))
        assertTrue(source.contains("确认退出"))
        assertTrue(source.contains("确认退出播放"))
        assertTrue(source.contains("是否停止播放并返回主页面？"))
        assertTrue(source.contains(".setPositiveButton(\"停止播放\")"))
    }
}
