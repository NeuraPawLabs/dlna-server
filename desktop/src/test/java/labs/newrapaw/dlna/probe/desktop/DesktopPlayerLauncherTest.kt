package labs.newrapaw.dlna.probe.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPlayerLauncherTest {
    @Test
    fun choosesMpvFirstInAutoMode() {
        val launcher = DesktopPlayerLauncher(
            commandExists = { it == "mpv" || it == "vlc" },
            spawn = { command -> command },
        )

        val result = launcher.launch(PlayerMode.AUTO, "http://127.0.0.1:43000/session/1/manifest.m3u8")

        assertEquals(listOf("mpv", "http://127.0.0.1:43000/session/1/manifest.m3u8"), result)
    }

    @Test
    fun fallsBackToVlcWhenMpvMissing() {
        val launcher = DesktopPlayerLauncher(
            commandExists = { it == "vlc" },
            spawn = { command -> command },
        )

        val result = launcher.launch(PlayerMode.AUTO, "http://127.0.0.1:43000/session/1/manifest.m3u8")

        assertEquals(listOf("vlc", "http://127.0.0.1:43000/session/1/manifest.m3u8"), result)
    }
}
