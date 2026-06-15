package labs.newrapaw.dlna.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class UpstreamHttpTest {
    @Test
    fun formatsHttpFailureWithBodySnippet() {
        val message = formatUpstreamFailure(
            statusCode = 402,
            statusMessage = "Payment Required",
            body = "auth_key expired\nplease refresh url",
        )

        assertEquals("402 Payment Required: auth_key expired please refresh url", message)
    }

    @Test
    fun truncatesLongHttpFailureBody() {
        val message = formatUpstreamFailure(
            statusCode = 403,
            statusMessage = "Forbidden",
            body = "x".repeat(260),
        )

        assertEquals("403 Forbidden: ${"x".repeat(197)}...", message)
    }
}
