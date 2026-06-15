package labs.newrapaw.dlna.probe

fun formatUpstreamFailure(statusCode: Int, statusMessage: String, body: String): String {
    val normalizedBody = body
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { if (it.length > 200) it.take(197) + "..." else it }

    val status = listOf(statusCode.toString(), statusMessage)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    return if (normalizedBody.isEmpty()) status else "$status: $normalizedBody"
}
