package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import java.util.ArrayDeque

enum class PlaybackDiagnosticsStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    FAILED,
}

enum class DiagnosticsSeverity {
    OK,
    WARN,
    CRITICAL,
}

enum class SlotDiagnosticsState {
    NOT_READY,
    READY,
    PLAYING,
    BLOCKED,
    DEGRADED,
}

data class SegmentSample(
    val url: String,
    val source: String,
    val elapsedMs: Long,
    val success: Boolean,
    val reason: String? = null,
)

data class SlotDiagnosticsItem(
    val slotIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val state: SlotDiagnosticsState,
    val videoReady: Boolean,
    val audioReady: Boolean,
    val subtitleReady: Boolean,
    val blockedAssetKinds: List<SessionAssetKind>,
    val degradedAssetKinds: List<SessionAssetKind>,
    val videoAssetIdRef: String? = null,
    val audioAssetIdRefs: List<String> = emptyList(),
    val subtitleAssetIdRefs: List<String> = emptyList(),
    val prerequisiteAssetIdRefs: List<String> = emptyList(),
)

data class AssetDiagnosticsItem(
    val assetId: String,
    val kind: SessionAssetKind,
    val trackId: String?,
    val state: SessionAssetState,
    val localReady: Boolean,
    val sizeBytes: Long?,
    val lastElapsedMs: Long?,
    val lastSource: String?,
    val retryCount: Int,
    val failureReason: String?,
)

data class DiagnosticsInsight(
    val code: String,
    val message: String,
    val detail: String? = null,
)

data class PlaybackDiagnosticsSnapshot(
    val playbackStatus: PlaybackDiagnosticsStatus,
    val sessionStatus: String?,
    val sessionStartedAtMs: Long?,
    val sourceUrl: String,
    val localProxyUrl: String,
    val lastUpdatedAtMs: Long?,
    val upstreamMode: UpstreamMode,
    val activeProxy: String?,
    val lastError: String?,
    val lastRequestedSegment: String?,
    val lastSucceededSegment: String?,
    val lastFailedSegment: String?,
    val consecutiveFailures: Int,
    val recentSegmentSamples: List<SegmentSample>,
    val prefetchConcurrency: Int,
    val pendingPrefetchCount: Int,
    val inFlightCount: Int,
    val currentLoadingAssetId: String? = null,
    val currentLoadingAssetKind: String? = null,
    val currentLoadingTrackId: String? = null,
    val currentLoadingSource: String? = null,
    val slotStates: List<SlotDiagnosticsItem> = emptyList(),
    val assetDiagnostics: List<AssetDiagnosticsItem> = emptyList(),
    val currentPlaybackSlotIndex: Int? = null,
    val currentPlaybackSlotReady: Boolean? = null,
    val bufferedSlotIndex: Int? = null,
    val startupGatePhase: String? = null,
    val startupGateReady: Boolean? = null,
    val startupGateDetail: String? = null,
    val currentStallReason: String? = null,
    val playerPositionMs: Long?,
    val playerBufferedPositionMs: Long?,
    val playerIsLoading: Boolean?,
    val continuousReadySlotCount: Int = 0,
    val continuousReadySlotDurationMs: Long = 0L,
    val sessionReadyAssetCount: Int,
    val sessionTotalAssetCount: Int,
    val sessionReadyBytes: Long,
    val directWinCount: Int,
    val proxyWinCount: Int,
    val directAverageElapsedMs: Long?,
    val proxyAverageElapsedMs: Long?,
    val lastFiveAverageElapsedMs: Long?,
    val lastFiveFailureCount: Int,
    val lastTwentyAverageElapsedMs: Long?,
    val lastTwentyFailureCount: Int,
    val severity: DiagnosticsSeverity,
    val isStale: Boolean,
    val insights: List<DiagnosticsInsight>,
    val primaryBottleneck: DiagnosticsInsight?,
    val timeoutCount: Int,
    val fallbackCount: Int,
    val lastFallbackReason: String?,
) {
    companion object {
        fun empty(): PlaybackDiagnosticsSnapshot = PlaybackDiagnosticsSnapshot(
            playbackStatus = PlaybackDiagnosticsStatus.IDLE,
            sessionStatus = null,
            sessionStartedAtMs = null,
            sourceUrl = "",
            localProxyUrl = "",
            lastUpdatedAtMs = null,
            upstreamMode = UpstreamMode.PROXY_ONLY,
            activeProxy = null,
            lastError = null,
            lastRequestedSegment = null,
            lastSucceededSegment = null,
            lastFailedSegment = null,
            consecutiveFailures = 0,
            recentSegmentSamples = emptyList(),
            prefetchConcurrency = ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY,
            pendingPrefetchCount = 0,
            inFlightCount = 0,
            currentLoadingAssetId = null,
            currentLoadingAssetKind = null,
            currentLoadingTrackId = null,
            currentLoadingSource = null,
            slotStates = emptyList(),
            assetDiagnostics = emptyList(),
            currentPlaybackSlotIndex = null,
            currentPlaybackSlotReady = null,
            bufferedSlotIndex = null,
            startupGatePhase = null,
            startupGateReady = null,
            startupGateDetail = null,
            currentStallReason = null,
            playerPositionMs = null,
            playerBufferedPositionMs = null,
            playerIsLoading = null,
            continuousReadySlotCount = 0,
            continuousReadySlotDurationMs = 0L,
            sessionReadyAssetCount = 0,
            sessionTotalAssetCount = 0,
            sessionReadyBytes = 0L,
            directWinCount = 0,
            proxyWinCount = 0,
            directAverageElapsedMs = null,
            proxyAverageElapsedMs = null,
            lastFiveAverageElapsedMs = null,
            lastFiveFailureCount = 0,
            lastTwentyAverageElapsedMs = null,
            lastTwentyFailureCount = 0,
            severity = DiagnosticsSeverity.OK,
            isStale = false,
            insights = emptyList(),
            primaryBottleneck = null,
            timeoutCount = 0,
            fallbackCount = 0,
            lastFallbackReason = null,
        )
    }
}

class PlaybackDiagnosticsState(
    private val sampleLimit: Int = 20,
) {
    private val lock = Any()
    private val recentSamples = ArrayDeque<SegmentSample>()
    private var snapshot = PlaybackDiagnosticsSnapshot.empty()
    private val staleThresholdMs = 5_000L

    fun resetForPlayback(
        sourceUrl: String,
        localProxyUrl: String,
        settings: ProxySettingsState,
    ) = synchronized(lock) {
        recentSamples.clear()
        snapshot = PlaybackDiagnosticsSnapshot.empty().copy(
            playbackStatus = PlaybackDiagnosticsStatus.BUFFERING,
            sessionStartedAtMs = System.currentTimeMillis(),
            sourceUrl = sourceUrl,
            localProxyUrl = localProxyUrl,
            lastUpdatedAtMs = System.currentTimeMillis(),
            upstreamMode = settings.upstreamMode,
            activeProxy = settings.selectedProxy()?.displayUrl(),
            prefetchConcurrency = settings.prefetchConcurrency,
        )
    }

    fun setPlaybackStatus(status: PlaybackDiagnosticsStatus) = synchronized(lock) {
        touch(snapshot.copy(playbackStatus = status))
    }

    fun setSessionStatus(status: String?) = synchronized(lock) {
        touch(snapshot.copy(sessionStatus = status))
    }

    fun setLastError(message: String?) = synchronized(lock) {
        touch(
            snapshot.copy(
                lastError = message,
                playbackStatus = if (message.isNullOrBlank()) snapshot.playbackStatus else PlaybackDiagnosticsStatus.FAILED,
            ),
        )
    }

    fun setUpstreamSettings(settings: ProxySettingsState) = synchronized(lock) {
        touch(
            snapshot.copy(
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
        )
    }

    fun onSegmentRequested(url: String) = synchronized(lock) {
        touch(snapshot.copy(lastRequestedSegment = url))
    }

    fun onSegmentResult(
        url: String,
        source: String,
        elapsedMs: Long,
        success: Boolean,
        fallbackReason: String? = null,
    ) = synchronized(lock) {
        if (recentSamples.size >= sampleLimit) recentSamples.removeFirst()
        recentSamples.addLast(
            SegmentSample(
                url = url,
                source = source,
                elapsedMs = elapsedMs,
                success = success,
                reason = fallbackReason,
            ),
        )

        val nextConsecutiveFailures = if (success) 0 else snapshot.consecutiveFailures + 1
        val nextTimeouts = snapshot.timeoutCount + if (!success && fallbackReason?.contains("timeout", ignoreCase = true) == true) 1 else 0
        val nextFallbacks = snapshot.fallbackCount + if (!fallbackReason.isNullOrBlank()) 1 else 0
        val nextSamples = recentSamples.toList()
        val lastFive = nextSamples.takeLast(5)
        val lastFiveAverage = lastFive.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong()
        val lastFiveFailures = lastFive.count { !it.success }
        val lastTwentyAverage = nextSamples.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong()
        val lastTwentyFailures = nextSamples.count { !it.success }
        val directAverage = nextSamples.filter { it.success && it.source == "direct" }.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong()
        val proxyAverage = nextSamples.filter { it.success && it.source == "proxy" }.takeIf { it.isNotEmpty() }?.map { it.elapsedMs }?.average()?.toLong()
        touch(
            snapshot.copy(
                lastSucceededSegment = if (success) url else snapshot.lastSucceededSegment,
                lastFailedSegment = if (success) snapshot.lastFailedSegment else url,
                consecutiveFailures = nextConsecutiveFailures,
                recentSegmentSamples = nextSamples,
                directWinCount = snapshot.directWinCount + if (success && source == "direct") 1 else 0,
                proxyWinCount = snapshot.proxyWinCount + if (success && source == "proxy") 1 else 0,
                directAverageElapsedMs = directAverage,
                proxyAverageElapsedMs = proxyAverage,
                lastFiveAverageElapsedMs = lastFiveAverage,
                lastFiveFailureCount = lastFiveFailures,
                lastTwentyAverageElapsedMs = lastTwentyAverage,
                lastTwentyFailureCount = lastTwentyFailures,
                timeoutCount = nextTimeouts,
                fallbackCount = nextFallbacks,
                lastFallbackReason = fallbackReason ?: snapshot.lastFallbackReason,
                lastError = if (success) snapshot.lastError else fallbackReason ?: "segment fetch failed",
            ),
        )
    }

    fun updatePrefetchStats(
        prefetchConcurrency: Int,
        pendingPrefetchCount: Int,
        inFlightCount: Int,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                prefetchConcurrency = prefetchConcurrency,
                pendingPrefetchCount = pendingPrefetchCount,
                inFlightCount = inFlightCount,
            ),
        )
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                playerPositionMs = positionMs,
                playerBufferedPositionMs = bufferedPositionMs,
                playerIsLoading = isLoading,
            ),
        )
    }

    fun updateStartupGate(
        phase: String,
        ready: Boolean,
        detail: String?,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                startupGatePhase = phase,
                startupGateReady = ready,
                startupGateDetail = detail,
            ),
        )
    }

    fun updateSlotDiagnostics(
        slotStates: List<SlotDiagnosticsItem>,
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
        currentPlaybackSlotReady: Boolean?,
        continuousReadySlotCount: Int,
        continuousReadySlotDurationMs: Long,
    ) = synchronized(lock) {
        val currentSlot = slotStates.firstOrNull { it.slotIndex == currentPlaybackSlotIndex }
        val stallReason = when {
            currentPlaybackSlotReady == false && currentSlot != null -> buildCurrentSlotStallReason(currentSlot)
            else -> snapshot.currentStallReason
        }
        touch(
            snapshot.copy(
                slotStates = slotStates.sortedBy { it.slotIndex },
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
                currentPlaybackSlotReady = currentPlaybackSlotReady,
                continuousReadySlotCount = continuousReadySlotCount,
                continuousReadySlotDurationMs = continuousReadySlotDurationMs,
                currentStallReason = stallReason,
            ),
        )
    }

    fun updateAssetDiagnostics(assetDiagnostics: List<AssetDiagnosticsItem>) = synchronized(lock) {
        val readyAssets = assetDiagnostics.count { it.localReady }
        val readyBytes = assetDiagnostics.mapNotNull { it.sizeBytes }.sum()
        touch(
            snapshot.copy(
                assetDiagnostics = assetDiagnostics.sortedWith(compareBy<AssetDiagnosticsItem> { it.kind.name }.thenBy { it.assetId }),
                sessionReadyAssetCount = readyAssets,
                sessionTotalAssetCount = assetDiagnostics.size,
                sessionReadyBytes = readyBytes,
            ),
        )
    }

    fun updateAssetSummary(
        readyAssetCount: Int,
        totalAssetCount: Int,
        readyBytes: Long,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                assetDiagnostics = emptyList(),
                sessionReadyAssetCount = readyAssetCount,
                sessionTotalAssetCount = totalAssetCount,
                sessionReadyBytes = readyBytes,
            ),
        )
    }

    fun clearSlotDiagnostics(
        currentPlaybackSlotIndex: Int?,
        bufferedSlotIndex: Int?,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                slotStates = emptyList(),
                currentPlaybackSlotIndex = currentPlaybackSlotIndex,
                bufferedSlotIndex = bufferedSlotIndex,
                currentPlaybackSlotReady = null,
                continuousReadySlotCount = 0,
                continuousReadySlotDurationMs = 0L,
                currentStallReason = null,
            ),
        )
    }

    fun updateCurrentLoadingAsset(
        assetId: String?,
        kind: String?,
        trackId: String?,
        source: String?,
    ) = synchronized(lock) {
        touch(
            snapshot.copy(
                currentLoadingAssetId = assetId,
                currentLoadingAssetKind = kind,
                currentLoadingTrackId = trackId,
                currentLoadingSource = source,
            ),
        )
    }

    fun snapshot(): PlaybackDiagnosticsSnapshot = synchronized(lock) {
        val stale = snapshot.lastUpdatedAtMs?.let { System.currentTimeMillis() - it > staleThresholdMs } ?: false
        snapshot.copy(
            recentSegmentSamples = recentSamples.toList(),
            isStale = stale,
        )
    }

    private fun touch(nextSnapshot: PlaybackDiagnosticsSnapshot) {
        val withRules = nextSnapshot.copy(
            lastUpdatedAtMs = System.currentTimeMillis(),
            severity = diagnosticsSeverity(nextSnapshot),
            insights = diagnosticsInsights(nextSnapshot),
            primaryBottleneck = primaryBottleneck(nextSnapshot),
            isStale = false,
        )
        snapshot = withRules
    }
}

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

private fun buildCurrentSlotStallReason(slot: SlotDiagnosticsItem): String =
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
