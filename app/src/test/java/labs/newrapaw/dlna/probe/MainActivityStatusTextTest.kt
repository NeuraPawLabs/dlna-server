package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.ui.mainActivityStatusTextFor
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStatusTextTest {
    @Test
    fun playingStatusMapsToChinesePlaybackText() {
        assertEquals("正在播放", mainActivityStatusTextFor("Playing"))
    }

    @Test
    fun bufferingStatusMapsToChineseBufferingText() {
        assertEquals("正在缓冲...", mainActivityStatusTextFor("Buffering"))
    }

    @Test
    fun pausedStatusMapsToChinesePausedText() {
        assertEquals("已暂停", mainActivityStatusTextFor("Paused"))
    }

    @Test
    fun readyLabelFallsBackToRawTextBecauseItIsNoLongerProduced() {
        assertEquals("Ready", mainActivityStatusTextFor("Ready"))
    }
}
