package labs.newrapaw.dlna.probe

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

fun parseSingleVariantMasterManifest(
    manifest: String,
    manifestUrl: String,
): SingleVariantMasterPlaylist? {
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
            .forEach { line ->
                when {
                    line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                        pendingAttributes = parseAttributeList(line.substringAfter(":"))
                    }
                    line.startsWith("#") -> Unit
                    pendingAttributes != null -> {
                        add(pendingAttributes.orEmpty() + ("URI" to URI(manifestUrl).resolve(line).toString()))
                        pendingAttributes = null
                    }
                }
            }
    }
    if (variants.size != 1) return null

    val variant = variants.single()
    val audioGroupId = variant["AUDIO"]
    val subtitleGroupId = variant["SUBTITLES"]
    return SingleVariantMasterPlaylist(
        variantUrl = variant.getValue("URI"),
        audioTracks = mediaTracks.filter { it.type == "AUDIO" && (audioGroupId == null || it.groupId == audioGroupId) },
        subtitleTracks = mediaTracks.filter { it.type == "SUBTITLES" && (subtitleGroupId == null || it.groupId == subtitleGroupId) },
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

private fun String.withBase64Padding(): String {
    val missingPadding = (4 - length % 4) % 4
    return if (missingPadding == 0) this else this + "=".repeat(missingPadding)
}
