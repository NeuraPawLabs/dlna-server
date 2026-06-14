package labs.newrapaw.dlna.probe

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import java.net.URI

fun encodeProxyUrl(url: String): String =
    url.encodeUtf8().base64Url().trimEnd('=')

fun decodeProxyUrl(encoded: String): String =
    encoded.withBase64Padding().decodeBase64()?.utf8() ?: ""

fun isLikelyHlsManifest(url: String): Boolean =
    Regex("""\.m3u8(?:$|[/?#&=;%])""", RegexOption.IGNORE_CASE).containsMatchIn(url)

fun resolvePlayableUri(uri: String, proxyBaseUrl: String): String =
    if (isLikelyHlsManifest(uri)) "$proxyBaseUrl/proxy/hls.m3u8?u=${encodeProxyUrl(uri)}" else uri

fun rewriteHlsManifest(manifest: String, manifestUrl: String, proxyBaseUrl: String): String =
    manifest.lineSequence()
        .map { line -> rewriteManifestLine(line, manifestUrl, proxyBaseUrl) }
        .joinToString("\n")

fun extractHlsSegmentUrls(manifest: String, manifestUrl: String): List<String> =
    manifest.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { URI(manifestUrl).resolve(it).toString() }
        .toList()

fun stripPngWrapperFromSegment(segment: ByteArray): ByteArray {
    val offset = findMpegTsOffset(segment)
    return if (offset > 0) segment.copyOfRange(offset, segment.size) else segment
}

private fun rewriteManifestLine(line: String, manifestUrl: String, proxyBaseUrl: String): String {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return line

    val segmentUrl = URI(manifestUrl).resolve(trimmed).toString()
    return "$proxyBaseUrl/proxy/segment.ts?u=${encodeProxyUrl(segmentUrl)}"
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

private fun String.withBase64Padding(): String {
    val missingPadding = (4 - length % 4) % 4
    return if (missingPadding == 0) this else this + "=".repeat(missingPadding)
}
