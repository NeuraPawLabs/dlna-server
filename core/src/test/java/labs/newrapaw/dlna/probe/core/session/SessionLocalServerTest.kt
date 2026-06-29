package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLocalServerTest {
    @Test
    fun localManifestUsesSessionAssetRoutes() {
        val server = SessionLocalServer()
        val manifest = server.buildManifest(
            sessionId = "session-1",
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0,
                    endMs = 4_000,
                    videoAssetId = "video-0",
                    audioAssetIds = listOf("audio-0"),
                    subtitleAssetIds = listOf("subtitle-0"),
                    prerequisiteAssetIds = listOf("init-0", "key-0"),
                ),
            ),
            assetsById = mapOf(
                "video-0" to SessionAsset(
                    assetId = "video-0",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-0.ts",
                    durationMs = 4_000,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
            ),
        )

        assertTrue(manifest.contains("/session/session-1/asset/video-0.ts"))
        assertTrue(!manifest.contains("/session/session-1/asset/audio-0.ts"))
    }

    @Test
    fun masterManifestIncludesMediaTagsAndPointsToTrackPlaylists() {
        val server = SessionLocalServer()
        val manifest = server.buildMasterManifest(
            sessionId = "session-1",
            videoPlaylistPath = "/session/session-1/video.m3u8",
            audioTracks = listOf(
                SessionTrackManifest(
                    trackId = "audio-main",
                    name = "Main",
                    language = "zh",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    playlistPath = "/session/session-1/audio/audio-main.m3u8",
                ),
            ),
            subtitleTracks = listOf(
                SessionTrackManifest(
                    trackId = "sub-zh",
                    name = "ZH",
                    language = "zh",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    playlistPath = "/session/session-1/subtitle/sub-zh.m3u8",
                ),
            ),
        )

        assertTrue(manifest.contains("""#EXT-X-MEDIA:TYPE=AUDIO"""))
        assertTrue(manifest.contains("""URI="/session/session-1/audio/audio-main.m3u8""""))
        assertTrue(manifest.contains("""#EXT-X-MEDIA:TYPE=SUBTITLES"""))
        assertTrue(manifest.contains("""URI="/session/session-1/subtitle/sub-zh.m3u8""""))
        assertTrue(manifest.contains("/session/session-1/video.m3u8"))
    }

    @Test
    fun mediaPlaylistIncludesPrerequisitesBeforeSegments() {
        val server = SessionLocalServer()
        val playlist = server.buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "video-main",
            kind = SessionAssetKind.VIDEO_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0,
                    endMs = 4_000,
                    videoAssetId = "video-0",
                    prerequisiteAssetIds = listOf("init-0", "key-0"),
                ),
            ),
            assetsById = mapOf(
                "video-0" to SessionAsset(
                    assetId = "video-0",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video-0.ts",
                    durationMs = 4_000,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "init-0" to SessionAsset(
                    assetId = "init-0",
                    kind = SessionAssetKind.INIT_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/init-0.ts",
                    durationMs = null,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
                "key-0" to SessionAsset(
                    assetId = "key-0",
                    kind = SessionAssetKind.KEY,
                    trackId = "video-main",
                    url = "https://example.com/key-0",
                    durationMs = null,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
            ),
        )

        assertTrue(playlist.contains("""#EXT-X-MAP:URI="/session/session-1/asset/init-0.ts""""))
        assertTrue(playlist.contains("""#EXT-X-KEY:METHOD=NONE,URI="/session/session-1/asset/key-0.key""""))
        assertTrue(playlist.contains("/session/session-1/asset/video-0.ts"))
    }

    @Test
    fun routeBuildersReturnExpectedPaths() {
        val server = SessionLocalServer()

        assertEquals("/session/session-1/manifest.m3u8", server.masterManifestPath("session-1"))
        assertEquals("/session/session-1/video.m3u8", server.videoPlaylistPath("session-1"))
        assertEquals("/session/session-1/audio/audio-main.m3u8", server.trackPlaylistPath("session-1", SessionAssetKind.AUDIO_SEGMENT, "audio-main"))
        assertEquals("/session/session-1/subtitle/sub-zh.m3u8", server.trackPlaylistPath("session-1", SessionAssetKind.SUBTITLE_SEGMENT, "sub-zh"))
        assertEquals(
            "/session/session-1/asset/video-0.ts",
            server.assetPath(
                sessionId = "session-1",
                asset = SessionAsset(
                    assetId = "video-0",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    trackId = "video-main",
                    url = "https://example.com/video0.ts",
                    durationMs = 4_000,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                ),
            ),
        )
    }

    @Test
    fun mediaPlaylistUsesTrackSpecificKeyMetadata() {
        val server = SessionLocalServer()
        val playlist = server.buildMediaPlaylist(
            sessionId = "session-1",
            trackId = "audio-main",
            kind = SessionAssetKind.AUDIO_SEGMENT,
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0,
                    endMs = 4_000,
                    videoAssetId = "video-0",
                    audioAssetIds = listOf("audio-audio-main-0"),
                    prerequisiteAssetIds = listOf("init-video-main-0", "key-video-main-0"),
                    audioPrerequisiteAssetIds = mapOf(
                        "audio-main" to listOf("init-audio-main-0", "key-audio-main-0"),
                    ),
                ),
            ),
            assetsById = mapOf(
                "key-audio-main-0" to SessionAsset(
                    assetId = "key-audio-main-0",
                    kind = SessionAssetKind.KEY,
                    trackId = "audio-main",
                    url = "https://example.com/audio.key",
                    durationMs = null,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = true,
                    localPath = null,
                    keyMethod = "AES-128",
                    keyIv = "0x0002",
                ),
            ),
        )

        assertTrue(playlist.contains("""#EXT-X-KEY:METHOD=AES-128,URI="/session/session-1/asset/key-audio-main-0.key",IV=0x0002"""))
    }
}
