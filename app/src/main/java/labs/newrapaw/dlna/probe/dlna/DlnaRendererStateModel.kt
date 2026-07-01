package labs.newrapaw.dlna.probe.dlna

internal data class DlnaRendererStateModel(
    val currentUri: String = "",
    val currentUriMetadata: String = "",
    val transportState: String = "STOPPED",
    val transportStatus: String = "OK",
    val relativeTimePosition: String = "00:00:00",
    val mediaDurationMs: Long? = null,
    val volume: Int = 50,
    val muted: Boolean = false,
) {
    fun toSnapshot(): DlnaRendererSnapshot =
        DlnaRendererSnapshot(
            currentUri = currentUri,
            currentUriMetadata = currentUriMetadata,
            transportState = transportState,
            transportStatus = transportStatus,
            relativeTimePosition = relativeTimePosition,
            mediaDurationMs = mediaDurationMs,
            volume = volume,
            muted = muted,
        )
}
