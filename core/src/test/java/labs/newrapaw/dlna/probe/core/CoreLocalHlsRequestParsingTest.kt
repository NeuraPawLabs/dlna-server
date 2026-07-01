package labs.newrapaw.dlna.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsRequestParsingTest {
    @Test
    fun parseRequestLineExtractsMethodAndPath() {
        val parsed = parseRequestLine("GET /session/test/manifest.m3u8 HTTP/1.1")

        assertEquals("GET", parsed.method)
        assertEquals("/session/test/manifest.m3u8", parsed.path)
    }

    @Test
    fun parseRequestLineRejectsMalformedRequests() {
        val failure = runCatching {
            parseRequestLine("GET /missing-version")
        }.exceptionOrNull()

        assertTrue(failure is MalformedRequestException)
        assertTrue(failure?.message.orEmpty().contains("malformed request line"))
    }

    @Test
    fun parseContentLengthRejectsInvalidValues() {
        val failure = runCatching {
            parseContentLength("nope")
        }.exceptionOrNull()

        assertTrue(failure is MalformedRequestException)
        assertTrue(failure?.message.orEmpty().contains("invalid Content-Length"))
    }

    @Test
    fun parseByteRangeSupportsSingleAndSuffixRanges() {
        val explicit = parseByteRange("bytes=5-9") as ParsedByteRangeHeader.Single
        val suffix = parseByteRange("bytes=-4") as ParsedByteRangeHeader.Single

        assertEquals(5L, explicit.range.start)
        assertEquals(9L, explicit.range.endInclusive)
        assertEquals(null, suffix.range.start)
        assertEquals(4L, suffix.range.endInclusive)
    }

    @Test
    fun requestedByteRangeResolveRejectsUnsatisfiableRange() {
        val resolved = RequestedByteRange(start = 50L, endInclusive = 80L).resolve(totalLength = 10L)

        assertFalse(resolved.satisfiable)
    }
}
