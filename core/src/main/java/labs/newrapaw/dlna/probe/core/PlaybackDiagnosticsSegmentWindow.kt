package labs.newrapaw.dlna.probe.core

internal data class PlaybackDiagnosticsSegmentWindow(
    val recentSamples: List<SegmentSample>,
    private val sampleLimit: Int,
) {
    fun record(sample: SegmentSample): PlaybackDiagnosticsSegmentWindow =
        copy(recentSamples = (recentSamples + sample).takeLast(sampleLimit))

    companion object {
        fun empty(sampleLimit: Int): PlaybackDiagnosticsSegmentWindow =
            PlaybackDiagnosticsSegmentWindow(
                recentSamples = emptyList(),
                sampleLimit = sampleLimit,
            )
    }
}
