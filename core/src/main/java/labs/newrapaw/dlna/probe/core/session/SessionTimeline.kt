package labs.newrapaw.dlna.probe.core.session

data class SessionTimeline(
    val slots: List<TimelineSlot> = emptyList(),
    val assets: List<SessionAsset> = emptyList(),
)

data class TimelineSlot(
    val slotIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val videoAssetId: String?,
    val videoDiscontinuityBefore: Boolean = false,
    val videoAssetIdsByTrack: Map<String, String> = emptyMap(),
    val videoPrerequisiteAssetIdsByTrack: Map<String, List<String>> = emptyMap(),
    val audioAssetIds: List<String> = emptyList(),
    val subtitleAssetIds: List<String> = emptyList(),
    val prerequisiteAssetIds: List<String> = emptyList(),
    val audioPrerequisiteAssetIds: Map<String, List<String>> = emptyMap(),
    val subtitlePrerequisiteAssetIds: Map<String, List<String>> = emptyMap(),
)

enum class SessionAssetKind {
    MANIFEST,
    VIDEO_SEGMENT,
    AUDIO_SEGMENT,
    SUBTITLE_SEGMENT,
    INIT_SEGMENT,
    KEY,
}

enum class SessionAssetState {
    NOT_STARTED,
    QUEUED,
    DOWNLOADING,
    READY,
    FAILED,
}

data class SessionAsset(
    val assetId: String,
    val kind: SessionAssetKind,
    val trackId: String?,
    val url: String,
    val durationMs: Long?,
    val sequence: Int?,
    val blocking: Boolean,
    val requiredForStartup: Boolean,
    val localPath: String?,
    val keyMethod: String? = null,
    val keyIv: String? = null,
    val downloadState: SessionAssetState = SessionAssetState.NOT_STARTED,
)

data class PlaybackTelemetrySnapshot(
    val playHeadSlotIndex: Int?,
    val bufferHeadSlotIndex: Int?,
    val isLoading: Boolean,
)

class PlaybackTelemetryBridge(
    private val slots: List<TimelineSlot>,
) {
    fun snapshot(
        currentPositionMs: Long,
        bufferedPositionMs: Long,
        isLoading: Boolean,
    ): PlaybackTelemetrySnapshot = PlaybackTelemetrySnapshot(
        playHeadSlotIndex = slots.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }?.slotIndex
            ?: slots.lastOrNull()?.slotIndex,
        bufferHeadSlotIndex = slots.firstOrNull { bufferedPositionMs >= it.startMs && bufferedPositionMs < it.endMs }?.slotIndex
            ?: slots.lastOrNull()?.slotIndex,
        isLoading = isLoading,
    )
}
