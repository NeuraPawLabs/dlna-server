package labs.newrapaw.dlna.probe.dlna

internal data class DlnaRendererSnapshot(
    val currentUri: String,
    val currentUriMetadata: String,
    val transportState: String,
    val transportStatus: String,
    val relativeTimePosition: String,
    val mediaDurationMs: Long? = null,
    val volume: Int,
    val muted: Boolean,
)

internal class DlnaRendererState {
    private val lock = Any()
    private var state = DlnaRendererStateModel()

    fun snapshot(): DlnaRendererSnapshot = synchronized(lock) { state.toSnapshot() }

    fun currentUri(): String = synchronized(lock) { state.currentUri }

    fun updateMedia(
        currentUri: String,
        currentUriMetadata: String,
        transportState: String,
        transportStatus: String,
        relativeTimePosition: String,
    ): DlnaRendererSnapshot = synchronized(lock) {
        state = state.copy(
            currentUri = currentUri,
            currentUriMetadata = currentUriMetadata,
            transportState = transportState,
            transportStatus = transportStatus,
            relativeTimePosition = relativeTimePosition,
            mediaDurationMs = null,
        )
        snapshot()
    }

    fun updateTransport(
        transportState: String,
        transportStatus: String,
        relativeTimePosition: String? = null,
        mediaDurationMs: Long? = state.mediaDurationMs,
    ): DlnaRendererSnapshot = synchronized(lock) {
        state = state.copy(
            transportState = transportState,
            transportStatus = transportStatus,
            relativeTimePosition = relativeTimePosition ?: state.relativeTimePosition,
            mediaDurationMs = mediaDurationMs,
        )
        snapshot()
    }

    fun updatePosition(relativeTimePosition: String): DlnaRendererSnapshot = synchronized(lock) {
        state = state.copy(relativeTimePosition = relativeTimePosition)
        snapshot()
    }

    fun updateRendering(volume: Int? = null, muted: Boolean? = null): DlnaRendererSnapshot = synchronized(lock) {
        state = state.copy(
            volume = volume ?: state.volume,
            muted = muted ?: state.muted,
        )
        snapshot()
    }
}
