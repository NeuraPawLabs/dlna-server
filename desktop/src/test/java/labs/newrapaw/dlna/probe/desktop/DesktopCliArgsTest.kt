package labs.newrapaw.dlna.probe.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCliArgsTest {
    @Test
    fun parsesPlayCommandWithUrl() {
        val args = DesktopCliArgs.parse(listOf("play", "https://example.com/video.m3u8"))

        assertEquals("https://example.com/video.m3u8", args.url)
        assertEquals(PlayerMode.AUTO, args.playerMode)
    }

    @Test
    fun rejectsRemovedDiagnosticsFlag() {
        val error = runCatching {
            DesktopCliArgs.parse(
                listOf(
                    "play",
                    "https://example.com/video.m3u8",
                    "--player=none",
                    "--diag",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("--diag") == true)
    }
}
