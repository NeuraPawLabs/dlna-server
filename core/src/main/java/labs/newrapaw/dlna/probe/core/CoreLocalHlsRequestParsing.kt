package labs.newrapaw.dlna.probe.core

internal data class ParsedRequestLine(
    val method: String,
    val path: String,
)

internal class MalformedRequestException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun parseRequestLine(line: String): ParsedRequestLine {
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.size != 3) {
        throw MalformedRequestException("malformed request line")
    }
    val method = parts[0]
    val path = parts[1]
    val version = parts[2]
    if (method.isBlank() || path.isBlank() || !version.startsWith("HTTP/")) {
        throw MalformedRequestException("malformed request line")
    }
    return ParsedRequestLine(method = method, path = path)
}

internal fun parseContentLength(headerValue: String?): Int {
    if (headerValue == null) return 0
    val value = headerValue.trim()
    val parsed = value.toIntOrNull() ?: throw MalformedRequestException("invalid Content-Length")
    if (parsed < 0) {
        throw MalformedRequestException("invalid Content-Length")
    }
    return parsed
}

internal data class RequestedByteRange(
    val start: Long?,
    val endInclusive: Long?,
) {
    fun resolve(totalLength: Long): ResolvedByteRange =
        when {
            totalLength <= 0L -> ResolvedByteRange(0L, -1L, false)
            start == null && endInclusive == null -> ResolvedByteRange(0L, totalLength - 1L, true)
            start != null -> {
                val resolvedStart = start
                val resolvedEnd = minOf(endInclusive ?: (totalLength - 1L), totalLength - 1L)
                if (resolvedStart >= totalLength || resolvedStart > resolvedEnd) {
                    ResolvedByteRange(0L, totalLength - 1L, false)
                } else {
                    ResolvedByteRange(resolvedStart, resolvedEnd, true)
                }
            }
            else -> {
                val suffixLength = endInclusive ?: return ResolvedByteRange(0L, totalLength - 1L, false)
                if (suffixLength <= 0L) {
                    ResolvedByteRange(0L, totalLength - 1L, false)
                } else {
                    val resolvedStart = (totalLength - suffixLength).coerceAtLeast(0L)
                    ResolvedByteRange(resolvedStart, totalLength - 1L, true)
                }
            }
        }
}

internal data class ResolvedByteRange(
    val start: Long,
    val endInclusive: Long,
    val satisfiable: Boolean,
)

internal sealed class ParsedByteRangeHeader {
    data object Absent : ParsedByteRangeHeader()
    data object Unsupported : ParsedByteRangeHeader()
    data class Single(val range: RequestedByteRange) : ParsedByteRangeHeader()
}

internal fun parseByteRange(headerValue: String?): ParsedByteRangeHeader {
    val value = headerValue?.trim().orEmpty()
    if (value.isEmpty()) return ParsedByteRangeHeader.Absent
    if (!value.startsWith("bytes=", ignoreCase = true)) return ParsedByteRangeHeader.Unsupported
    val spec = value.substringAfter("=").trim()
    if (spec.contains(",")) return ParsedByteRangeHeader.Unsupported
    val match = Regex("""^(\d*)-(\d*)$""").matchEntire(spec) ?: return ParsedByteRangeHeader.Unsupported
    val startText = match.groupValues[1]
    val endText = match.groupValues[2]
    val start = startText.toLongOrNull()
    val end = endText.toLongOrNull()
    if (start == null && end == null) return ParsedByteRangeHeader.Unsupported
    return ParsedByteRangeHeader.Single(
        range = RequestedByteRange(start = start, endInclusive = end),
    )
}
