package labs.newrapaw.dlna.probe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsProxyTransformsTest {
    @Test
    fun encodeDecodeRoundTripDoesNotLeakExtension() {
        val original = "https://cdn.example/path/seg0.png?token=abc"
        val encoded = encodeProxyUrl(original)

        assertFalse(encoded.contains(".png"))
        assertEquals(original, decodeProxyUrl(encoded))
    }

    @Test
    fun rewriteManifestRoutesAbsoluteAndRelativeSegmentsThroughProxy() {
        val manifest = """
            #EXTM3U
            #EXTINF:6.0,
            https://cdn.example/path/seg0.png
            #EXTINF:6.0,
            relative/seg1.png
            #EXT-X-ENDLIST
        """.trimIndent()

        val rewritten = rewriteHlsManifest(
            manifest = manifest,
            manifestUrl = "https://origin.example/video/index.m3u8?token=abc",
            proxyBaseUrl = "http://127.0.0.1:49152",
        )

        assertTrue(rewritten.contains("http://127.0.0.1:49152/proxy/segment.ts?u="))
        assertFalse(rewritten.contains("cdn.example"))
        assertFalse(rewritten.lines().filter { !it.startsWith("#") }.any { it.contains(".png") })
    }

    @Test
    fun stripPngWrapperReturnsBytesStartingAtTsSync() {
        val wrapper = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x00,
        )
        val ts = ByteArray(188 * 3) { 0xff.toByte() }
        ts[0] = 0x47
        ts[188] = 0x47
        ts[376] = 0x47

        assertArrayEquals(ts, stripPngWrapperFromSegment(wrapper + ts))
    }

    @Test
    fun stripPngWrapperPassesThroughNormalSegments() {
        val ts = ByteArray(188 * 3) { 0xff.toByte() }
        ts[0] = 0x47
        ts[188] = 0x47
        ts[376] = 0x47

        assertArrayEquals(ts, stripPngWrapperFromSegment(ts))
    }
}
