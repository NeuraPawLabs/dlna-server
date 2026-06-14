package labs.newrapaw.dlna.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class ControlPageTest {
    @Test
    fun buildControlPageContainsPlayAndStopForms() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
        )

        assertTrue(html.contains("Honor Screen"))
        assertTrue(html.contains("Ready"))
        assertTrue(html.contains("textarea"))
        assertTrue(html.contains("name=\"url\""))
        assertTrue(html.contains("action=\"/control/play\""))
        assertTrue(html.contains("action=\"/control/stop\""))
    }

    @Test
    fun decodeFormUrlExtractsLongSignedUrl() {
        val original = "https://origin.example/object?filename=video.m3u8&X-Amz-Signature=abc+123"
        val body = "url=${URLEncoder.encode(original, "UTF-8")}"

        assertEquals(original, decodeFormUrl(body))
    }
}
