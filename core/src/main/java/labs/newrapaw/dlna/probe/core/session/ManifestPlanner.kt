package labs.newrapaw.dlna.probe.core.session

import java.net.URI

data class PlannedTrackManifest(
    val trackId: String,
    val kind: SessionAssetKind,
    val manifestUrl: String,
    val manifestBody: String,
)

data class PlannedSessionTimeline(
    val slots: List<TimelineSlot>,
    val assets: List<SessionAsset>,
)

class ManifestPlanner {
    fun plan(
        manifestUrl: String,
        videoManifest: String,
        audioTracks: List<PlannedTrackManifest>,
        subtitleTracks: List<PlannedTrackManifest>,
    ): PlannedSessionTimeline {
        val assets = mutableListOf<SessionAsset>()
        val videoEntries = parseMediaEntries(videoManifest, manifestUrl)
        val audioEntries = audioTracks.associate { track ->
            track.trackId to parseTrackPlan(
                trackId = track.trackId,
                manifestBody = track.manifestBody,
                manifestUrl = track.manifestUrl,
                blocking = true,
                requiredForStartup = false,
            )
        }
        val subtitleEntries = subtitleTracks.associate { track ->
            track.trackId to parseTrackPlan(
                trackId = track.trackId,
                manifestBody = track.manifestBody,
                manifestUrl = track.manifestUrl,
                blocking = false,
                requiredForStartup = false,
            )
        }

        parseTrackPlan(
            trackId = "video-main",
            manifestBody = videoManifest,
            manifestUrl = manifestUrl,
            blocking = true,
            requiredForStartup = true,
        ).prerequisiteAssets.forEach { assets += it }
        val videoPrerequisiteIds = assets.map { it.assetId }

        val audioPrerequisiteIds = mutableMapOf<String, List<String>>()
        audioEntries.forEach { (trackId, trackPlan) ->
            trackPlan.prerequisiteAssets.forEach { assets += it }
            audioPrerequisiteIds[trackId] = trackPlan.prerequisiteAssets.map { it.assetId }
        }
        val subtitlePrerequisiteIds = mutableMapOf<String, List<String>>()
        subtitleEntries.forEach { (trackId, trackPlan) ->
            trackPlan.prerequisiteAssets.forEach { assets += it }
            subtitlePrerequisiteIds[trackId] = trackPlan.prerequisiteAssets.map { it.assetId }
        }

        val slots = videoEntries.mapIndexed { index, entry ->
            val videoId = "video-$index"
            assets += SessionAsset(
                assetId = videoId,
                kind = SessionAssetKind.VIDEO_SEGMENT,
                trackId = "video-main",
                url = entry.url,
                durationMs = entry.durationMs,
                sequence = index,
                blocking = true,
                requiredForStartup = index < 4,
                localPath = null,
            )

            val audioIds = audioEntries.flatMap { (trackId, trackPlan) ->
                trackPlan.entries.getOrNull(index)?.let { audioEntry ->
                    val audioId = "audio-$trackId-$index"
                    assets += SessionAsset(
                        assetId = audioId,
                        kind = SessionAssetKind.AUDIO_SEGMENT,
                        trackId = trackId,
                        url = audioEntry.url,
                        durationMs = audioEntry.durationMs,
                        sequence = index,
                        blocking = true,
                        requiredForStartup = false,
                        localPath = null,
                    )
                    listOf(audioId)
                } ?: emptyList()
            }

            val subtitleIds = subtitleEntries.flatMap { (trackId, trackPlan) ->
                trackPlan.entries.getOrNull(index)?.let { subtitleEntry ->
                    val subtitleId = "subtitle-$trackId-$index"
                    assets += SessionAsset(
                        assetId = subtitleId,
                        kind = SessionAssetKind.SUBTITLE_SEGMENT,
                        trackId = trackId,
                        url = subtitleEntry.url,
                        durationMs = subtitleEntry.durationMs,
                        sequence = index,
                        blocking = false,
                        requiredForStartup = false,
                        localPath = null,
                    )
                    listOf(subtitleId)
                } ?: emptyList()
            }

            val startMs = videoEntries.take(index).sumOf { it.durationMs ?: 0L }
            val endMs = startMs + (entry.durationMs ?: 0L)
            TimelineSlot(
                slotIndex = index,
                startMs = startMs,
                endMs = endMs,
                videoAssetId = videoId,
                audioAssetIds = audioIds,
                subtitleAssetIds = subtitleIds,
                prerequisiteAssetIds = videoPrerequisiteIds,
                audioPrerequisiteAssetIds = audioPrerequisiteIds,
                subtitlePrerequisiteAssetIds = subtitlePrerequisiteIds,
            )
        }

        return PlannedSessionTimeline(slots = slots, assets = assets)
    }
}

private data class MediaEntry(
    val url: String,
    val durationMs: Long?,
)

private data class TrackPlan(
    val entries: List<MediaEntry>,
    val prerequisiteAssets: List<SessionAsset>,
)

private fun parseMediaEntries(manifestBody: String, manifestUrl: String): List<MediaEntry> {
    var pendingDurationMs: Long? = null
    return buildList {
        manifestBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingDurationMs = (line.substringAfter(":").substringBefore(",").trim().toDoubleOrNull()?.times(1000))?.toLong()
                }
                line.startsWith("#") -> Unit
                else -> {
                    add(MediaEntry(url = URI(manifestUrl).resolve(line).toString(), durationMs = pendingDurationMs))
                    pendingDurationMs = null
                }
            }
        }
    }
}

private fun parseTrackPlan(
    trackId: String,
    manifestBody: String,
    manifestUrl: String,
    blocking: Boolean,
    requiredForStartup: Boolean,
): TrackPlan {
    val prerequisites = mutableListOf<SessionAsset>()
    parseMapUri(manifestBody, manifestUrl)?.let { initUrl ->
        prerequisites += SessionAsset(
            assetId = "init-$trackId-0",
            kind = SessionAssetKind.INIT_SEGMENT,
            trackId = trackId,
            url = initUrl,
            durationMs = null,
            sequence = 0,
            blocking = blocking,
            requiredForStartup = requiredForStartup,
            localPath = null,
        )
    }
    parseKey(manifestBody, manifestUrl)?.let { key ->
        prerequisites += SessionAsset(
            assetId = "key-$trackId-0",
            kind = SessionAssetKind.KEY,
            trackId = trackId,
            url = key.url,
            durationMs = null,
            sequence = 0,
            blocking = blocking,
            requiredForStartup = requiredForStartup,
            localPath = null,
            keyMethod = key.method,
            keyIv = key.iv,
        )
    }
    return TrackPlan(
        entries = parseMediaEntries(manifestBody, manifestUrl),
        prerequisiteAssets = prerequisites,
    )
}

private fun parseMapUri(manifestBody: String, manifestUrl: String): String? =
    Regex("""#EXT-X-MAP:URI="([^"]+)"""").find(manifestBody)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() }

private data class ParsedKey(
    val method: String,
    val url: String,
    val iv: String?,
)

private fun parseKey(manifestBody: String, manifestUrl: String): ParsedKey? {
    val line = manifestBody.lineSequence().firstOrNull { it.trim().startsWith("#EXT-X-KEY:", ignoreCase = true) } ?: return null
    val method = Regex("""METHOD=([^,]+)""").find(line)?.groupValues?.get(1) ?: "NONE"
    val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() } ?: return null
    val iv = Regex("""IV=([^,\s]+)""").find(line)?.groupValues?.get(1)
    return ParsedKey(method = method, url = uri, iv = iv)
}
