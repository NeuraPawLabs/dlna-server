package labs.newrapaw.dlna.probe.core.session

class SessionDownloader {
    companion object {
        fun planStartupQueue(assets: List<SessionAsset>): List<SessionAsset> =
            assets.sortedWith(
                compareBy<SessionAsset> { startupPriority(it) }
                    .thenBy { it.sequence ?: Int.MAX_VALUE },
            )

        fun planPlaybackQueue(
            slots: List<TimelineSlot>,
            assetsById: Map<String, SessionAsset>,
            playHeadSlotIndex: Int,
            readyAssetIds: Set<String>,
        ): List<String> {
            return slots
                .filter { it.slotIndex >= playHeadSlotIndex }
                .flatMap(::playbackAssetIdsForSlot)
                .filter { assetId -> assetId !in readyAssetIds }
                .distinct()
                .map { assetId -> assetId to assetKindPriority(assetsById.getValue(assetId).kind) }
                .chunkedBySlot(assetsById)
                .flatMap { chunk -> chunk.sortedBy { (_, priority) -> priority }.map { (assetId, _) -> assetId } }
        }
    }
}

private fun playbackAssetIdsForSlot(slot: TimelineSlot): List<String> =
    buildList {
        addAll(slot.prerequisiteAssetIds)
        addAll(slot.videoPrerequisiteAssetIdsByTrack.values.flatten())
        slot.videoAssetId?.let(::add)
        addAll(slot.videoAssetIdsByTrack.values)
        addAll(slot.audioPrerequisiteAssetIds.values.flatten())
        addAll(slot.audioAssetIds)
        addAll(slot.subtitlePrerequisiteAssetIds.values.flatten())
        addAll(slot.subtitleAssetIds)
    }

private fun List<Pair<String, Int>>.chunkedBySlot(
    assetsById: Map<String, SessionAsset>,
): List<List<Pair<String, Int>>> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<Pair<String, Int>>>()
    forEach { entry ->
        val sequence = assetsById.getValue(entry.first).sequence ?: Int.MAX_VALUE
        val bucket = result.lastOrNull()
        val bucketSequence = bucket?.firstOrNull()?.let { assetsById.getValue(it.first).sequence ?: Int.MAX_VALUE }
        if (bucket == null || bucketSequence != sequence) {
            result += mutableListOf(entry)
        } else {
            bucket += entry
        }
    }
    return result
}

private fun assetKindPriority(kind: SessionAssetKind): Int =
    when (kind) {
        SessionAssetKind.INIT_SEGMENT -> 0
        SessionAssetKind.KEY -> 1
        SessionAssetKind.VIDEO_SEGMENT -> 2
        SessionAssetKind.AUDIO_SEGMENT -> 3
        SessionAssetKind.SUBTITLE_SEGMENT -> 4
        else -> 5
    }

private fun startupPriority(asset: SessionAsset): Int =
    when {
        asset.kind == SessionAssetKind.INIT_SEGMENT -> 0
        asset.kind == SessionAssetKind.KEY -> 1
        asset.kind == SessionAssetKind.VIDEO_SEGMENT && asset.requiredForStartup -> 2
        asset.kind == SessionAssetKind.AUDIO_SEGMENT -> 3
        asset.kind == SessionAssetKind.SUBTITLE_SEGMENT -> 4
        else -> 5
    }
