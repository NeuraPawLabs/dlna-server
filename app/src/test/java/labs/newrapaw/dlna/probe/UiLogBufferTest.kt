package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.ui.UiLogBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLogBufferTest {
    @Test
    fun retainsOnlyMostRecentThousandEntries() {
        val buffer = UiLogBuffer(maxEntries = 1000)

        repeat(1005) { index ->
            buffer.append("log-$index")
        }

        val snapshot = buffer.snapshot()
        assertEquals(1000, snapshot.size)
        assertEquals("log-5", snapshot.first())
        assertEquals("log-1004", snapshot.last())
    }
}
