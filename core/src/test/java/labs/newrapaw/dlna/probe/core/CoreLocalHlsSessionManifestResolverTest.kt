package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsSessionManifestResolverTest {
    @Test
    fun resolveReturnsSourceManifestAsVideoManifestWhenInputIsNotMasterPlaylist() {
        val manifest = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val resolver = CoreLocalHlsSessionManifestResolver(
            fetchManifest = { url ->
                assertEquals("https://example.com/video.m3u8", url)
                manifest
            },
        )

        val resolved = resolver.resolve("https://example.com/video.m3u8")

        assertEquals("https://example.com/video.m3u8", resolved.videoManifestUrl)
        assertEquals(manifest, resolved.videoManifestBody)
        assertTrue(resolved.audioTracks.isEmpty())
        assertTrue(resolved.subtitleTracks.isEmpty())
        assertEquals(null, resolved.masterPlaylist)
    }

    @Test
    fun resolveFetchesVariantAudioAndSubtitleManifestsForSingleVariantMasterPlaylist() {
        val resolver = CoreLocalHlsSessionManifestResolver(
            fetchManifest = { url ->
                when (url) {
                    "https://example.com/master.m3u8" -> """
                        #EXTM3U
                        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Main",LANGUAGE="zh",DEFAULT=YES,URI="audio/index.m3u8"
                        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="ZH",LANGUAGE="zh",DEFAULT=YES,URI="subs/index.m3u8"
                        #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="audio",SUBTITLES="subs"
                        video/index.m3u8
                    """.trimIndent()
                    "https://example.com/video/index.m3u8" -> "#EXTM3U\n#EXTINF:4.0,\nseg-1.ts\n#EXT-X-ENDLIST"
                    "https://example.com/audio/index.m3u8" -> "#EXTM3U\n#EXTINF:4.0,\naudio-1.aac\n#EXT-X-ENDLIST"
                    "https://example.com/subs/index.m3u8" -> "#EXTM3U\n#EXTINF:4.0,\nsub-1.vtt\n#EXT-X-ENDLIST"
                    else -> error("unexpected manifest fetch: $url")
                }
            },
        )

        val resolved = resolver.resolve("https://example.com/master.m3u8")

        assertEquals("https://example.com/video/index.m3u8", resolved.videoManifestUrl)
        assertEquals(1, resolved.audioTracks.size)
        assertEquals(1, resolved.subtitleTracks.size)
        assertEquals(SessionAssetKind.AUDIO_SEGMENT, resolved.audioTracks.single().kind)
        assertEquals(SessionAssetKind.SUBTITLE_SEGMENT, resolved.subtitleTracks.single().kind)
        assertEquals("https://example.com/audio/index.m3u8", resolved.audioTracks.single().manifestUrl)
        assertEquals("https://example.com/subs/index.m3u8", resolved.subtitleTracks.single().manifestUrl)
        assertEquals("Main", resolved.masterPlaylist?.audioTracks?.single()?.name)
        assertEquals("ZH", resolved.masterPlaylist?.subtitleTracks?.single()?.name)
    }

    @Test(expected = UnsupportedSessionSourceException::class)
    fun resolveRejectsMultiVariantMasterPlaylist() {
        val resolver = CoreLocalHlsSessionManifestResolver(
            fetchManifest = {
                """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=800000
                    low/index.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=1600000
                    high/index.m3u8
                """.trimIndent()
            },
        )

        resolver.resolve("https://example.com/master.m3u8")
    }
}
