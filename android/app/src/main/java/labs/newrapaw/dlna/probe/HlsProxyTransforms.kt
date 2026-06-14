package labs.newrapaw.dlna.probe

import android.util.Base64
import java.net.URI

fun encodeProxyUrl(url: String): String =
    Base64.encodeToString(
        url.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

fun decodeProxyUrl(encoded: String): String =
    String(
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        Charsets.UTF_8,
    )

fun isLikelyHlsManifest(url: String): Boolean =
    Regex("""\.m3u8(?:$|[/?#&=;%])""", RegexOption.IGNORE_CASE).containsMatchIn(url)

fun resolvePlayableUri(uri: String, proxyBaseUrl: String): String =
    if (isLikelyHlsManifest(uri)) "$proxyBaseUrl/proxy/hls.m3u8?u=${encodeProxyUrl(uri)}" else uri

fun rewriteHlsManifest(manifest: String, manifestUrl: String, proxyBaseUrl: String): String =
    manifest.lineSequence()
        .map { line -> rewriteManifestLine(line, manifestUrl, proxyBaseUrl) }
        .joinToString("\n")

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
