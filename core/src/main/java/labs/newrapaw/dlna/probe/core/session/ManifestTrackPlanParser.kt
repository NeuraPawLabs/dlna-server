package labs.newrapaw.dlna.probe.core.session

import java.net.URI

internal data class MediaEntry(
    val url: String,
    val durationMs: Long?,
    val discontinuityBefore: Boolean,
    val prerequisiteAssetIds: List<String> = emptyList(),
)

internal data class TrackPlan(
    val entries: List<MediaEntry>,
    val prerequisiteAssets: List<SessionAsset>,
)

internal fun parseMediaEntries(manifestBody: String, manifestUrl: String): List<MediaEntry> {
    var pendingDurationMs: Long? = null
    var pendingDiscontinuity = false
    return buildList {
        manifestBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingDurationMs = (line.substringAfter(":").substringBefore(",").trim().toDoubleOrNull()?.times(1000))?.toLong()
                }
                line.equals("#EXT-X-DISCONTINUITY", ignoreCase = true) -> {
                    pendingDiscontinuity = true
                }
                line.startsWith("#") -> Unit
                else -> {
                    add(
                        MediaEntry(
                            url = URI(manifestUrl).resolve(line).toString(),
                            durationMs = pendingDurationMs,
                            discontinuityBefore = pendingDiscontinuity,
                        ),
                    )
                    pendingDurationMs = null
                    pendingDiscontinuity = false
                }
            }
        }
    }
}

internal fun parseTrackPlan(
    trackId: String,
    manifestBody: String,
    manifestUrl: String,
    blocking: Boolean,
    requiredForStartup: Boolean,
): TrackPlan {
    val prerequisites = mutableListOf<SessionAsset>()
    var pendingDurationMs: Long? = null
    var pendingDiscontinuity = false
    var currentMapAssetId: String? = null
    var currentMapUrl: String? = null
    var currentKeyAssetId: String? = null
    var currentKeyFingerprint: String? = null
    var mapSequence = 0
    var keySequence = 0
    var segmentCount = 0

    val entries = buildList {
        manifestBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val initUrl = parseMapUri(line, manifestUrl) ?: return@forEach
                    if (initUrl != currentMapUrl) {
                        val assetId = "init-$trackId-${mapSequence++}"
                        prerequisites += SessionAsset(
                            assetId = assetId,
                            kind = SessionAssetKind.INIT_SEGMENT,
                            trackId = trackId,
                            url = initUrl,
                            durationMs = null,
                            sequence = segmentCount,
                            blocking = blocking,
                            requiredForStartup = requiredForStartup && segmentCount == 0,
                            localPath = null,
                        )
                        currentMapUrl = initUrl
                        currentMapAssetId = assetId
                    }
                }
                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val key = parseKey(line, manifestUrl)
                    if (key == null) {
                        currentKeyAssetId = null
                        currentKeyFingerprint = null
                    } else {
                        val fingerprint = "${key.method}|${key.url}|${key.iv.orEmpty()}"
                        if (fingerprint != currentKeyFingerprint) {
                            val assetId = "key-$trackId-${keySequence++}"
                            prerequisites += SessionAsset(
                                assetId = assetId,
                                kind = SessionAssetKind.KEY,
                                trackId = trackId,
                                url = key.url,
                                durationMs = null,
                                sequence = segmentCount,
                                blocking = blocking,
                                requiredForStartup = requiredForStartup && segmentCount == 0,
                                localPath = null,
                                keyMethod = key.method,
                                keyIv = key.iv,
                            )
                            currentKeyAssetId = assetId
                            currentKeyFingerprint = fingerprint
                        }
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingDurationMs = (line.substringAfter(":").substringBefore(",").trim().toDoubleOrNull()?.times(1000))?.toLong()
                }
                line.equals("#EXT-X-DISCONTINUITY", ignoreCase = true) -> {
                    pendingDiscontinuity = true
                }
                line.startsWith("#") -> Unit
                else -> {
                    add(
                        MediaEntry(
                            url = URI(manifestUrl).resolve(line).toString(),
                            durationMs = pendingDurationMs,
                            discontinuityBefore = pendingDiscontinuity,
                            prerequisiteAssetIds = listOfNotNull(currentMapAssetId, currentKeyAssetId),
                        ),
                    )
                    pendingDurationMs = null
                    pendingDiscontinuity = false
                    segmentCount += 1
                }
            }
        }
    }
    return TrackPlan(
        entries = entries,
        prerequisiteAssets = prerequisites,
    )
}

internal fun parseMapUri(line: String, manifestUrl: String): String? =
    Regex("""#EXT-X-MAP:URI="([^"]+)"""").find(line)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() }

internal data class ParsedKey(
    val method: String,
    val url: String,
    val iv: String?,
)

internal fun parseKey(line: String, manifestUrl: String): ParsedKey? {
    val method = Regex("""METHOD=([^,]+)""").find(line)?.groupValues?.get(1) ?: "NONE"
    if (method.equals("NONE", ignoreCase = true)) return null
    val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() } ?: return null
    val iv = Regex("""IV=([^,\s]+)""").find(line)?.groupValues?.get(1)
    return ParsedKey(method = method, url = uri, iv = iv)
}
