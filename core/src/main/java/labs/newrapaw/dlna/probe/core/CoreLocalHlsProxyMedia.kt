package labs.newrapaw.dlna.probe.core

import java.util.ArrayDeque
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionDownloader
import labs.newrapaw.dlna.probe.core.session.TimelineSlot

internal fun guessSegmentContentType(upstreamUrl: String): String {
    val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
    return when {
        path.endsWith(".m4s", ignoreCase = true) ||
            path.endsWith(".mp4", ignoreCase = true) ||
            path.endsWith(".cmfv", ignoreCase = true) -> "video/mp4"
        path.endsWith(".aac", ignoreCase = true) -> "audio/aac"
        path.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        path.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
        else -> "video/mp2t"
    }
}

internal fun looksLikeTransportStream(upstreamUrl: String): Boolean {
    val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
    return path.endsWith(".ts", ignoreCase = true) || path.endsWith(".png", ignoreCase = true)
}

internal fun isWrappedTransportStream(upstreamUrl: String): Boolean {
    val path = runCatching { java.net.URI(upstreamUrl).path.orEmpty() }.getOrDefault(upstreamUrl)
    return path.endsWith(".png", ignoreCase = true)
}

internal fun buildSessionTrackId(
    prefix: String,
    name: String?,
    language: String?,
    index: Int,
): String {
    val normalizedName = (name ?: "$prefix-$index")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "$prefix-$index" }
    val normalizedLanguage = language
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]+"), "-")
        ?.trim('-')
        ?.takeIf { it.isNotBlank() }
    return listOf(prefix, normalizedName, normalizedLanguage)
        .filterNotNull()
        .joinToString("-")
}

internal fun findSlotIndexForAsset(slots: List<TimelineSlot>, assetId: String): Int? =
    slots.firstOrNull { slot ->
        slot.videoAssetId == assetId ||
            assetId in slot.audioAssetIds ||
            assetId in slot.subtitleAssetIds ||
            assetId in slot.prerequisiteAssetIds ||
            slot.audioPrerequisiteAssetIds.values.any { assetId in it } ||
            slot.subtitlePrerequisiteAssetIds.values.any { assetId in it }
    }?.slotIndex

internal fun buildSessionPrefetchQueue(assets: List<SessionAsset>): ArrayDeque<String> {
    val startup = SessionDownloader.planStartupQueue(assets)
    val remaining = assets
        .filterNot { asset -> startup.any { it.assetId == asset.assetId } }
        .sortedWith(compareBy<SessionAsset> { it.sequence ?: Int.MAX_VALUE }.thenBy { it.assetId })
    return ArrayDeque((startup + remaining).map { it.assetId })
}
