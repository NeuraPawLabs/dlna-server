package labs.newrapaw.dlna.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaEventingTest {
    @Test
    fun subscribeCreatesEventSubscriptionHeaders() {
        val response = buildEventSubscribeResponse()

        assertEquals(200, response.statusCode)
        assertTrue(response.headers["SID"].orEmpty().startsWith("uuid:"))
        assertEquals("Second-1800", response.headers["TIMEOUT"])
    }

    @Test
    fun unsubscribeReturnsOkWithoutBody() {
        val response = buildEventUnsubscribeResponse()

        assertEquals(200, response.statusCode)
        assertTrue(response.body.isEmpty())
    }
}
