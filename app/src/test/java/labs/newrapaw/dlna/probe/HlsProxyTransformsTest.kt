package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsProxyTransformsTest {
    @Test
    fun encodeDecodeRoundTripDoesNotLeakExtension() {
        val original = "https://cdn.example/path/seg0.png?token=abc"
        val encoded = labs.newrapaw.dlna.probe.core.encodeProxyUrl(original)

        assertFalse(encoded.contains(".png"))
        assertEquals(original, labs.newrapaw.dlna.probe.core.decodeProxyUrl(encoded))
    }

    @Test
    fun proxyUrlEncodingDoesNotUseAndroidApi26Base64() {
        val sourcePath = Paths.get("src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt")
        val source = String(Files.readAllBytes(sourcePath), Charsets.UTF_8)

        assertFalse(source.contains("java.util.Base64"))
    }

    @Test
    fun detectsVodManifestFromEndlistTag() {
        val manifest = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertTrue(labs.newrapaw.dlna.probe.core.isVodManifest(manifest))
    }

    @Test
    fun parseSingleVariantMasterManifestIncludesAudioAndSubtitleTracks() {
        val manifest = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Main",LANGUAGE="zh",DEFAULT=YES,URI="audio/index.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="ZH",LANGUAGE="zh",DEFAULT=YES,URI="subs/index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="audio",SUBTITLES="subs"
            video/index.m3u8
        """.trimIndent()

        val playlist = labs.newrapaw.dlna.probe.core.parseSingleVariantMasterManifest(
            manifest = manifest,
            manifestUrl = "https://example.com/master.m3u8",
        )

        assertEquals("https://example.com/video/index.m3u8", playlist?.variantUrl)
        assertEquals(1, playlist?.audioTracks?.size)
        assertEquals("https://example.com/audio/index.m3u8", playlist?.audioTracks?.single()?.uri)
        assertEquals(1, playlist?.subtitleTracks?.size)
        assertEquals("https://example.com/subs/index.m3u8", playlist?.subtitleTracks?.single()?.uri)
    }

    @Test
    fun parseSingleVariantMasterManifestRejectsMultipleVariants() {
        val manifest = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000
            video/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000
            video-hd/index.m3u8
        """.trimIndent()

        assertNull(
            labs.newrapaw.dlna.probe.core.parseSingleVariantMasterManifest(
                manifest = manifest,
                manifestUrl = "https://example.com/master.m3u8",
            ),
        )
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

        assertArrayEquals(ts, labs.newrapaw.dlna.probe.core.stripPngWrapperFromSegment(wrapper + ts))
    }

    @Test
    fun stripPngWrapperPassesThroughNormalSegments() {
        val ts = ByteArray(188 * 3) { 0xff.toByte() }
        ts[0] = 0x47
        ts[188] = 0x47
        ts[376] = 0x47

        assertArrayEquals(ts, labs.newrapaw.dlna.probe.core.stripPngWrapperFromSegment(ts))
    }
}
