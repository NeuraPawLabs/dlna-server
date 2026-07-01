package labs.newrapaw.dlna.probe.core

fun buildPlaybackDiagnosticsJson(snapshot: PlaybackDiagnosticsSnapshot): String =
    buildString {
        append('{')
        appendJsonField("playbackStatus", snapshot.playbackStatus.name)
        append(',')
        appendJsonField("sessionStatus", snapshot.sessionStatus)
        append(',')
        appendJsonField("sessionStartedAtMs", snapshot.sessionStartedAtMs)
        append(',')
        appendJsonField("sourceUrl", snapshot.sourceUrl)
        append(',')
        appendJsonField("localProxyUrl", snapshot.localProxyUrl)
        append(',')
        appendJsonField("lastUpdatedAtMs", snapshot.lastUpdatedAtMs)
        append(',')
        appendJsonField("upstreamMode", snapshot.upstreamMode.name)
        append(',')
        appendJsonField("activeProxy", snapshot.activeProxy)
        append(',')
        appendJsonField("lastError", snapshot.lastError)
        append(',')
        appendJsonField("lastRequestedSegment", snapshot.lastRequestedSegment)
        append(',')
        appendJsonField("lastSucceededSegment", snapshot.lastSucceededSegment)
        append(',')
        appendJsonField("lastFailedSegment", snapshot.lastFailedSegment)
        append(',')
        appendJsonField("consecutiveFailures", snapshot.consecutiveFailures)
        append(',')
        appendJsonField("recentSegmentSamples", buildRecentSegmentSamplesJson(snapshot), isRawJson = true)
        append(',')
        appendJsonField("prefetchConcurrency", snapshot.prefetchConcurrency)
        append(',')
        appendJsonField("pendingPrefetchCount", snapshot.pendingPrefetchCount)
        append(',')
        appendJsonField("currentLoadingAssetId", snapshot.currentLoadingAssetId)
        append(',')
        appendJsonField("currentLoadingAssetKind", snapshot.currentLoadingAssetKind)
        append(',')
        appendJsonField("currentLoadingTrackId", snapshot.currentLoadingTrackId)
        append(',')
        appendJsonField("currentLoadingSource", snapshot.currentLoadingSource)
        append(',')
        appendJsonField("slotStates", buildSlotStatesJson(snapshot), isRawJson = true)
        append(',')
        appendJsonField("assetDiagnostics", buildAssetDiagnosticsJson(snapshot), isRawJson = true)
        append(',')
        appendJsonField("currentPlaybackSlotIndex", snapshot.currentPlaybackSlotIndex)
        append(',')
        appendJsonField("currentPlaybackSlotReady", snapshot.currentPlaybackSlotReady)
        append(',')
        appendJsonField("bufferedSlotIndex", snapshot.bufferedSlotIndex)
        append(',')
        appendJsonField("startupGatePhase", snapshot.startupGatePhase)
        append(',')
        appendJsonField("startupGateReady", snapshot.startupGateReady)
        append(',')
        appendJsonField("startupGateDetail", snapshot.startupGateDetail)
        append(',')
        appendJsonField("currentStallReason", snapshot.currentStallReason)
        append(',')
        appendJsonField("playerPositionMs", snapshot.playerPositionMs)
        append(',')
        appendJsonField("playerBufferedPositionMs", snapshot.playerBufferedPositionMs)
        append(',')
        appendJsonField("playerIsLoading", snapshot.playerIsLoading)
        append(',')
        appendJsonField("continuousReadySlotCount", snapshot.continuousReadySlotCount)
        append(',')
        appendJsonField("continuousReadySlotDurationMs", snapshot.continuousReadySlotDurationMs)
        append(',')
        appendJsonField("sessionReadyAssetCount", snapshot.sessionReadyAssetCount)
        append(',')
        appendJsonField("sessionTotalAssetCount", snapshot.sessionTotalAssetCount)
        append(',')
        appendJsonField("sessionReadyBytes", snapshot.sessionReadyBytes)
        append(',')
        appendJsonField("inFlightCount", snapshot.inFlightCount)
        append(',')
        appendJsonField("directWinCount", snapshot.directWinCount)
        append(',')
        appendJsonField("proxyWinCount", snapshot.proxyWinCount)
        append(',')
        appendJsonField("directAverageElapsedMs", snapshot.directAverageElapsedMs)
        append(',')
        appendJsonField("proxyAverageElapsedMs", snapshot.proxyAverageElapsedMs)
        append(',')
        appendJsonField("lastFiveAverageElapsedMs", snapshot.lastFiveAverageElapsedMs)
        append(',')
        appendJsonField("lastFiveFailureCount", snapshot.lastFiveFailureCount)
        append(',')
        appendJsonField("lastTwentyAverageElapsedMs", snapshot.lastTwentyAverageElapsedMs)
        append(',')
        appendJsonField("lastTwentyFailureCount", snapshot.lastTwentyFailureCount)
        append(',')
        appendJsonField("severity", snapshot.severity.name)
        append(',')
        appendJsonField("isStale", snapshot.isStale)
        append(',')
        appendJsonField("insights", buildInsightsJson(snapshot), isRawJson = true)
        append(',')
        appendJsonField("primaryBottleneck", buildPrimaryBottleneckJson(snapshot), isRawJson = true)
        append(',')
        appendJsonField("timeoutCount", snapshot.timeoutCount)
        append(',')
        appendJsonField("fallbackCount", snapshot.fallbackCount)
        append(',')
        appendJsonField("lastFallbackReason", snapshot.lastFallbackReason)
        append('}')
    }

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
