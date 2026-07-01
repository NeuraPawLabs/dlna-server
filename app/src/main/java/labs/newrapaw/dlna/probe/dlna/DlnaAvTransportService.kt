package labs.newrapaw.dlna.probe.dlna

import java.math.BigDecimal
import java.math.RoundingMode

internal class DlnaAvTransportService(
    private val state: DlnaRendererState,
    private val log: (String) -> Unit,
    private val onPlayRequested: (String) -> Unit,
    private val onStopRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onSeekRequested: (Long) -> Unit,
    private val onStateChanged: (DlnaRendererSnapshot) -> Unit = {},
) {
    fun handleAction(actionName: String, args: Map<String, String>): Map<String, Any> =
        when (actionName) {
            "SetAVTransportURI" -> {
                val uri = args["CurrentURI"].orEmpty()
                onStateChanged(
                    state.updateMedia(
                    currentUri = uri,
                    currentUriMetadata = args["CurrentURIMetaData"].orEmpty(),
                    transportState = "STOPPED",
                    transportStatus = "OK",
                    relativeTimePosition = "00:00:00",
                    ),
                )
                log("[DLNA] Set URI: $uri")
                emptyMap()
            }
            "SetNextAVTransportURI" -> emptyMap()
            "Play" -> {
                val currentUri = state.currentUri()
                if (currentUri.isBlank()) throw IllegalStateException("No current URI")
                onPlayRequested(currentUri)
                onStateChanged(
                    state.updateTransport(
                    transportState = "TRANSITIONING",
                    transportStatus = "OK",
                    ),
                )
                log("[DLNA] Play: $currentUri")
                emptyMap()
            }
            "Pause" -> {
                onPauseRequested()
                onStateChanged(
                    state.updateTransport(
                    transportState = "TRANSITIONING",
                    transportStatus = "OK",
                    ),
                )
                log("[DLNA] Pause")
                emptyMap()
            }
            "Stop" -> {
                onStopRequested()
                onStateChanged(
                    state.updateTransport(
                    transportState = "TRANSITIONING",
                    transportStatus = "OK",
                    ),
                )
                log("[DLNA] Stop")
                emptyMap()
            }
            "Seek" -> {
                val unit = args["Unit"].orEmpty().ifBlank { "REL_TIME" }
                if (unit != "REL_TIME" && unit != "ABS_TIME") {
                    throw IllegalArgumentException("Unsupported seek unit: $unit")
                }
                val target = args["Target"].orEmpty().ifBlank { "00:00:00" }
                val targetMs = parseDlnaTimeToMs(target)
                    ?: throw IllegalArgumentException("Invalid seek target: $target")
                onSeekRequested(targetMs)
                val snapshot = state.snapshot()
                onStateChanged(
                    state.updateTransport(
                    transportState = snapshot.transportState,
                    transportStatus = snapshot.transportStatus,
                    relativeTimePosition = target,
                    ),
                )
                log("[DLNA] Seek: $target")
                emptyMap()
            }
            "GetTransportInfo" -> state.snapshot().let { snapshot ->
                mapOf(
                    "CurrentTransportState" to snapshot.transportState,
                    "CurrentTransportStatus" to snapshot.transportStatus,
                    "CurrentSpeed" to "1",
                )
            }
            "GetMediaInfo" -> state.snapshot().let { snapshot ->
                mapOf(
                    "NrTracks" to 1,
                    "MediaDuration" to snapshot.mediaDurationMs?.let(::formatDlnaTime).orEmpty().ifBlank { "00:00:00" },
                    "CurrentURI" to snapshot.currentUri,
                    "CurrentURIMetaData" to snapshot.currentUriMetadata,
                    "NextURI" to "",
                    "NextURIMetaData" to "",
                    "PlayMedium" to "NETWORK",
                    "RecordMedium" to "NOT_IMPLEMENTED",
                    "WriteStatus" to "NOT_IMPLEMENTED",
                )
            }
            "GetPositionInfo" -> state.snapshot().let { snapshot ->
                mapOf(
                    "Track" to 1,
                    "TrackDuration" to snapshot.mediaDurationMs?.let(::formatDlnaTime).orEmpty().ifBlank { "00:00:00" },
                    "TrackMetaData" to snapshot.currentUriMetadata,
                    "TrackURI" to snapshot.currentUri,
                    "RelTime" to snapshot.relativeTimePosition,
                    "AbsTime" to snapshot.relativeTimePosition,
                    "RelCount" to 0,
                    "AbsCount" to 0,
                )
            }
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

internal fun parseDlnaTimeToMs(value: String): Long? {
    val parts = value.split(":")
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toBigDecimalOrNull() ?: return null
    if (hours < 0L || minutes !in 0L..59L || seconds < BigDecimal.ZERO || seconds >= BigDecimal.valueOf(60L)) {
        return null
    }
    val totalSeconds = BigDecimal.valueOf(hours * 3_600L + minutes * 60L).add(seconds)
    return totalSeconds
        .multiply(BigDecimal.valueOf(1_000L))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
}

internal fun formatDlnaTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
