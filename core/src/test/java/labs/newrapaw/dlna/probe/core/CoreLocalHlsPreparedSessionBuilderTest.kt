package labs.newrapaw.dlna.probe.core

import java.util.concurrent.Executors
import labs.newrapaw.dlna.probe.core.session.PlannedSessionTimeline
import labs.newrapaw.dlna.probe.core.session.PlannedTrackManifest
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer
import labs.newrapaw.dlna.probe.core.session.SessionTimeline
import labs.newrapaw.dlna.probe.core.session.TimelineSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsPreparedSessionBuilderTest {
    @Test
    fun buildPreparedSessionCreatesPlaylistsAssetRuntimeAndPrefetchController() {
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val builder = CoreLocalHlsPreparedSessionBuilder(
            sessionLocalServer = SessionLocalServer(),
            sessionPrefetchExecutor = prefetchExecutor,
        )
        val session = PlaybackSession(
            sessionId = "session-1",
            sourceUrl = "https://example.com/master.m3u8",
            entryManifestUrl = "https://example.com/master.m3u8",
            localRootDir = "session-1",
            createdAtMs = 1L,
            status = PlaybackSessionStatus.PREPARING,
            timeline = SessionTimeline(),
        )
        val plan = PlannedSessionTimeline(
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-0",
                    audioAssetIds = listOf("audio-a1-0"),
                    subtitleAssetIds = listOf("subtitle-s1-0"),
                ),
            ),
            assets = listOf(
                SessionAsset(
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
                SessionAsset(
                    assetId = "audio-a1-0",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    trackId = "a1",
                    url = "https://example.com/audio-0.aac",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = true,
                    requiredForStartup = false,
                    localPath = null,
                ),
                SessionAsset(
                    assetId = "subtitle-s1-0",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    trackId = "s1",
                    url = "https://example.com/sub-0.vtt",
                    durationMs = 4_000L,
                    sequence = 0,
                    blocking = false,
                    requiredForStartup = false,
                    localPath = null,
                ),
            ),
        )
        val audioTracks = listOf(
            PlannedTrackManifest(
                trackId = "a1",
                kind = SessionAssetKind.AUDIO_SEGMENT,
                manifestUrl = "https://example.com/audio/index.m3u8",
                manifestBody = "#EXTM3U",
            ),
        )
        val subtitleTracks = listOf(
            PlannedTrackManifest(
                trackId = "s1",
                kind = SessionAssetKind.SUBTITLE_SEGMENT,
                manifestUrl = "https://example.com/subs/index.m3u8",
                manifestBody = "#EXTM3U",
            ),
        )
        val manifestSet = ResolvedSessionManifestSet(
            masterPlaylist = HlsMasterPlaylist(
                videoVariants = listOf(
                    HlsVariantStream(
                        trackId = "video-main",
                        uri = "https://example.com/video/index.m3u8",
                        bandwidth = 1L,
                        averageBandwidth = null,
                        resolution = null,
                        codecs = null,
                        audioGroupId = "audio",
                        subtitleGroupId = "subs",
                    ),
                ),
                audioTracks = listOf(
                    HlsMediaTrack(
                        type = "AUDIO",
                        groupId = "audio",
                        name = "Main",
                        language = "zh",
                        uri = "https://example.com/audio/index.m3u8",
                        isDefault = true,
                    ),
                ),
                subtitleTracks = listOf(
                    HlsMediaTrack(
                        type = "SUBTITLES",
                        groupId = "subs",
                        name = "ZH",
                        language = "zh",
                        uri = "https://example.com/subs/index.m3u8",
                        isDefault = true,
                    ),
                ),
            ),
            primaryVideoTrackId = "video-main",
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = "#EXTM3U",
                ),
            ),
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
        )

        val prepared = try {
            builder.buildPreparedSession(
                session = session,
                manifestSet = manifestSet,
                plan = plan,
                prefetchConcurrency = 2,
            )
        } finally {
            prefetchExecutor.shutdownNow()
        }

        assertEquals(PlaybackSessionStatus.READY, prepared.session.status)
        assertTrue(prepared.masterManifest.contains("/session/session-1/audio/a1.m3u8"))
        assertTrue(prepared.masterManifest.contains("/session/session-1/subtitle/s1.m3u8"))
        assertTrue(prepared.videoPlaylist.contains("/session/session-1/asset/video-0"))
        assertEquals("video-main", prepared.primaryVideoTrackId)
        assertTrue(prepared.videoPlaylists.containsKey("video-main"))
        assertEquals(setOf("video-0", "audio-a1-0", "subtitle-s1-0"), prepared.assetRuntime.keys)
        assertEquals(listOf("video-0", "audio-a1-0", "subtitle-s1-0"), prepared.prefetchController.snapshotQueue())
    }

    @Test
    fun buildPreparedSessionPrioritizesLaterSlotInitKeyAndVideoBeforeAudioAndSubtitleInInitialQueue() {
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val builder = CoreLocalHlsPreparedSessionBuilder(
            sessionLocalServer = SessionLocalServer(),
            sessionPrefetchExecutor = prefetchExecutor,
        )
        val session = PlaybackSession(
            sessionId = "session-1",
            sourceUrl = "https://example.com/master.m3u8",
            entryManifestUrl = "https://example.com/master.m3u8",
            localRootDir = "session-1",
            createdAtMs = 1L,
            status = PlaybackSessionStatus.PREPARING,
            timeline = SessionTimeline(),
        )
        val plan = PlannedSessionTimeline(
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0L,
                    endMs = 4_000L,
                    videoAssetId = "video-0",
                    prerequisiteAssetIds = listOf("init-0", "key-0"),
                    audioAssetIds = listOf("audio-a1-0"),
                    subtitleAssetIds = listOf("subtitle-s1-0"),
                ),
                TimelineSlot(
                    slotIndex = 1,
                    startMs = 4_000L,
                    endMs = 8_000L,
                    videoAssetId = "video-1",
                    prerequisiteAssetIds = listOf("init-1", "key-1"),
                    audioAssetIds = listOf("audio-a1-1"),
                    subtitleAssetIds = listOf("subtitle-s1-1"),
                ),
            ),
            assets = listOf(
                SessionAsset("init-0", SessionAssetKind.INIT_SEGMENT, "video-main", "https://example.com/init-0.mp4", null, 0, true, true, null),
                SessionAsset("key-0", SessionAssetKind.KEY, "video-main", "https://example.com/key-0.key", null, 0, true, true, null),
                SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video-main", "https://example.com/video-0.ts", 4_000L, 0, true, true, null),
                SessionAsset("audio-a1-0", SessionAssetKind.AUDIO_SEGMENT, "a1", "https://example.com/audio-0.aac", 4_000L, 0, true, false, null),
                SessionAsset("subtitle-s1-0", SessionAssetKind.SUBTITLE_SEGMENT, "s1", "https://example.com/sub-0.vtt", 4_000L, 0, false, false, null),
                SessionAsset("init-1", SessionAssetKind.INIT_SEGMENT, "video-main", "https://example.com/init-1.mp4", null, 1, true, false, null),
                SessionAsset("key-1", SessionAssetKind.KEY, "video-main", "https://example.com/key-1.key", null, 1, true, false, null),
                SessionAsset("video-1", SessionAssetKind.VIDEO_SEGMENT, "video-main", "https://example.com/video-1.ts", 4_000L, 1, true, false, null),
                SessionAsset("audio-a1-1", SessionAssetKind.AUDIO_SEGMENT, "a1", "https://example.com/audio-1.aac", 4_000L, 1, true, false, null),
                SessionAsset("subtitle-s1-1", SessionAssetKind.SUBTITLE_SEGMENT, "s1", "https://example.com/sub-1.vtt", 4_000L, 1, false, false, null),
            ),
        )
        val manifestSet = ResolvedSessionManifestSet(
            masterPlaylist = null,
            primaryVideoTrackId = "video-main",
            videoTracks = listOf(
                PlannedTrackManifest(
                    trackId = "video-main",
                    kind = SessionAssetKind.VIDEO_SEGMENT,
                    manifestUrl = "https://example.com/video/index.m3u8",
                    manifestBody = "#EXTM3U",
                ),
            ),
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        )

        val prepared = try {
            builder.buildPreparedSession(
                session = session,
                manifestSet = manifestSet,
                plan = plan,
                prefetchConcurrency = 2,
            )
        } finally {
            prefetchExecutor.shutdownNow()
        }

        assertEquals(
            listOf(
                "init-0",
                "key-0",
                "video-0",
                "audio-a1-0",
                "subtitle-s1-0",
                "init-1",
                "key-1",
                "video-1",
                "audio-a1-1",
                "subtitle-s1-1",
            ),
            prepared.prefetchController.snapshotQueue(),
        )
    }
}
