package labs.newrapaw.dlna.probe.admin

internal fun slotHealthGrid(snapshot: AdminPlaybackDiagnosticsSnapshot): String {
    val items = snapshot.slotStates
    if (items.isEmpty()) {
        return "<p>暂无全量槽位状态</p>"
    }
    val readyWindowIndexes = snapshot.currentPlaybackSlotIndex?.let { start ->
        items.asSequence()
            .filter { it.slotIndex >= start }
            .takeWhile { it.state != AdminSlotDiagnosticsState.BLOCKED }
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

internal fun selectedSlotDetail(snapshot: AdminPlaybackDiagnosticsSnapshot): String {
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

private fun slotHealthCss(state: AdminSlotDiagnosticsState): String =
    when (state) {
        AdminSlotDiagnosticsState.NOT_READY -> "not-started"
        AdminSlotDiagnosticsState.READY -> "cached"
        AdminSlotDiagnosticsState.PLAYING -> "playing"
        AdminSlotDiagnosticsState.BLOCKED -> "failed"
        AdminSlotDiagnosticsState.DEGRADED -> "degraded"
    }

private fun slotHealthLabel(state: AdminSlotDiagnosticsState): String =
    when (state) {
        AdminSlotDiagnosticsState.NOT_READY -> "尚未就绪"
        AdminSlotDiagnosticsState.READY -> "可播"
        AdminSlotDiagnosticsState.PLAYING -> "当前播放"
        AdminSlotDiagnosticsState.BLOCKED -> "阻塞"
        AdminSlotDiagnosticsState.DEGRADED -> "降级可播"
    }

private fun slotDependencySummary(kinds: List<AdminSessionAssetKind>): String =
    if (kinds.isEmpty()) "-" else kinds.joinToString("、") { slotDependencyLabel(it) }

private fun slotDependencyLabel(kind: AdminSessionAssetKind): String =
    when (kind) {
        AdminSessionAssetKind.MANIFEST -> "清单"
        AdminSessionAssetKind.VIDEO_SEGMENT -> "视频"
        AdminSessionAssetKind.AUDIO_SEGMENT -> "音频"
        AdminSessionAssetKind.SUBTITLE_SEGMENT -> "字幕"
        AdminSessionAssetKind.INIT_SEGMENT -> "初始化段"
        AdminSessionAssetKind.KEY -> "密钥"
    }

private fun slotAssetDiagnosticsTable(items: List<AdminAssetDiagnosticsItem>): String {
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

private fun assetStateLabel(state: AdminSessionAssetState): String =
    when (state) {
        AdminSessionAssetState.NOT_STARTED -> "未开始"
        AdminSessionAssetState.QUEUED -> "已排队"
        AdminSessionAssetState.DOWNLOADING -> "下载中"
        AdminSessionAssetState.READY -> "已就绪"
        AdminSessionAssetState.FAILED -> "失败"
    }
