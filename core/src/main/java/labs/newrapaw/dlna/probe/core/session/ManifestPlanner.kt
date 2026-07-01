package labs.newrapaw.dlna.probe.core.session

data class PlannedTrackManifest(
    val trackId: String,
    val kind: SessionAssetKind,
    val manifestUrl: String,
    val manifestBody: String,
    val hasEndList: Boolean = true,
    val groupId: String? = null,
    val displayName: String? = null,
    val language: String? = null,
    val isDefault: Boolean = false,
    val bandwidth: Long? = null,
    val averageBandwidth: Long? = null,
    val resolution: String? = null,
    val codecs: String? = null,
)

data class PlannedSessionTimeline(
    val slots: List<TimelineSlot>,
    val assets: List<SessionAsset>,
)

class ManifestPlanner {
    fun plan(
        videoTracks: List<PlannedTrackManifest>,
        primaryVideoTrackId: String,
        audioTracks: List<PlannedTrackManifest>,
        subtitleTracks: List<PlannedTrackManifest>,
    ): PlannedSessionTimeline {
        val primaryVideoTrack = videoTracks.firstOrNull { it.trackId == primaryVideoTrackId }
            ?: error("Primary video track not found: $primaryVideoTrackId")
        val assets = mutableListOf<SessionAsset>()
        val videoPlans = videoTracks.associate { track ->
            track.trackId to parseTrackPlan(
                trackId = track.trackId,
                manifestBody = track.manifestBody,
                manifestUrl = track.manifestUrl,
                blocking = true,
                requiredForStartup = true,
            )
        }
        val primaryVideoPlan = videoPlans.getValue(primaryVideoTrackId)
        val videoEntries = primaryVideoPlan.entries
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

        videoTracks.forEach { track ->
            val prerequisites = videoPlans.getValue(track.trackId).prerequisiteAssets
            prerequisites.forEach { assets += it }
        }
        audioEntries.forEach { (trackId, trackPlan) ->
            trackPlan.prerequisiteAssets.forEach { assets += it }
        }
        subtitleEntries.forEach { (trackId, trackPlan) ->
            trackPlan.prerequisiteAssets.forEach { assets += it }
        }

        val slots = videoEntries.mapIndexed { index, entry ->
            val videoId = "video-$index"
            val videoAssetIdsByTrack = linkedMapOf<String, String>()
            val videoPrerequisiteAssetIdsByTrack = linkedMapOf<String, List<String>>()
            val audioPrerequisiteAssetIds = linkedMapOf<String, List<String>>()
            val subtitlePrerequisiteAssetIds = linkedMapOf<String, List<String>>()
            assets += SessionAsset(
                assetId = videoId,
                kind = SessionAssetKind.VIDEO_SEGMENT,
                trackId = primaryVideoTrack.trackId,
                url = entry.url,
                durationMs = entry.durationMs,
                sequence = index,
                blocking = true,
                requiredForStartup = index < 4,
                localPath = null,
            )
            videoAssetIdsByTrack[primaryVideoTrackId] = videoId
            videoPrerequisiteAssetIdsByTrack[primaryVideoTrackId] = entry.prerequisiteAssetIds
            videoTracks
                .filterNot { it.trackId == primaryVideoTrackId }
                .forEach { track ->
                    val variantEntry = videoPlans.getValue(track.trackId).entries.getOrNull(index) ?: return@forEach
                    val variantAssetId = "video-${track.trackId}-$index"
                    assets += SessionAsset(
                        assetId = variantAssetId,
                        kind = SessionAssetKind.VIDEO_SEGMENT,
                        trackId = track.trackId,
                        url = variantEntry.url,
                        durationMs = variantEntry.durationMs,
                        sequence = index,
                        blocking = true,
                        requiredForStartup = index < 4,
                        localPath = null,
                    )
                    videoAssetIdsByTrack[track.trackId] = variantAssetId
                    videoPrerequisiteAssetIdsByTrack[track.trackId] = variantEntry.prerequisiteAssetIds
                }

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
                    audioPrerequisiteAssetIds[trackId] = audioEntry.prerequisiteAssetIds
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
                    subtitlePrerequisiteAssetIds[trackId] = subtitleEntry.prerequisiteAssetIds
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
                videoDiscontinuityBefore = entry.discontinuityBefore,
                videoAssetIdsByTrack = videoAssetIdsByTrack,
                videoPrerequisiteAssetIdsByTrack = videoPrerequisiteAssetIdsByTrack,
                audioAssetIds = audioIds,
                subtitleAssetIds = subtitleIds,
                prerequisiteAssetIds = entry.prerequisiteAssetIds,
                audioPrerequisiteAssetIds = audioPrerequisiteAssetIds,
                subtitlePrerequisiteAssetIds = subtitlePrerequisiteAssetIds,
            )
        }

        return PlannedSessionTimeline(slots = slots, assets = assets)
    }
}
