package labs.newrapaw.dlna.probe.admin

internal enum class AdminPlaybackDiagnosticsStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    FAILED,
}

internal enum class AdminDiagnosticsSeverity {
    OK,
    WARN,
    CRITICAL,
}

internal enum class AdminUpstreamMode {
    PROXY_ONLY,
    RACE_DIRECT_AND_PROXY,
}

internal enum class AdminSlotDiagnosticsState {
    NOT_READY,
    READY,
    PLAYING,
    BLOCKED,
    DEGRADED,
}

internal enum class AdminSessionAssetKind {
    MANIFEST,
    VIDEO_SEGMENT,
    AUDIO_SEGMENT,
    SUBTITLE_SEGMENT,
    INIT_SEGMENT,
    KEY,
}

internal enum class AdminSessionAssetState {
    NOT_STARTED,
    QUEUED,
    DOWNLOADING,
    READY,
    FAILED,
}

internal data class AdminSegmentSample(
    val url: String,
    val source: String,
    val elapsedMs: Long,
    val success: Boolean,
    val reason: String? = null,
)

internal data class AdminSlotDiagnosticsItem(
    val slotIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val state: AdminSlotDiagnosticsState,
    val videoReady: Boolean,
    val audioReady: Boolean,
    val subtitleReady: Boolean,
    val blockedAssetKinds: List<AdminSessionAssetKind>,
    val degradedAssetKinds: List<AdminSessionAssetKind>,
    val videoAssetIdRef: String? = null,
    val audioAssetIdRefs: List<String> = emptyList(),
    val subtitleAssetIdRefs: List<String> = emptyList(),
    val prerequisiteAssetIdRefs: List<String> = emptyList(),
)

internal data class AdminAssetDiagnosticsItem(
    val assetId: String,
    val kind: AdminSessionAssetKind,
    val trackId: String?,
    val state: AdminSessionAssetState,
    val localReady: Boolean,
    val sizeBytes: Long?,
    val lastElapsedMs: Long?,
    val lastSource: String?,
    val retryCount: Int,
    val failureReason: String?,
)

internal data class AdminDiagnosticsInsight(
    val code: String,
    val message: String,
    val detail: String? = null,
)

internal data class AdminPlaybackDiagnosticsSnapshot(
    val playbackStatus: AdminPlaybackDiagnosticsStatus,
    val sessionStatus: String?,
    val sessionStartedAtMs: Long?,
    val sourceUrl: String,
    val localProxyUrl: String,
    val lastUpdatedAtMs: Long?,
    val upstreamMode: AdminUpstreamMode,
    val activeProxy: String?,
    val lastError: String?,
    val recentSegmentSamples: List<AdminSegmentSample>,
    val prefetchConcurrency: Int,
    val pendingPrefetchCount: Int,
    val inFlightCount: Int,
    val currentLoadingAssetId: String? = null,
    val currentLoadingAssetKind: String? = null,
    val currentLoadingTrackId: String? = null,
    val currentLoadingSource: String? = null,
    val slotStates: List<AdminSlotDiagnosticsItem> = emptyList(),
    val assetDiagnostics: List<AdminAssetDiagnosticsItem> = emptyList(),
    val currentPlaybackSlotIndex: Int? = null,
    val currentPlaybackSlotReady: Boolean? = null,
    val bufferedSlotIndex: Int? = null,
    val startupGatePhase: String? = null,
    val startupGateReady: Boolean? = null,
    val startupGateDetail: String? = null,
    val currentStallReason: String? = null,
    val continuousReadySlotCount: Int = 0,
    val continuousReadySlotDurationMs: Long = 0L,
    val sessionReadyAssetCount: Int,
    val sessionTotalAssetCount: Int,
    val sessionReadyBytes: Long,
    val directWinCount: Int,
    val proxyWinCount: Int,
    val directAverageElapsedMs: Long?,
    val proxyAverageElapsedMs: Long?,
    val severity: AdminDiagnosticsSeverity,
    val isStale: Boolean,
    val insights: List<AdminDiagnosticsInsight>,
    val primaryBottleneck: AdminDiagnosticsInsight?,
    val timeoutCount: Int,
    val fallbackCount: Int,
    val lastFallbackReason: String?,
) {
    companion object {
        fun empty(): AdminPlaybackDiagnosticsSnapshot = AdminPlaybackDiagnosticsSnapshot(
            playbackStatus = AdminPlaybackDiagnosticsStatus.IDLE,
            sessionStatus = null,
            sessionStartedAtMs = null,
            sourceUrl = "",
            localProxyUrl = "",
            lastUpdatedAtMs = null,
            upstreamMode = AdminUpstreamMode.PROXY_ONLY,
            activeProxy = null,
            lastError = null,
            recentSegmentSamples = emptyList(),
            prefetchConcurrency = 4,
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
            continuousReadySlotCount = 0,
            continuousReadySlotDurationMs = 0L,
            sessionReadyAssetCount = 0,
            sessionTotalAssetCount = 0,
            sessionReadyBytes = 0L,
            directWinCount = 0,
            proxyWinCount = 0,
            directAverageElapsedMs = null,
            proxyAverageElapsedMs = null,
            severity = AdminDiagnosticsSeverity.OK,
            isStale = false,
            insights = emptyList(),
            primaryBottleneck = null,
            timeoutCount = 0,
            fallbackCount = 0,
            lastFallbackReason = null,
        )
    }
}
