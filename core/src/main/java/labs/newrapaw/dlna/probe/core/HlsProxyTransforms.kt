package labs.newrapaw.dlna.probe.core

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import java.net.URI

data class HlsMediaTrack(
    val type: String,
    val groupId: String?,
    val name: String?,
    val language: String?,
    val uri: String,
    val isDefault: Boolean,
)

data class HlsVariantStream(
    val trackId: String,
    val uri: String,
    val bandwidth: Long?,
    val averageBandwidth: Long?,
    val resolution: String?,
    val codecs: String?,
    val audioGroupId: String?,
    val subtitleGroupId: String?,
)

data class HlsMasterPlaylist(
    val videoVariants: List<HlsVariantStream>,
    val audioTracks: List<HlsMediaTrack>,
    val subtitleTracks: List<HlsMediaTrack>,
)

data class SingleVariantMasterPlaylist(
    val variantUrl: String,
    val audioTracks: List<HlsMediaTrack>,
    val subtitleTracks: List<HlsMediaTrack>,
)

fun encodeProxyUrl(url: String): String =
    url.encodeUtf8().base64Url().trimEnd('=')

fun decodeProxyUrl(encoded: String): String =
    encoded.withBase64Padding().decodeBase64()?.utf8() ?: ""

fun isVodManifest(manifest: String): Boolean =
    manifest.lineSequence().any { it.trim() == "#EXT-X-ENDLIST" }

fun looksLikeMasterPlaylist(manifest: String): Boolean =
    manifest.lineSequence().any { it.trim().startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }

fun parseMasterManifest(
    manifest: String,
    manifestUrl: String,
): HlsMasterPlaylist? {
    val mediaTracks = manifest.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("#EXT-X-MEDIA:", ignoreCase = true) }
        .mapNotNull { parseMediaTrack(it, manifestUrl) }
        .toList()

    val variants = buildList {
        var pendingAttributes: Map<String, String>? = null
        manifest.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEachIndexed { index, line ->
                when {
                    line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                        pendingAttributes = parseAttributeList(line.substringAfter(":"))
                    }
                    line.startsWith("#") -> Unit
                    pendingAttributes != null -> {
                        val attributes = pendingAttributes.orEmpty()
                        val resolvedUri = URI(manifestUrl).resolve(line).toString()
                        add(
                            HlsVariantStream(
                                trackId = buildVideoTrackId(
                                    uri = resolvedUri,
                                    resolution = attributes["RESOLUTION"],
                                    bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
                                    index = index,
                                ),
                                uri = resolvedUri,
                                bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
                                averageBandwidth = attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                                resolution = attributes["RESOLUTION"],
                                codecs = attributes["CODECS"],
                                audioGroupId = attributes["AUDIO"],
                                subtitleGroupId = attributes["SUBTITLES"],
                            ),
                        )
                        pendingAttributes = null
                    }
                }
            }
    }
    if (variants.isEmpty()) return null

    return HlsMasterPlaylist(
        videoVariants = variants,
        audioTracks = mediaTracks.filter { it.type == "AUDIO" },
        subtitleTracks = mediaTracks.filter { it.type == "SUBTITLES" },
    )
}

fun parseSingleVariantMasterManifest(
    manifest: String,
    manifestUrl: String,
): SingleVariantMasterPlaylist? {
    val masterPlaylist = parseMasterManifest(manifest, manifestUrl) ?: return null
    val variant = masterPlaylist.videoVariants.maxWithOrNull(
        compareBy<HlsVariantStream>(
            { it.sessionSelectionBandwidth() },
            { it.uri },
        ),
    ) ?: return null
    return SingleVariantMasterPlaylist(
        variantUrl = variant.uri,
        audioTracks = masterPlaylist.audioTracks.filter { variant.audioGroupId == null || it.groupId == variant.audioGroupId },
        subtitleTracks = masterPlaylist.subtitleTracks.filter { variant.subtitleGroupId == null || it.groupId == variant.subtitleGroupId },
    )
}

fun stripPngWrapperFromSegment(segment: ByteArray): ByteArray {
    val offset = findMpegTsOffset(segment)
    return if (offset > 0) segment.copyOfRange(offset, segment.size) else segment
}

private fun findMpegTsOffset(segment: ByteArray): Int {
    var offset = 0
    while (offset < segment.size - 376) {
        if (
            segment[offset] == 0x47.toByte() &&
            segment[offset + 188] == 0x47.toByte() &&
            segment[offset + 376] == 0x47.toByte()
        ) {
            return offset
        }
        offset += 1
    }
    return 0
}

private fun parseMediaTrack(line: String, manifestUrl: String): HlsMediaTrack? {
    val attributes = parseAttributeList(line.substringAfter(":"))
    val type = attributes["TYPE"] ?: return null
    val uri = attributes["URI"]?.let { URI(manifestUrl).resolve(it).toString() } ?: return null
    return HlsMediaTrack(
        type = type,
        groupId = attributes["GROUP-ID"],
        name = attributes["NAME"],
        language = attributes["LANGUAGE"],
        uri = uri,
        isDefault = attributes["DEFAULT"].equals("YES", ignoreCase = true),
    )
}

private fun parseAttributeList(raw: String): Map<String, String> =
    Regex("""([A-Z0-9-]+)=("([^"]*)"|[^,]*)""")
        .findAll(raw)
        .associate { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[2].trim('"') }
            key to value
        }

private fun HlsVariantStream.sessionSelectionBandwidth(): Long =
    averageBandwidth
        ?: bandwidth
        ?: -1L

private fun buildVideoTrackId(
    uri: String,
    resolution: String?,
    bandwidth: Long?,
    index: Int,
): String {
    val label = resolution
        ?: bandwidth?.toString()
        ?: URI(uri).path.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = "")
    val normalizedLabel = label
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "variant" }
    return "video-$normalizedLabel-$index"
}

private fun String.withBase64Padding(): String {
    val missingPadding = (4 - length % 4) % 4
    return if (missingPadding == 0) this else this + "=".repeat(missingPadding)
}
