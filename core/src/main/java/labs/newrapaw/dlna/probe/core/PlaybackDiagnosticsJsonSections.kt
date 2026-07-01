package labs.newrapaw.dlna.probe.core

internal fun buildRecentSegmentSamplesJson(snapshot: PlaybackDiagnosticsSnapshot): String =
    buildJsonArray(snapshot.recentSegmentSamples) { sample ->
        buildString {
            append('{')
            appendJsonField("url", sample.url)
            append(',')
            appendJsonField("source", sample.source)
            append(',')
            appendJsonField("elapsedMs", sample.elapsedMs)
            append(',')
            appendJsonField("success", sample.success)
            append(',')
            appendJsonField("reason", sample.reason)
            append('}')
        }
    }

internal fun buildSlotStatesJson(snapshot: PlaybackDiagnosticsSnapshot): String =
    buildJsonArray(snapshot.slotStates) { item ->
        buildString {
            append('{')
            appendJsonField("slotIndex", item.slotIndex)
            append(',')
            appendJsonField("startMs", item.startMs)
            append(',')
            appendJsonField("endMs", item.endMs)
            append(',')
            appendJsonField("state", item.state.name)
            append(',')
            appendJsonField("videoReady", item.videoReady)
            append(',')
            appendJsonField("audioReady", item.audioReady)
            append(',')
            appendJsonField("subtitleReady", item.subtitleReady)
            append(',')
            appendJsonField(
                "blockedAssetKinds",
                buildJsonArray(item.blockedAssetKinds) { kind -> "\"${escapeJson(kind.name)}\"" },
                isRawJson = true,
            )
            append(',')
            appendJsonField(
                "degradedAssetKinds",
                buildJsonArray(item.degradedAssetKinds) { kind -> "\"${escapeJson(kind.name)}\"" },
                isRawJson = true,
            )
            append('}')
        }
    }

internal fun buildAssetDiagnosticsJson(snapshot: PlaybackDiagnosticsSnapshot): String =
    buildJsonArray(snapshot.assetDiagnostics) { item ->
        buildString {
            append('{')
            appendJsonField("assetId", item.assetId)
            append(',')
            appendJsonField("kind", item.kind.name)
            append(',')
            appendJsonField("trackId", item.trackId)
            append(',')
            appendJsonField("state", item.state.name)
            append(',')
            appendJsonField("localReady", item.localReady)
            append(',')
            appendJsonField("sizeBytes", item.sizeBytes)
            append(',')
            appendJsonField("lastElapsedMs", item.lastElapsedMs)
            append(',')
            appendJsonField("lastSource", item.lastSource)
            append(',')
            appendJsonField("retryCount", item.retryCount)
            append(',')
            appendJsonField("failureReason", item.failureReason)
            append('}')
        }
    }

internal fun buildInsightsJson(snapshot: PlaybackDiagnosticsSnapshot): String =
    buildJsonArray(snapshot.insights) { insight ->
        buildString {
            append('{')
            appendJsonField("code", insight.code)
            append(',')
            appendJsonField("message", insight.message)
            append(',')
            appendJsonField("detail", insight.detail)
            append('}')
        }
    }

internal fun buildPrimaryBottleneckJson(snapshot: PlaybackDiagnosticsSnapshot): String? =
    snapshot.primaryBottleneck?.let { insight ->
        buildString {
            append('{')
            appendJsonField("code", insight.code)
            append(',')
            appendJsonField("message", insight.message)
            append(',')
            appendJsonField("detail", insight.detail)
            append('}')
        }
    }

internal fun <T> buildJsonArray(items: List<T>, itemBuilder: (T) -> String): String =
    items.joinToString(prefix = "[", postfix = "]", separator = ",", transform = itemBuilder)

internal fun StringBuilder.appendJsonField(name: String, value: String?, isRawJson: Boolean = false) {
    append('"')
    append(escapeJson(name))
    append("\":")
    when {
        value == null -> append("null")
        isRawJson -> append(value)
        else -> {
            append('"')
            append(escapeJson(value))
            append('"')
        }
    }
}

internal fun StringBuilder.appendJsonField(name: String, value: Number?) {
    append('"')
    append(escapeJson(name))
    append("\":")
    append(value ?: "null")
}

internal fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    append('"')
    append(escapeJson(name))
    append("\":")
    append(value)
}

internal fun StringBuilder.appendJsonField(name: String, value: Boolean?) {
    append('"')
    append(escapeJson(name))
    append("\":")
    append(value ?: "null")
}

internal fun escapeJson(value: String): String =
    buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
