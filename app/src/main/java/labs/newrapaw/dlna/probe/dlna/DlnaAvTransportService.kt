package labs.newrapaw.dlna.probe.dlna

internal class DlnaAvTransportService(
    private val state: DlnaRendererState,
    private val log: (String) -> Unit,
    private val onPlayRequested: (String) -> Unit,
    private val onStopRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
) {
    fun handleAction(actionName: String, args: Map<String, String>): Map<String, Any> =
        when (actionName) {
            "SetAVTransportURI" -> {
                state.currentUri = args["CurrentURI"].orEmpty()
                state.currentUriMetadata = args["CurrentURIMetaData"].orEmpty()
                state.transportState = "STOPPED"
                state.transportStatus = "OK"
                state.relativeTimePosition = "00:00:00"
                log("[DLNA] Set URI: ${state.currentUri}")
                emptyMap()
            }
            "SetNextAVTransportURI" -> emptyMap()
            "Play" -> {
                if (state.currentUri.isBlank()) throw IllegalStateException("No current URI")
                onPlayRequested(state.currentUri)
                state.transportState = "PLAYING"
                state.transportStatus = "OK"
                log("[DLNA] Play: ${state.currentUri}")
                emptyMap()
            }
            "Pause" -> {
                onPauseRequested()
                state.transportState = "PAUSED_PLAYBACK"
                state.transportStatus = "OK"
                log("[DLNA] Pause")
                emptyMap()
            }
            "Stop" -> {
                onStopRequested()
                state.transportState = "STOPPED"
                state.transportStatus = "OK"
                state.relativeTimePosition = "00:00:00"
                log("[DLNA] Stop")
                emptyMap()
            }
            "Seek" -> {
                state.relativeTimePosition = args["Target"].orEmpty().ifBlank { "00:00:00" }
                log("[DLNA] Seek: ${state.relativeTimePosition}")
                emptyMap()
            }
            "GetTransportInfo" -> mapOf(
                "CurrentTransportState" to state.transportState,
                "CurrentTransportStatus" to state.transportStatus,
                "CurrentSpeed" to "1",
            )
            "GetMediaInfo" -> mapOf(
                "NrTracks" to 1,
                "MediaDuration" to "00:00:00",
                "CurrentURI" to state.currentUri,
                "CurrentURIMetaData" to state.currentUriMetadata,
                "NextURI" to "",
                "NextURIMetaData" to "",
                "PlayMedium" to "NETWORK",
                "RecordMedium" to "NOT_IMPLEMENTED",
                "WriteStatus" to "NOT_IMPLEMENTED",
            )
            "GetPositionInfo" -> mapOf(
                "Track" to 1,
                "TrackDuration" to "00:00:00",
                "TrackMetaData" to state.currentUriMetadata,
                "TrackURI" to state.currentUri,
                "RelTime" to state.relativeTimePosition,
                "AbsTime" to state.relativeTimePosition,
                "RelCount" to 0,
                "AbsCount" to 0,
            )
            "GetCurrentTransportActions" -> mapOf("Actions" to "Play,Stop,Pause,Seek")
            "GetDeviceCapabilities" -> mapOf(
                "PlayMedia" to "NETWORK",
                "RecMedia" to "NOT_IMPLEMENTED",
                "RecQualityModes" to "NOT_IMPLEMENTED",
            )
            "GetTransportSettings" -> mapOf(
                "PlayMode" to "NORMAL",
                "RecQualityMode" to "NOT_IMPLEMENTED",
            )
            else -> throw IllegalArgumentException("Unsupported AVTransport action $actionName")
        }
}
