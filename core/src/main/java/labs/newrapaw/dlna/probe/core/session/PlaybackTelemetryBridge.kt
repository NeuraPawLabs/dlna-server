package labs.newrapaw.dlna.probe.core.session

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
