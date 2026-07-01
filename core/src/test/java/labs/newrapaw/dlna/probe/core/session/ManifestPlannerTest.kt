package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPlannerTest {
    @Test
    fun plannerBuildsSlotsAndAssetsForVideoAudioSubtitleInitAndKey() {
        val manifest = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="enc.key"
            #EXTINF:4.0,
            video-1.ts
            #EXTINF:4.0,
            video-2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = manifest,
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = listOf(
                PlannedTrackManifest(
                    trackId = "audio-main",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = "https://example.com/audio/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXTINF:4.0,
                        audio-1.aac
                        #EXTINF:4.0,
                        audio-2.aac
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            subtitleTracks = listOf(
                PlannedTrackManifest(
                    trackId = "sub-zh",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    manifestUrl = "https://example.com/subs/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXTINF:4.0,
                        sub-1.vtt
                        #EXTINF:4.0,
                        sub-2.vtt
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(2, plan.slots.size)
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.INIT_SEGMENT })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.KEY })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.AUDIO_SEGMENT })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.SUBTITLE_SEGMENT })
        assertEquals(0L, plan.slots[0].startMs)
        assertEquals(4_000L, plan.slots[0].endMs)
    }

    @Test
    fun plannerPreservesKeyMethodIvAndTrackSpecificPrerequisites() {
        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="video-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="video.key",IV=0x0001
                        #EXTINF:4.0,
                        video-1.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = listOf(
                PlannedTrackManifest(
                    trackId = "audio-main",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = "https://example.com/audio/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="audio-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="audio.key",IV=0x0002
                        #EXTINF:4.0,
                        audio-1.aac
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            subtitleTracks = emptyList(),
        )

        val videoKey = plan.assets.firstOrNull { it.assetId == "key-video-main-0" }
        val audioKey = plan.assets.firstOrNull { it.assetId == "key-audio-main-0" }
        val audioInit = plan.assets.firstOrNull { it.assetId == "init-audio-main-0" }
        val slot = plan.slots.single()

        assertNotNull(videoKey)
        assertEquals("AES-128", videoKey?.keyMethod)
        assertEquals("0x0001", videoKey?.keyIv)
        assertNotNull(audioKey)
        assertEquals("AES-128", audioKey?.keyMethod)
        assertEquals("0x0002", audioKey?.keyIv)
        assertNotNull(audioInit)
        assertTrue(slot.prerequisiteAssetIds.contains("init-video-main-0"))
        assertTrue(slot.prerequisiteAssetIds.contains("key-video-main-0"))
        assertTrue(slot.audioPrerequisiteAssetIds["audio-main"]?.contains("init-audio-main-0") == true)
        assertTrue(slot.audioPrerequisiteAssetIds["audio-main"]?.contains("key-audio-main-0") == true)
    }

    @Test
    fun startupGateOnlyDependsOnVideoStartupAssets() {
        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="video-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="video.key"
                        #EXTINF:4.0,
                        video-1.ts
                        #EXTINF:4.0,
                        video-2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = listOf(
                PlannedTrackManifest(
                    trackId = "audio-main",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = "https://example.com/audio/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="audio-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="audio.key"
                        #EXTINF:4.0,
                        audio-1.aac
                        #EXTINF:4.0,
                        audio-2.aac
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            subtitleTracks = listOf(
                PlannedTrackManifest(
                    trackId = "sub-zh",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    manifestUrl = "https://example.com/subs/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="sub-init.vtt"
                        #EXTINF:4.0,
                        sub-1.vtt
                        #EXTINF:4.0,
                        sub-2.vtt
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
        )

        val startupAssets = plan.assets.filter { it.requiredForStartup }

        assertTrue(startupAssets.any { it.assetId == "init-video-main-0" })
        assertTrue(startupAssets.any { it.assetId == "key-video-main-0" })
        assertTrue(startupAssets.any { it.assetId == "video-0" })
        assertTrue(startupAssets.any { it.assetId == "video-1" })
        assertTrue(startupAssets.none { it.assetId.startsWith("audio-") })
        assertTrue(startupAssets.none { it.assetId.startsWith("subtitle-") })
        assertTrue(startupAssets.none { it.assetId == "init-audio-main-0" })
        assertTrue(startupAssets.none { it.assetId == "key-audio-main-0" })
        assertTrue(startupAssets.none { it.assetId == "init-sub-zh-0" })
    }

    @Test
    fun plannerPreservesDiscontinuityBoundariesPerSlot() {
        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXTINF:4.0,
                        video-1.ts
                        #EXT-X-DISCONTINUITY
                        #EXTINF:4.0,
                        video-2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        )

        assertEquals(2, plan.slots.size)
        assertTrue(plan.slots[0].videoDiscontinuityBefore.not())
        assertTrue(plan.slots[1].videoDiscontinuityBefore)
    }

    @Test
    fun plannerTracksPerSlotMapAndKeyRotations() {
        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="init-a.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="key-a.bin",IV=0x0001
                        #EXTINF:4.0,
                        video-1.ts
                        #EXT-X-MAP:URI="init-b.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="key-b.bin",IV=0x0002
                        #EXTINF:4.0,
                        video-2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        )

        assertEquals(listOf("init-video-main-0", "key-video-main-0"), plan.slots[0].prerequisiteAssetIds)
        assertEquals(listOf("init-video-main-1", "key-video-main-1"), plan.slots[1].prerequisiteAssetIds)
        assertEquals("https://example.com/video/init-b.mp4", plan.assets.first { it.assetId == "init-video-main-1" }.url)
        assertEquals("https://example.com/video/key-b.bin", plan.assets.first { it.assetId == "key-video-main-1" }.url)
        assertEquals("0x0002", plan.assets.first { it.assetId == "key-video-main-1" }.keyIv)
    }

    @Test
    fun startupAssetsIncludeInitialSegmentsAndPrerequisitesForAllVideoVariants() {
        val plan = ManifestPlanner().plan(
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/main.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="main-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="main.key"
                        #EXTINF:4.0,
                        main-1.ts
                        #EXTINF:4.0,
                        main-2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
                PlannedTrackManifest(
                    trackId = "video-low",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/low.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXT-X-MAP:URI="low-init.mp4"
                        #EXT-X-KEY:METHOD=AES-128,URI="low.key"
                        #EXTINF:4.0,
                        low-1.ts
                        #EXTINF:4.0,
                        low-2.ts
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            primaryVideoTrackId = "video-main",
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        )

        val startupAssets = plan.assets.filter { it.requiredForStartup }.map { it.assetId }.toSet()

        assertTrue(startupAssets.contains("init-video-main-0"))
        assertTrue(startupAssets.contains("key-video-main-0"))
        assertTrue(startupAssets.contains("video-0"))
        assertTrue(startupAssets.contains("video-1"))
        assertTrue(startupAssets.contains("init-video-low-0"))
        assertTrue(startupAssets.contains("key-video-low-0"))
        assertTrue(startupAssets.contains("video-video-low-0"))
        assertTrue(startupAssets.contains("video-video-low-1"))
    }
}
