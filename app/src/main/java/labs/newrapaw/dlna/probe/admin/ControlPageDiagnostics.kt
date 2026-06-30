package labs.newrapaw.dlna.probe.admin

fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / 1024.0 / 1024.0)
        bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0)
        else -> "$bytes B"
    }

internal fun buildDiagnosticsPanelHtml(snapshot: AdminPlaybackDiagnosticsSnapshot): String = """
    <div class="diagnostics-summary">
      <strong>当前判断</strong> <span class="severity-badge ${severityBadgeCss(snapshot.severity)}">${severityBadgeLabel(snapshot.severity)}</span>
      <p>最后更新：${formatDateTime(snapshot.lastUpdatedAtMs)}</p>
      ${if (snapshot.isStale) "<p>状态可能已过期</p>" else ""}
      ${snapshot.primaryBottleneck?.let { "<p><strong>当前瓶颈：</strong>${escapeHtml(it.message)}${it.detail?.let { detail -> "<br><small>${escapeHtml(detail)}</small>" } ?: ""}</p>" } ?: ""}
      ${if (snapshot.insights.isEmpty()) "<p>当前未发现明显异常。</p>" else "<ul>${snapshot.insights.joinToString("") { "<li data-code='${escapeHtml(it.code)}'>${escapeHtml(it.message)}</li>" }}</ul>"}
    </div>
    <details class="diagnostics-group" open>
      <summary>会话摘要</summary>
      <div class="diagnostics-group-body">
        <p>播放状态：${playbackStatusLabel(snapshot.playbackStatus)}</p>
        <p>会话状态：${sessionStatusLabel(snapshot.sessionStatus)}</p>
        <p>本次播放时长：${formatDuration(snapshot.sessionStartedAtMs)}</p>
        <p>原始媒体地址：${escapeHtml(snapshot.sourceUrl.ifBlank { "-" })}</p>
        <p>本地代理地址：${escapeHtml(snapshot.localProxyUrl.ifBlank { "-" })}</p>
        <p>当前上游模式：${upstreamModeLabel(snapshot.upstreamMode)}</p>
        <p>当前代理：${escapeHtml(snapshot.activeProxy ?: "直连")}</p>
        <p>启动门控：${startupGateSummary(snapshot)}</p>
        <p>当前卡顿原因：${escapeHtml(snapshot.currentStallReason ?: "-")}</p>
        <p>最近错误摘要：${escapeHtml(snapshot.lastError ?: "-")}</p>
      </div>
    </details>
    <details class="diagnostics-group" open>
      <summary>缓存健康图</summary>
      <div class="diagnostics-group-body">
        <h4>槽位全量健康图</h4>
        ${slotHealthGrid(snapshot)}
        ${selectedSlotDetail(snapshot)}
      </div>
    </details>
    <details class="diagnostics-group" open>
      <summary>会话缓存</summary>
      <div class="diagnostics-group-body">
        <p>已就绪资源：${snapshot.sessionReadyAssetCount} / ${snapshot.sessionTotalAssetCount}</p>
        <p>本地已落盘体积：${formatBytes(snapshot.sessionReadyBytes)}</p>
        <p>预取并发配置：${snapshot.prefetchConcurrency}</p>
        <p>当前下载中：${snapshot.inFlightCount}</p>
        <p>当前待预取：${snapshot.pendingPrefetchCount}</p>
        <p>连续可播窗口：${snapshot.continuousReadySlotCount} 槽 / ${formatSegmentDuration(snapshot.continuousReadySlotDurationMs)}</p>
        <p>当前加载资源：${currentLoadingSummary(snapshot)}</p>
      </div>
    </details>
    <details class="diagnostics-group" open>
      <summary>上游竞速</summary>
      <div class="diagnostics-group-body">
        <p>当前上游判断：${buildUpstreamSummary(snapshot)}</p>
        <p>直连胜出次数：${snapshot.directWinCount}</p>
        <p>代理胜出次数：${snapshot.proxyWinCount}</p>
        <p>直连平均耗时：${snapshot.directAverageElapsedMs?.let { "$it ms" } ?: "-"}</p>
        <p>代理平均耗时：${snapshot.proxyAverageElapsedMs?.let { "$it ms" } ?: "-"}</p>
        <p>超时次数：${snapshot.timeoutCount}</p>
        <p>回退次数：${snapshot.fallbackCount}</p>
        <p>最近回退原因：${escapeHtml(snapshot.lastFallbackReason ?: "-")}</p>
      </div>
    </details>
    <details class="diagnostics-group" open>
      <summary>最近分片明细</summary>
      <div class="diagnostics-group-body">
        ${recentSegmentsTable(snapshot.recentSegmentSamples)}
      </div>
    </details>
""".trimIndent()

private fun playbackStatusLabel(status: AdminPlaybackDiagnosticsStatus): String =
    when (status) {
        AdminPlaybackDiagnosticsStatus.IDLE -> "待命"
        AdminPlaybackDiagnosticsStatus.BUFFERING -> "加载中"
        AdminPlaybackDiagnosticsStatus.PLAYING -> "播放中"
        AdminPlaybackDiagnosticsStatus.PAUSED -> "已暂停"
        AdminPlaybackDiagnosticsStatus.STOPPED -> "已停止"
        AdminPlaybackDiagnosticsStatus.FAILED -> "失败"
    }

private fun sessionStatusLabel(status: String?): String =
    when (status) {
        "PREPARING" -> "准备中"
        "PRIMING" -> "启动预热"
        "READY" -> "已就绪"
        "PLAYING" -> "播放中"
        "DEGRADED" -> "降级可播"
        "STALLED" -> "已卡住"
        "COMPLETED" -> "已完成"
        "FAILED" -> "失败"
        "CLOSED" -> "已关闭"
        null -> "-"
        else -> escapeHtml(status)
    }

private fun upstreamModeLabel(mode: AdminUpstreamMode): String =
    when (mode) {
        AdminUpstreamMode.PROXY_ONLY -> "仅代理"
        AdminUpstreamMode.RACE_DIRECT_AND_PROXY -> "直连 + 代理竞速"
    }

private fun formatDuration(startedAtMs: Long?): String {
    if (startedAtMs == null) return "-"
    val totalSeconds = ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatDateTime(timestampMs: Long?): String {
    if (timestampMs == null) return "-"
    val instant = java.time.Instant.ofEpochMilli(timestampMs)
    val zone = java.time.ZoneId.systemDefault()
    val dateTime = java.time.ZonedDateTime.ofInstant(instant, zone)
    return "%02d:%02d:%02d".format(dateTime.hour, dateTime.minute, dateTime.second)
}

private fun startupGateSummary(snapshot: AdminPlaybackDiagnosticsSnapshot): String =
    when (snapshot.startupGateReady) {
        true -> "${escapeHtml(snapshot.startupGatePhase ?: "已完成")}（已满足）"
        false -> buildString {
            append(escapeHtml(snapshot.startupGatePhase ?: "启动预热"))
            append("（未满足）")
            snapshot.startupGateDetail?.takeIf { it.isNotBlank() }?.let {
                append(" ")
                append(escapeHtml(it))
            }
        }
        null -> "-"
    }

internal fun yesNoLabel(value: Boolean): String = if (value) "是" else "否"

internal fun formatSegmentDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0 ms"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}分${seconds}秒" else "${seconds}秒"
}

private fun currentLoadingSummary(snapshot: AdminPlaybackDiagnosticsSnapshot): String =
    snapshot.currentLoadingAssetId?.let { assetId ->
        buildString {
            append(escapeHtml(assetId))
            snapshot.currentLoadingAssetKind?.let { append(" / ${escapeHtml(it)}") }
            snapshot.currentLoadingTrackId?.let { append(" / ${escapeHtml(it)}") }
            snapshot.currentLoadingSource?.let { append(" / ${escapeHtml(it)}") }
        }
    } ?: "-"

private fun buildUpstreamSummary(snapshot: AdminPlaybackDiagnosticsSnapshot): String {
    val directAvg = snapshot.directAverageElapsedMs
    val proxyAvg = snapshot.proxyAverageElapsedMs
    if (snapshot.timeoutCount > 0 || snapshot.fallbackCount > 0) {
        if ((proxyAvg != null && directAvg != null && proxyAvg >= directAvg + 200) || snapshot.proxyWinCount < snapshot.directWinCount) {
            return "代理链路明显慢于直连"
        }
        if (directAvg != null && proxyAvg != null && directAvg >= proxyAvg + 200) {
            return "直连整体慢于代理"
        }
        return "双路都不稳定，超时/回退较多"
    }
    if (proxyAvg != null && directAvg != null && proxyAvg >= directAvg + 200) return "代理链路明显慢于直连"
    if (directAvg != null && proxyAvg != null && directAvg >= proxyAvg + 200) return "直连整体慢于代理"
    if (snapshot.directWinCount > 0 || snapshot.proxyWinCount > 0) return "当前竞速结果较均衡"
    return "当前样本不足"
}

private fun recentSegmentsTable(samples: List<AdminSegmentSample>): String {
    if (samples.isEmpty()) {
        return "<p>暂无分片记录</p>"
    }
    val rows = samples.asReversed().joinToString("") { sample ->
        val reasonTag = segmentReasonTag(sample)
        """
        <tr class="segment-row${if (sample.success) "" else " error"}" data-elapsed-ms="${sample.elapsedMs}" data-success="${sample.success}" data-source="${escapeHtml(sample.source.lowercase())}" data-reason="${escapeHtml(sample.reason.orEmpty())}">
          <td>${escapeHtml(compactSegmentLabel(sample.url))}</td>
          <td>${sourceTag(sample.source)}</td>
          <td>${sample.elapsedMs} ms</td>
          <td>$reasonTag</td>
          <td class="${if (sample.success) "result-ok" else "result-error"}">${if (sample.success) "成功" else "失败"}</td>
        </tr>
        """.trimIndent()
    }
    return """
        <table>
          <thead>
            <tr>
              <th>分片</th>
              <th>来源</th>
              <th>耗时</th>
              <th>原因</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody>$rows</tbody>
        </table>
    """.trimIndent()
}

private fun compactSegmentLabel(url: String): String {
    val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
    val query = runCatching { java.net.URI(url).query.orEmpty() }.getOrDefault("")
    val tail = path.substringAfterLast('/').ifBlank { url.substringAfterLast('/') }
    return if (query.isBlank()) tail else "$tail?$query"
}

private fun sourceTag(source: String): String {
    val normalized = source.lowercase()
    val css = when (normalized) {
        "direct" -> "direct"
        "proxy" -> "proxy"
        else -> "race"
    }
    val label = when (normalized) {
        "direct" -> "直连"
        "proxy" -> "代理"
        else -> source
    }
    return """<span class="source-tag $css">${escapeHtml(label)}</span>"""
}

private fun segmentReasonTag(sample: AdminSegmentSample): String {
    val classification = classifySegmentReason(sample)
    return """<span class="reason-tag ${classification.css}">${escapeHtml(classification.label)}</span>"""
}

private data class SegmentReasonClassification(
    val css: String,
    val label: String,
)

private fun classifySegmentReason(sample: AdminSegmentSample): SegmentReasonClassification {
    val reason = sample.reason.orEmpty()
    val lower = reason.lowercase()
    return when {
        lower.contains("timeout") -> SegmentReasonClassification("timeout", "超时")
        reason.isNotBlank() -> SegmentReasonClassification("fallback", "回退")
        !sample.success -> SegmentReasonClassification("failed", "失败")
        sample.elapsedMs >= 800 -> SegmentReasonClassification("slow", "慢分片")
        else -> SegmentReasonClassification("ok", "正常")
    }
}

private fun severityBadgeCss(severity: AdminDiagnosticsSeverity): String =
    when (severity) {
        AdminDiagnosticsSeverity.OK -> "ok"
        AdminDiagnosticsSeverity.WARN -> "warn"
        AdminDiagnosticsSeverity.CRITICAL -> "critical"
    }

private fun severityBadgeLabel(severity: AdminDiagnosticsSeverity): String =
    when (severity) {
        AdminDiagnosticsSeverity.OK -> "正常"
        AdminDiagnosticsSeverity.WARN -> "注意"
        AdminDiagnosticsSeverity.CRITICAL -> "异常"
    }
