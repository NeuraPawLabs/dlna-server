package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind

internal fun deriveDiagnosticsSnapshot(nextSnapshot: PlaybackDiagnosticsSnapshot): PlaybackDiagnosticsSnapshot =
    nextSnapshot.copy(
        severity = diagnosticsSeverity(nextSnapshot),
        insights = diagnosticsInsights(nextSnapshot),
        primaryBottleneck = primaryBottleneck(nextSnapshot),
        isStale = false,
    )

private fun diagnosticsInsights(snapshot: PlaybackDiagnosticsSnapshot): List<DiagnosticsInsight> {
    val insights = mutableListOf<DiagnosticsInsight>()
    sessionAssetFailureInsight(snapshot)?.let(insights::add)
    sessionAssetTimeoutInsight(snapshot)?.let(insights::add)
    if (snapshot.startupGateReady == false) {
        insights += DiagnosticsInsight(
            "startup_gate_blocked",
            "启动门控尚未满足",
            snapshot.startupGateDetail ?: "启动预热仍未完成",
        )
    }
    if (snapshot.currentPlaybackSlotReady == false && snapshot.currentPlaybackSlotIndex != null) {
        insights += DiagnosticsInsight(
            "current_slot_blocked",
            "当前播放槽位存在硬依赖阻塞",
            snapshot.currentStallReason ?: "当前槽位 ${snapshot.currentPlaybackSlotIndex} 仍不可播",
        )
    }
    if (snapshot.currentPlaybackSlotIndex != null && snapshot.continuousReadySlotCount <= 1) {
        insights += DiagnosticsInsight(
            "slot_window_low",
            "当前播放槽位后的可播窗口不足",
            "当前槽位 ${snapshot.currentPlaybackSlotIndex}，连续可播槽位 ${snapshot.continuousReadySlotCount}，连续可播时长 ${snapshot.continuousReadySlotDurationMs} ms",
        )
    }
    if (snapshot.pendingPrefetchCount <= 0 && snapshot.currentPlaybackSlotIndex != null) {
        insights += DiagnosticsInsight(
            "prefetch_queue_empty",
            "预取队列已耗尽",
            "当前播放槽位 ${snapshot.currentPlaybackSlotIndex}，后台已没有待预取资源",
        )
    }
    if (snapshot.consecutiveFailures > 0) {
        insights += DiagnosticsInsight(
            "segment_failures",
            "存在连续失败分片",
            "当前连续失败次数 ${snapshot.consecutiveFailures}，阈值大于 0",
        )
    }
    val directAvg = snapshot.directAverageElapsedMs
    val proxyAvg = snapshot.proxyAverageElapsedMs
    if (directAvg != null && proxyAvg != null && proxyAvg >= directAvg + 200) {
        insights += DiagnosticsInsight(
            "proxy_slower_than_direct",
            "代理链路平均耗时明显高于直连",
            "代理 ${proxyAvg} ms，直连 ${directAvg} ms，差值 ${proxyAvg - directAvg} ms",
        )
    }
    if (snapshot.timeoutCount > 0) {
        insights += DiagnosticsInsight(
            "timeout_detected",
            "最近存在分片超时",
            "最近超时次数 ${snapshot.timeoutCount}，阈值大于 0",
        )
    }
    return insights
}

private fun diagnosticsSeverity(snapshot: PlaybackDiagnosticsSnapshot): DiagnosticsSeverity =
    when {
        sessionAssetFailureInsight(snapshot) != null -> DiagnosticsSeverity.CRITICAL
        sessionAssetTimeoutInsight(snapshot) != null -> DiagnosticsSeverity.CRITICAL
        snapshot.startupGateReady == false -> DiagnosticsSeverity.CRITICAL
        snapshot.currentPlaybackSlotReady == false && snapshot.currentPlaybackSlotIndex != null -> DiagnosticsSeverity.CRITICAL
        snapshot.currentPlaybackSlotIndex != null && snapshot.continuousReadySlotCount <= 1 -> DiagnosticsSeverity.CRITICAL
        snapshot.consecutiveFailures > 0 || snapshot.timeoutCount > 0 -> DiagnosticsSeverity.CRITICAL
        (snapshot.proxyAverageElapsedMs ?: 0) >= ((snapshot.directAverageElapsedMs ?: Long.MAX_VALUE) + 200) -> DiagnosticsSeverity.WARN
        else -> DiagnosticsSeverity.OK
    }

private fun primaryBottleneck(snapshot: PlaybackDiagnosticsSnapshot): DiagnosticsInsight? =
    when {
        sessionAssetFailureInsight(snapshot) != null -> sessionAssetFailureInsight(snapshot)
        sessionAssetTimeoutInsight(snapshot) != null -> sessionAssetTimeoutInsight(snapshot)
        snapshot.startupGateReady == false -> DiagnosticsInsight(
            "startup_gate_blocked",
            "启动门控尚未满足",
            snapshot.startupGateDetail ?: "启动预热仍未完成",
        )
        snapshot.currentPlaybackSlotReady == false && snapshot.currentPlaybackSlotIndex != null -> DiagnosticsInsight(
            "current_slot_blocked",
            "当前播放槽位存在硬依赖阻塞",
            snapshot.currentStallReason ?: "当前槽位 ${snapshot.currentPlaybackSlotIndex} 仍不可播",
        )
        snapshot.currentPlaybackSlotIndex != null && snapshot.continuousReadySlotCount <= 1 -> DiagnosticsInsight(
            "slot_window_low",
            "当前播放槽位后的可播窗口不足",
            "当前槽位 ${snapshot.currentPlaybackSlotIndex}，连续可播槽位 ${snapshot.continuousReadySlotCount}，连续可播时长 ${snapshot.continuousReadySlotDurationMs} ms",
        )
        snapshot.pendingPrefetchCount <= 0 && snapshot.currentPlaybackSlotIndex != null -> DiagnosticsInsight(
            "prefetch_queue_empty",
            "预取队列已耗尽",
            "当前播放槽位 ${snapshot.currentPlaybackSlotIndex}，后台已没有待预取资源",
        )
        snapshot.timeoutCount > 0 -> DiagnosticsInsight(
            "timeout_detected",
            "最近存在分片超时",
            "最近超时次数 ${snapshot.timeoutCount}，阈值大于 0",
        )
        snapshot.consecutiveFailures > 0 -> DiagnosticsInsight(
            "segment_failures",
            "存在连续失败分片",
            "当前连续失败次数 ${snapshot.consecutiveFailures}，阈值大于 0",
        )
        run {
            val directAvg = snapshot.directAverageElapsedMs
            val proxyAvg = snapshot.proxyAverageElapsedMs
            directAvg != null && proxyAvg != null && proxyAvg >= directAvg + 200
        } -> DiagnosticsInsight(
            "proxy_slower_than_direct",
            "代理链路平均耗时明显高于直连",
            "代理 ${snapshot.proxyAverageElapsedMs} ms，直连 ${snapshot.directAverageElapsedMs} ms，差值 ${(snapshot.proxyAverageElapsedMs ?: 0) - (snapshot.directAverageElapsedMs ?: 0)} ms",
        )
        else -> null
    }

private fun sessionAssetTimeoutInsight(snapshot: PlaybackDiagnosticsSnapshot): DiagnosticsInsight? {
    val error = snapshot.lastError ?: return null
    if (!error.startsWith("Session asset wait timed out: ")) return null
    val assetId = error.substringAfter(": ").ifBlank { "unknown" }
    return DiagnosticsInsight(
        "session_asset_timeout",
        "当前会话资源等待超时",
        "资源 $assetId 在本地供应等待窗口内未就绪",
    )
}

private fun sessionAssetFailureInsight(snapshot: PlaybackDiagnosticsSnapshot): DiagnosticsInsight? {
    val error = snapshot.lastError ?: return null
    if (!error.startsWith("Session asset failed: ")) return null
    val assetId = error.substringAfter(": ").ifBlank { "unknown" }
    return DiagnosticsInsight(
        "session_asset_failed",
        "当前会话资源已明确失败",
        "资源 $assetId 已进入失败状态，无法继续本地供应",
    )
}

internal fun currentSlotStallReason(slot: SlotDiagnosticsItem): String =
    when {
        slot.blockedAssetKinds.isNotEmpty() ->
            "当前槽位 ${slot.slotIndex} 缺少硬依赖：${slot.blockedAssetKinds.joinToString("、") { blockedAssetLabel(it) }}"
        !slot.videoReady -> "当前槽位 ${slot.slotIndex} 的视频资源未就绪"
        !slot.audioReady -> "当前槽位 ${slot.slotIndex} 的音频资源未就绪"
        else -> "当前槽位 ${slot.slotIndex} 仍不可播"
    }

private fun blockedAssetLabel(kind: SessionAssetKind): String =
    when (kind) {
        SessionAssetKind.MANIFEST -> "清单"
        SessionAssetKind.VIDEO_SEGMENT -> "视频"
        SessionAssetKind.AUDIO_SEGMENT -> "音频"
        SessionAssetKind.SUBTITLE_SEGMENT -> "字幕"
        SessionAssetKind.INIT_SEGMENT -> "初始化段"
        SessionAssetKind.KEY -> "密钥"
    }
