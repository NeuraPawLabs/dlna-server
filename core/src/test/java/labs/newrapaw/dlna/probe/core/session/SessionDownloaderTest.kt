package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDownloaderTest {
    @Test
    fun startupAssetsAreScheduledBeforeFarTailVideoAssets() {
        val assets = listOf(
            SessionAsset("video-8", SessionAssetKind.VIDEO_SEGMENT, "video", "u8", 4_000, 8, true, false, null),
            SessionAsset("init-0", SessionAssetKind.INIT_SEGMENT, null, "init", null, 0, true, true, null),
            SessionAsset("key-0", SessionAssetKind.KEY, null, "key", null, 0, true, true, null),
            SessionAsset("audio-0", SessionAssetKind.AUDIO_SEGMENT, "audio", "a0", 4_000, 0, true, false, null),
            SessionAsset("subtitle-0", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s0", 4_000, 0, false, false, null),
            SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video", "u0", 4_000, 0, true, true, null),
        )

        val scheduled = SessionDownloader.planStartupQueue(assets).map { it.assetId }

        assertEquals(listOf("init-0", "key-0", "video-0", "audio-0", "subtitle-0", "video-8"), scheduled)
    }

    @Test
    fun startupQueueKeepsVideoStartupAssetsAheadOfAudioAndSubtitleAssets() {
        val assets = listOf(
            SessionAsset("audio-0", SessionAssetKind.AUDIO_SEGMENT, "audio", "a0", 4_000, 0, true, false, null),
            SessionAsset("subtitle-0", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s0", 4_000, 0, false, false, null),
            SessionAsset("video-1", SessionAssetKind.VIDEO_SEGMENT, "video", "u1", 4_000, 1, true, true, null),
            SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video", "u0", 4_000, 0, true, true, null),
            SessionAsset("init-video", SessionAssetKind.INIT_SEGMENT, "video", "init", null, 0, true, true, null),
            SessionAsset("key-video", SessionAssetKind.KEY, "video", "key", null, 0, true, true, null),
        )

        val scheduled = SessionDownloader.planStartupQueue(assets).map { it.assetId }

        assertEquals(listOf("init-video", "key-video", "video-0", "video-1", "audio-0", "subtitle-0"), scheduled)
    }

    @Test
    fun playheadQueuePrioritizesCurrentAndForwardSlotsAfterSeek() {
        val slots = listOf(
            TimelineSlot(
                slotIndex = 0,
                startMs = 0,
                endMs = 4_000,
                videoAssetId = "video-0",
                audioAssetIds = listOf("audio-0"),
                subtitleAssetIds = listOf("sub-0"),
                prerequisiteAssetIds = listOf("init-0", "key-0"),
            ),
            TimelineSlot(
                slotIndex = 1,
                startMs = 4_000,
                endMs = 8_000,
                videoAssetId = "video-1",
                audioAssetIds = listOf("audio-1"),
                subtitleAssetIds = listOf("sub-1"),
                prerequisiteAssetIds = listOf("init-0", "key-0"),
            ),
            TimelineSlot(
                slotIndex = 2,
                startMs = 8_000,
                endMs = 12_000,
                videoAssetId = "video-2",
                audioAssetIds = listOf("audio-2"),
                subtitleAssetIds = listOf("sub-2"),
                prerequisiteAssetIds = listOf("init-0", "key-0"),
            ),
        )
        val assets = listOf(
            SessionAsset("init-0", SessionAssetKind.INIT_SEGMENT, null, "init", null, 0, true, true, null),
            SessionAsset("key-0", SessionAssetKind.KEY, null, "key", null, 0, true, true, null),
            SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video", "v0", 4_000, 0, true, true, null),
            SessionAsset("audio-0", SessionAssetKind.AUDIO_SEGMENT, "audio", "a0", 4_000, 0, true, true, null),
            SessionAsset("sub-0", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s0", 4_000, 0, false, true, null),
            SessionAsset("video-1", SessionAssetKind.VIDEO_SEGMENT, "video", "v1", 4_000, 1, true, true, null),
            SessionAsset("audio-1", SessionAssetKind.AUDIO_SEGMENT, "audio", "a1", 4_000, 1, true, true, null),
            SessionAsset("sub-1", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s1", 4_000, 1, false, true, null),
            SessionAsset("video-2", SessionAssetKind.VIDEO_SEGMENT, "video", "v2", 4_000, 2, true, false, null),
            SessionAsset("audio-2", SessionAssetKind.AUDIO_SEGMENT, "audio", "a2", 4_000, 2, true, false, null),
            SessionAsset("sub-2", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s2", 4_000, 2, false, false, null),
        ).associateBy { it.assetId }

        val queue = SessionDownloader.planPlaybackQueue(
            slots = slots,
            assetsById = assets,
            playHeadSlotIndex = 1,
            readyAssetIds = setOf("init-0", "key-0"),
        )

        assertEquals(
            listOf("video-1", "audio-1", "sub-1", "video-2", "audio-2", "sub-2"),
            queue,
        )
    }

    @Test
    fun playheadQueueIncludesPerSlotPrerequisitesBeforeLaterSlotMedia() {
        val slots = listOf(
            TimelineSlot(
                slotIndex = 0,
                startMs = 0,
                endMs = 4_000,
                videoAssetId = "video-0",
                audioAssetIds = listOf("audio-0"),
                subtitleAssetIds = emptyList(),
                prerequisiteAssetIds = listOf("init-0", "key-0"),
            ),
            TimelineSlot(
                slotIndex = 1,
                startMs = 4_000,
                endMs = 8_000,
                videoAssetId = "video-1",
                audioAssetIds = listOf("audio-1"),
                subtitleAssetIds = emptyList(),
                prerequisiteAssetIds = listOf("init-1", "key-1"),
            ),
        )
        val assets = listOf(
            SessionAsset("init-0", SessionAssetKind.INIT_SEGMENT, null, "init-0", null, 0, true, true, null),
            SessionAsset("key-0", SessionAssetKind.KEY, null, "key-0", null, 0, true, true, null),
            SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video", "v0", 4_000, 0, true, true, null),
            SessionAsset("audio-0", SessionAssetKind.AUDIO_SEGMENT, "audio", "a0", 4_000, 0, true, true, null),
            SessionAsset("init-1", SessionAssetKind.INIT_SEGMENT, null, "init-1", null, 1, true, false, null),
            SessionAsset("key-1", SessionAssetKind.KEY, null, "key-1", null, 1, true, false, null),
            SessionAsset("video-1", SessionAssetKind.VIDEO_SEGMENT, "video", "v1", 4_000, 1, true, false, null),
            SessionAsset("audio-1", SessionAssetKind.AUDIO_SEGMENT, "audio", "a1", 4_000, 1, true, false, null),
        ).associateBy { it.assetId }

        val queue = SessionDownloader.planPlaybackQueue(
            slots = slots,
            assetsById = assets,
            playHeadSlotIndex = 1,
            readyAssetIds = setOf("init-0", "key-0", "video-0", "audio-0"),
        )

        assertEquals(
            listOf("init-1", "key-1", "video-1", "audio-1"),
            queue,
        )
    }

    @Test
    fun playheadQueueIncludesAlternateVideoTrackAssetsAndPrerequisitesForForwardSlots() {
        val slots = listOf(
            TimelineSlot(
                slotIndex = 1,
                startMs = 4_000,
                endMs = 8_000,
                videoAssetId = "video-main-1",
                videoAssetIdsByTrack = linkedMapOf(
                    "video-main" to "video-main-1",
                    "video-low" to "video-low-1",
                ),
                videoPrerequisiteAssetIdsByTrack = linkedMapOf(
                    "video-main" to listOf("init-main-1", "key-main-1"),
                    "video-low" to listOf("init-low-1", "key-low-1"),
                ),
                prerequisiteAssetIds = listOf("init-main-1", "key-main-1"),
            ),
        )
        val assets = listOf(
            SessionAsset("init-main-1", SessionAssetKind.INIT_SEGMENT, "video-main", "init-main", null, 1, true, false, null),
            SessionAsset("key-main-1", SessionAssetKind.KEY, "video-main", "key-main", null, 1, true, false, null),
            SessionAsset("video-main-1", SessionAssetKind.VIDEO_SEGMENT, "video-main", "video-main", 4_000, 1, true, false, null),
            SessionAsset("init-low-1", SessionAssetKind.INIT_SEGMENT, "video-low", "init-low", null, 1, true, false, null),
            SessionAsset("key-low-1", SessionAssetKind.KEY, "video-low", "key-low", null, 1, true, false, null),
            SessionAsset("video-low-1", SessionAssetKind.VIDEO_SEGMENT, "video-low", "video-low", 4_000, 1, true, false, null),
        ).associateBy { it.assetId }

        val queue = SessionDownloader.planPlaybackQueue(
            slots = slots,
            assetsById = assets,
            playHeadSlotIndex = 1,
            readyAssetIds = emptySet(),
        )

        assertEquals(
            listOf(
                "init-main-1",
                "init-low-1",
                "key-main-1",
                "key-low-1",
                "video-main-1",
                "video-low-1",
            ),
            queue,
        )
    }
}
