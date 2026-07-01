package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLocalServerTest {
    @Test
    fun mediaPlaylistIncludesTargetDurationUsingLargestSegmentLength() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-main",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(slotIndex = 0, startMs = 0L, endMs = 4_000L, videoAssetId = "video-0"),
                TimelineSlot(slotIndex = 1, startMs = 4_000L, endMs = 10_100L, videoAssetId = "video-1"),
            ),
            includeEndList = true,
        )

        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:7"))
    }

    @Test
    fun mediaPlaylistOmitsEndListWhenSourceManifestIsOpenEnded() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-main",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(slotIndex = 0, startMs = 0L, endMs = 4_000L, videoAssetId = "video-0"),
            ),
            includeEndList = false,
        )

        assertFalse(playlist.contains("#EXT-X-ENDLIST"))
    }

    @Test
    fun mediaPlaylistReEmitsDiscontinuityBeforeMarkedSlot() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-main",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(slotIndex = 0, startMs = 0L, endMs = 4_000L, videoAssetId = "video-0"),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-1",
                    videoDiscontinuityBefore = true,
                ),
            ),
            includeEndList = true,
        )

        assertTrue(playlist.contains("#EXTINF:4.0,\n/session/session-1/asset/video-0"))
        assertTrue(playlist.contains("#EXT-X-DISCONTINUITY\n#EXTINF:4.0,\n/session/session-1/asset/video-1"))
    }

    @Test
    fun mediaPlaylistReEmitsMapAndKeyWhenSlotPrerequisitesChange() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-main",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-0",
                    prerequisiteAssetIds = listOf("init-video-main-0", "key-video-main-0"),
                    videoPrerequisiteAssetIdsByTrack = mapOf(
                        "video-main" to listOf("init-video-main-0", "key-video-main-0"),
                    ),
                ),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-1",
                    prerequisiteAssetIds = listOf("init-video-main-1", "key-video-main-1"),
                    videoPrerequisiteAssetIdsByTrack = mapOf(
                        "video-main" to listOf("init-video-main-1", "key-video-main-1"),
                    ),
                ),
            ),
            assetsById = mapOf(
                "video-0" to SessionAsset(
                    assetId = "video-0",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-0.ts",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "video-1" to SessionAsset(
                    assetId = "video-1",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-1.ts",
                    durationMs = 4_000L,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "init-video-main-0" to SessionAsset(
                    assetId = "init-video-main-0",
                    kind = SessionAssetKind.INIT_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/init-a.mp4",
                    durationMs = null,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "key-video-main-0" to SessionAsset(
                    assetId = "key-video-main-0",
                    kind = SessionAssetKind.KEY,
                    trackId = "video-main",
                    url = "https://example.com/key-a.bin",
                    durationMs = null,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                    keyMethod = "AES-128",
                    keyIv = "0x0001",
                ),
                "init-video-main-1" to SessionAsset(
                    assetId = "init-video-main-1",
                    kind = SessionAssetKind.INIT_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/init-b.mp4",
                    durationMs = null,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
                "key-video-main-1" to SessionAsset(
                    assetId = "key-video-main-1",
                    kind = SessionAssetKind.KEY,
                    trackId = "video-main",
                    url = "https://example.com/key-b.bin",
                    durationMs = null,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                    keyMethod = "AES-128",
                    keyIv = "0x0002",
                ),
            ),
            includeEndList = true,
        )

        assertTrue(
            playlist.contains(
                "#EXT-X-MAP:URI=\"/session/session-1/asset/init-video-main-0.mp4\"\n" +
                    "#EXT-X-KEY:METHOD=AES-128,URI=\"/session/session-1/asset/key-video-main-0.key\",IV=0x0001\n" +
                    "#EXTINF:4.0,\n" +
                    "/session/session-1/asset/video-0.ts",
            ),
        )
        assertTrue(
            playlist.contains(
                "#EXT-X-MAP:URI=\"/session/session-1/asset/init-video-main-1.mp4\"\n" +
                    "#EXT-X-KEY:METHOD=AES-128,URI=\"/session/session-1/asset/key-video-main-1.key\",IV=0x0002\n" +
                    "#EXTINF:4.0,\n" +
                    "/session/session-1/asset/video-1.ts",
            ),
        )
    }

    @Test
    fun audioPlaylistDoesNotFallBackToDifferentTrackAssetWhenRequestedTrackIsMissingInSlot() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "audio-alt",
            kind = SessionAssetKind.AUDIO_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-0",
                    audioAssetIds = listOf("audio-audio-main-0"),
                    audioPrerequisiteAssetIds = mapOf("audio-alt" to emptyList()),
                ),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-1",
                    audioAssetIds = listOf("audio-audio-alt-1"),
                    audioPrerequisiteAssetIds = mapOf("audio-alt" to emptyList()),
                ),
            ),
            assetsById = mapOf(
                "audio-audio-main-0" to SessionAsset(
                    assetId = "audio-audio-main-0",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "audio-main",
                    url = "https://example.com/audio-main-0.aac",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
                "audio-audio-alt-1" to SessionAsset(
                    assetId = "audio-audio-alt-1",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "audio-alt",
                    url = "https://example.com/audio-alt-1.aac",
                    durationMs = 4_000L,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
            ),
            includeEndList = true,
        )

        assertFalse(playlist.contains("/session/session-1/asset/audio-audio-main-0.aac"))
        assertTrue(playlist.contains("/session/session-1/asset/audio-audio-alt-1.aac"))
    }

    @Test
    fun subtitlePlaylistDoesNotFallBackToDifferentTrackAssetWhenRequestedTrackIsMissingInSlot() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "sub-alt",
            kind = SessionAssetKind.SUBTITLE_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-0",
                    subtitleAssetIds = listOf("subtitle-main-0"),
                    subtitlePrerequisiteAssetIds = mapOf("sub-alt" to emptyList()),
                ),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-1",
                    subtitleAssetIds = listOf("subtitle-sub-alt-1"),
                    subtitlePrerequisiteAssetIds = mapOf("sub-alt" to emptyList()),
                ),
            ),
            assetsById = mapOf(
                "subtitle-main-0" to SessionAsset(
                    assetId = "subtitle-main-0",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    trackId = "sub-main",
                    url = "https://example.com/sub-main-0.vtt",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = false,
                    requiredForStartup = false,
                    localPath = null,
                ),
                "subtitle-sub-alt-1" to SessionAsset(
                    assetId = "subtitle-sub-alt-1",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    trackId = "sub-alt",
                    url = "https://example.com/sub-alt-1.vtt",
                    durationMs = 4_000L,
                    sequence = 1,
                    blocking = false,
                    requiredForStartup = false,
                    localPath = null,
                ),
            ),
            includeEndList = true,
        )

        assertFalse(playlist.contains("/session/session-1/asset/subtitle-main-0.vtt"))
        assertTrue(playlist.contains("/session/session-1/asset/subtitle-sub-alt-1.vtt"))
    }

    @Test
    fun videoPlaylistDoesNotFallBackToPrimaryTrackAssetWhenRequestedVariantIsMissingInSlot() {
        val playlist = SessionLocalServer().buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-low",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-main-0",
                    videoAssetIdsByTrack = mapOf("video-main" to "video-main-0"),
                    videoPrerequisiteAssetIdsByTrack = mapOf("video-low" to emptyList()),
                ),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-main-1",
                    videoAssetIdsByTrack = mapOf(
                        "video-main" to "video-main-1",
                        "video-low" to "video-low-1",
                    ),
                    videoPrerequisiteAssetIdsByTrack = mapOf("video-low" to emptyList()),
                ),
            ),
            assetsById = mapOf(
                "video-main-0" to SessionAsset(
                    assetId = "video-main-0",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-main-0.ts",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "video-main-1" to SessionAsset(
                    assetId = "video-main-1",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-main-1.ts",
                    durationMs = 4_000L,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
                "video-low-1" to SessionAsset(
                    assetId = "video-low-1",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-low",
                    url = "https://example.com/video-low-1.ts",
                    durationMs = 4_000L,
                    sequence = 1,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
            ),
            includeEndList = true,
        )

        assertFalse(playlist.contains("/session/session-1/asset/video-main-0.ts"))
        assertFalse(playlist.contains("/session/session-1/asset/video-main-1.ts"))
        assertTrue(playlist.contains("/session/session-1/asset/video-low-1.ts"))
    }
}
