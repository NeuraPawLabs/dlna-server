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
                .flatMap { slot ->
                    buildList {
                        slot.videoAssetId?.let(::add)
                        addAll(slot.audioAssetIds)
                        addAll(slot.subtitleAssetIds)
                    }
                }
                .filter { assetId -> assetId !in readyAssetIds }
                .map { assetId -> assetId to assetKindPriority(assetsById.getValue(assetId).kind) }
                .chunkedBySlot(assetsById)
                .flatMap { chunk -> chunk.sortedBy { (_, priority) -> priority }.map { (assetId, _) -> assetId } }
        }
    }
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
        SessionAssetKind.VIDEO_SEGMENT -> 0
        SessionAssetKind.AUDIO_SEGMENT -> 1
        SessionAssetKind.SUBTITLE_SEGMENT -> 2
        else -> 3
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
