package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import java.net.URLDecoder

fun decodeFormUrl(body: String): String? =
    decodeFormValue(body, "url")

fun decodeFormValue(body: String, key: String): String? {
    val params = body.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
    }.toMap()
    return params[key]?.trim()?.takeIf { it.isNotEmpty() }
}

fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

fun proxyStatus(settings: ProxySettingsState): String =
    settings.selectedProxy()?.displayUrl() ?: "直连"

fun proxyOptionsHtml(settings: ProxySettingsState): String {
    val directChecked = if (settings.selectedProxyId == ProxySettingsState.DIRECT_PROXY_ID) " checked" else ""
    val direct = """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="direct"$directChecked> 直连</label>
        </div>
    """.trimIndent()

    val proxies = settings.proxies.joinToString("\n") { proxy ->
        val checked = if (settings.selectedProxyId == proxy.id) " checked" else ""
        """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="${escapeHtml(proxy.id)}"$checked></label>
          <code>${escapeHtml(proxy.displayUrl())}</code>
          <button type="submit" formaction="/control/proxy/delete" formmethod="post" name="proxyId" value="${escapeHtml(proxy.id)}">删除</button>
        </div>
        """.trimIndent()
    }

    return listOf(direct, proxies).filter { it.isNotBlank() }.joinToString("\n")
}

fun upstreamModeHtml(settings: ProxySettingsState): String {
    val proxyOnlyChecked = if (settings.upstreamMode == UpstreamMode.PROXY_ONLY) " checked" else ""
    val raceChecked = if (settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY) " checked" else ""
    val disabled = if (settings.selectedProxy() == null) " disabled" else ""
    return """
        <div>
          <label><input type="radio" name="upstreamMode" value="PROXY_ONLY"$proxyOnlyChecked$disabled> 仅代理</label>
          <label><input type="radio" name="upstreamMode" value="RACE_DIRECT_AND_PROXY"$raceChecked$disabled> 直连 + 代理竞速</label>
        </div>
    """.trimIndent()
}

fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / 1024.0 / 1024.0)
        bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0)
        else -> "$bytes B"
    }

fun buildDiagnosticsPanelHtml(snapshot: PlaybackDiagnosticsSnapshot): String = """
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

private fun playbackStatusLabel(status: PlaybackDiagnosticsStatus): String =
    when (status) {
        PlaybackDiagnosticsStatus.IDLE -> "待命"
        PlaybackDiagnosticsStatus.BUFFERING -> "加载中"
        PlaybackDiagnosticsStatus.PLAYING -> "播放中"
        PlaybackDiagnosticsStatus.PAUSED -> "已暂停"
        PlaybackDiagnosticsStatus.STOPPED -> "已停止"
        PlaybackDiagnosticsStatus.FAILED -> "失败"
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

private fun upstreamModeLabel(mode: UpstreamMode): String =
    when (mode) {
        UpstreamMode.PROXY_ONLY -> "仅代理"
        UpstreamMode.RACE_DIRECT_AND_PROXY -> "直连 + 代理竞速"
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

private fun startupGateSummary(snapshot: PlaybackDiagnosticsSnapshot): String =
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

private fun slotHealthGrid(snapshot: PlaybackDiagnosticsSnapshot): String {
    val items = snapshot.slotStates
    if (items.isEmpty()) {
        return "<p>暂无全量槽位状态</p>"
    }
    val readyWindowIndexes = snapshot.currentPlaybackSlotIndex?.let { start ->
        items.asSequence()
            .filter { it.slotIndex >= start }
            .takeWhile { it.state != SlotDiagnosticsState.BLOCKED }
            .map { it.slotIndex }
            .toSet()
    }.orEmpty()
    val blocks = items.joinToString("") { item ->
        val classes = buildList {
            add("segment-health-block")
            add(slotHealthCss(item.state))
            if (item.slotIndex in readyWindowIndexes) add("ready-window")
            if (item.slotIndex == snapshot.bufferedSlotIndex) add("buffer-edge")
        }.joinToString(" ")
        val markers = buildString {
            if (item.slotIndex in readyWindowIndexes) append("""<span class="segment-health-marker">连续可播</span>""")
            if (item.slotIndex == snapshot.bufferedSlotIndex) append("""<span class="segment-health-marker">播放器缓冲边界</span>""")
            if (item.slotIndex == snapshot.currentPlaybackSlotIndex) append("""<span class="segment-health-marker">当前槽位</span>""")
        }
        """<div class="$classes" title="槽位 ${item.slotIndex}">$markers</div>"""
    }
    return """<div id="segment-health-grid" class="segment-health-grid">$blocks</div>"""
}

private fun selectedSlotDetail(snapshot: PlaybackDiagnosticsSnapshot): String {
    val selected = snapshot.slotStates.firstOrNull { it.slotIndex == snapshot.currentPlaybackSlotIndex }
        ?: snapshot.slotStates.firstOrNull()
        ?: return """<div id="segment-health-detail" class="segment-health-detail"><strong>槽位详情</strong><p>-</p></div>"""
    val relatedAssetIds = buildList {
        addAll(selected.prerequisiteAssetIdRefs)
        selected.videoAssetIdRef?.let(::add)
        addAll(selected.audioAssetIdRefs)
        addAll(selected.subtitleAssetIdRefs)
    }.distinct()
    val relatedAssets = snapshot.assetDiagnostics.filter { it.assetId in relatedAssetIds }
    return """
        <div id="segment-health-detail" class="segment-health-detail">
          <strong>槽位详情</strong>
          <p>槽位索引：${selected.slotIndex}</p>
          <p>时间范围：${formatSegmentDuration(selected.startMs)} - ${formatSegmentDuration(selected.endMs)}</p>
          <p>当前状态：${slotHealthLabel(selected.state)}</p>
          <p>视频就绪：${yesNoLabel(selected.videoReady)}</p>
          <p>音频就绪：${yesNoLabel(selected.audioReady)}</p>
          <p>字幕就绪：${yesNoLabel(selected.subtitleReady)}</p>
          <p>当前槽位：${if (selected.slotIndex == snapshot.currentPlaybackSlotIndex) "是" else "否"}</p>
          <p>连续可播：${snapshot.continuousReadySlotCount} 槽 / ${formatSegmentDuration(snapshot.continuousReadySlotDurationMs)}</p>
          <p>播放器缓冲边界：${snapshot.bufferedSlotIndex?.let { "槽位 $it" } ?: "-"}</p>
          <p>阻塞依赖：${slotDependencySummary(selected.blockedAssetKinds)}</p>
          <p>降级依赖：${slotDependencySummary(selected.degradedAssetKinds)}</p>
          ${slotAssetDiagnosticsTable(relatedAssets)}
        </div>
    """.trimIndent()
}

private fun slotHealthCss(state: SlotDiagnosticsState): String =
    when (state) {
        SlotDiagnosticsState.NOT_READY -> "not-started"
        SlotDiagnosticsState.READY -> "cached"
        SlotDiagnosticsState.PLAYING -> "playing"
        SlotDiagnosticsState.BLOCKED -> "failed"
        SlotDiagnosticsState.DEGRADED -> "degraded"
    }

private fun slotHealthLabel(state: SlotDiagnosticsState): String =
    when (state) {
        SlotDiagnosticsState.NOT_READY -> "尚未就绪"
        SlotDiagnosticsState.READY -> "可播"
        SlotDiagnosticsState.PLAYING -> "当前播放"
        SlotDiagnosticsState.BLOCKED -> "阻塞"
        SlotDiagnosticsState.DEGRADED -> "降级可播"
    }

private fun yesNoLabel(value: Boolean): String = if (value) "是" else "否"

private fun formatSegmentDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0 ms"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}分${seconds}秒" else "${seconds}秒"
}

private fun slotDependencySummary(kinds: List<SessionAssetKind>): String =
    if (kinds.isEmpty()) "-" else kinds.joinToString("、") { slotDependencyLabel(it) }

private fun slotDependencyLabel(kind: SessionAssetKind): String =
    when (kind) {
        SessionAssetKind.MANIFEST -> "清单"
        SessionAssetKind.VIDEO_SEGMENT -> "视频"
        SessionAssetKind.AUDIO_SEGMENT -> "音频"
        SessionAssetKind.SUBTITLE_SEGMENT -> "字幕"
        SessionAssetKind.INIT_SEGMENT -> "初始化段"
        SessionAssetKind.KEY -> "密钥"
    }

private fun slotAssetDiagnosticsTable(items: List<AssetDiagnosticsItem>): String {
    if (items.isEmpty()) return "<p>关联资源：-</p>"
    val rows = items.joinToString("") { item ->
        """
        <tr>
          <td>${escapeHtml(item.assetId)}</td>
          <td>${slotDependencyLabel(item.kind)}</td>
          <td>${escapeHtml(item.trackId ?: "-")}</td>
          <td>${assetStateLabel(item.state)}</td>
          <td>${if (item.localReady) "是" else "否"}</td>
          <td>${item.lastElapsedMs?.let { "$it ms" } ?: "-"}</td>
          <td>${escapeHtml(item.lastSource ?: "-")}</td>
          <td>${item.retryCount}</td>
          <td>${item.sizeBytes?.let(::formatBytes) ?: "-"}</td>
          <td>${escapeHtml(item.failureReason ?: "-")}</td>
        </tr>
        """.trimIndent()
    }
    return """
        <div class="slot-asset-table">
          <strong>关联资源</strong>
          <table>
            <thead>
              <tr>
                <th>资源</th>
                <th>类型</th>
                <th>轨道</th>
                <th>状态</th>
                <th>本地可用</th>
                <th>耗时</th>
                <th>来源</th>
                <th>重试</th>
                <th>大小</th>
                <th>失败原因</th>
              </tr>
            </thead>
            <tbody>$rows</tbody>
          </table>
        </div>
    """.trimIndent()
}

private fun assetStateLabel(state: SessionAssetState): String =
    when (state) {
        SessionAssetState.NOT_STARTED -> "未开始"
        SessionAssetState.QUEUED -> "已排队"
        SessionAssetState.DOWNLOADING -> "下载中"
        SessionAssetState.READY -> "已就绪"
        SessionAssetState.FAILED -> "失败"
    }

private fun currentLoadingSummary(snapshot: PlaybackDiagnosticsSnapshot): String =
    snapshot.currentLoadingAssetId?.let { assetId ->
        buildString {
            append(escapeHtml(assetId))
            snapshot.currentLoadingAssetKind?.let { append(" / ${escapeHtml(it)}") }
            snapshot.currentLoadingTrackId?.let { append(" / ${escapeHtml(it)}") }
            snapshot.currentLoadingSource?.let { append(" / ${escapeHtml(it)}") }
        }
    } ?: "-"

private fun buildUpstreamSummary(snapshot: PlaybackDiagnosticsSnapshot): String {
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

private fun recentSegmentsTable(samples: List<SegmentSample>): String {
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

private fun segmentReasonTag(sample: SegmentSample): String {
    val classification = classifySegmentReason(sample)
    return """<span class="reason-tag ${classification.css}">${escapeHtml(classification.label)}</span>"""
}

private data class SegmentReasonClassification(
    val css: String,
    val label: String,
)

private fun classifySegmentReason(sample: SegmentSample): SegmentReasonClassification {
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

private fun severityBadgeCss(severity: DiagnosticsSeverity): String =
    when (severity) {
        DiagnosticsSeverity.OK -> "ok"
        DiagnosticsSeverity.WARN -> "warn"
        DiagnosticsSeverity.CRITICAL -> "critical"
    }

private fun severityBadgeLabel(severity: DiagnosticsSeverity): String =
    when (severity) {
        DiagnosticsSeverity.OK -> "正常"
        DiagnosticsSeverity.WARN -> "注意"
        DiagnosticsSeverity.CRITICAL -> "异常"
    }
