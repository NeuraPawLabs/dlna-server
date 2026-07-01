package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState

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
    val playbackStatus: PlaybackDiagnosticsStatus = PlaybackDiagnosticsStatus.IDLE,
    val sessionStatus: String? = null,
    val sessionStartedAtMs: Long? = null,
    val sourceUrl: String = "",
    val localProxyUrl: String = "",
    val lastUpdatedAtMs: Long? = null,
    val upstreamMode: UpstreamMode = UpstreamMode.PROXY_ONLY,
    val activeProxy: String? = null,
    val lastError: String? = null,
    val lastRequestedSegment: String? = null,
    val lastSucceededSegment: String? = null,
    val lastFailedSegment: String? = null,
    val consecutiveFailures: Int = 0,
    val recentSegmentSamples: List<SegmentSample> = emptyList(),
    val prefetchConcurrency: Int = ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY,
    val pendingPrefetchCount: Int = 0,
    val inFlightCount: Int = 0,
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
    val playerPositionMs: Long? = null,
    val playerBufferedPositionMs: Long? = null,
    val playerIsLoading: Boolean? = null,
    val continuousReadySlotCount: Int = 0,
    val continuousReadySlotDurationMs: Long = 0L,
    val sessionReadyAssetCount: Int = 0,
    val sessionTotalAssetCount: Int = 0,
    val sessionReadyBytes: Long = 0L,
    val directWinCount: Int = 0,
    val proxyWinCount: Int = 0,
    val directAverageElapsedMs: Long? = null,
    val proxyAverageElapsedMs: Long? = null,
    val lastFiveAverageElapsedMs: Long? = null,
    val lastFiveFailureCount: Int = 0,
    val lastTwentyAverageElapsedMs: Long? = null,
    val lastTwentyFailureCount: Int = 0,
    val severity: DiagnosticsSeverity = DiagnosticsSeverity.OK,
    val isStale: Boolean = false,
    val insights: List<DiagnosticsInsight> = emptyList(),
    val primaryBottleneck: DiagnosticsInsight? = null,
    val timeoutCount: Int = 0,
    val fallbackCount: Int = 0,
    val lastFallbackReason: String? = null,
) {
    companion object {
        fun empty(): PlaybackDiagnosticsSnapshot = PlaybackDiagnosticsSnapshot(
            playbackStatus = PlaybackDiagnosticsStatus.IDLE,
            upstreamMode = UpstreamMode.PROXY_ONLY,
            prefetchConcurrency = ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY,
        )
    }
}

internal data class PlaybackDiagnosticsSnapshotRuntimeState(
    val snapshot: PlaybackDiagnosticsSnapshot = PlaybackDiagnosticsSnapshot.empty(),
    val cachedSnapshot: PlaybackDiagnosticsSnapshot? = null,
    val cachedSnapshotVersion: Long = -1L,
    val snapshotDirty: Boolean = true,
    val snapshotVersion: Long = 0L,
    val lastDerivedAtMs: Long? = null,
    val canThrottleDerivedSnapshot: Boolean = false,
)
