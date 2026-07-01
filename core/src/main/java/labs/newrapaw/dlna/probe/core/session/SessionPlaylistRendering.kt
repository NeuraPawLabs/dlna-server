package labs.newrapaw.dlna.probe.core.session

import java.net.URI

internal fun StringBuilder.appendMapTag(
    sessionId: String,
    assetsById: Map<String, SessionAsset>,
    assetId: String,
) {
    val prerequisiteAsset = assetsById[assetId]
    append(
        """#EXT-X-MAP:URI="${prerequisiteAsset?.let { sessionAssetPath(sessionId, it) } ?: "/session/$sessionId/asset/$assetId.ts"}"""" + "\n",
    )
}

internal fun StringBuilder.appendKeyTag(
    sessionId: String,
    assetsById: Map<String, SessionAsset>,
    assetId: String?,
) {
    if (assetId == null) {
        append("#EXT-X-KEY:METHOD=NONE\n")
        return
    }
    val prerequisiteAsset = assetsById[assetId]
    append(
        buildString {
            append("#EXT-X-KEY:METHOD=${prerequisiteAsset?.keyMethod ?: "NONE"},URI=\"${prerequisiteAsset?.let { sessionAssetPath(sessionId, it) } ?: "/session/$sessionId/asset/$assetId.key"}\"")
            prerequisiteAsset?.keyIv?.let { append(",IV=$it") }
        } + "\n",
    )
}

internal fun prerequisiteIdsForSlot(
    slot: TimelineSlot,
    kind: SessionAssetKind,
    trackId: String,
): List<String> =
    when (kind) {
        SessionAssetKind.VIDEO_SEGMENT -> slot.videoPrerequisiteAssetIdsByTrack[trackId]
            ?: slot.prerequisiteAssetIds
        SessionAssetKind.AUDIO_SEGMENT -> slot.audioPrerequisiteAssetIds[trackId].orEmpty()
        SessionAssetKind.SUBTITLE_SEGMENT -> slot.subtitlePrerequisiteAssetIds[trackId].orEmpty()
        else -> emptyList()
    }

internal fun sessionAssetPath(sessionId: String, asset: SessionAsset): String =
    "/session/$sessionId/asset/${asset.assetId}${assetPathSuffix(asset)}"

internal fun targetDurationSeconds(slots: List<TimelineSlot>): Long =
    slots.maxOfOrNull { slot ->
        val durationMs = (slot.endMs - slot.startMs).coerceAtLeast(0L)
        ((durationMs + 999L) / 1_000L).coerceAtLeast(1L)
    } ?: 1L

internal fun escape(value: String): String =
    value.replace("\"", "")

internal fun assetPathSuffix(asset: SessionAsset): String =
    when (asset.kind) {
        SessionAssetKind.VIDEO_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".ts")
        SessionAssetKind.AUDIO_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".aac")
        SessionAssetKind.SUBTITLE_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".vtt")
        SessionAssetKind.INIT_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".ts")
        SessionAssetKind.KEY -> ".key"
        SessionAssetKind.MANIFEST -> ".m3u8"
    }

internal fun inferSegmentExtension(url: String, defaultExtension: String): String {
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
    val fileName = path.substringAfterLast('/')
    val suffix = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
    return suffix ?: defaultExtension
}
