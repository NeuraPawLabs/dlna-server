package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore
import labs.newrapaw.dlna.probe.core.session.SessionDownloader

internal fun ensureStartupAssetsReady(
    prepared: PreparedSessionPlayback,
    sessionAssetStore: SessionAssetStore,
    diagnosticsState: PlaybackDiagnosticsState,
) {
    val startupAssets = prepared.assetsById.values
        .filter { it.requiredForStartup }
        .sortedWith(compareBy<SessionAsset> { it.sequence ?: Int.MAX_VALUE }.thenBy { it.assetId })
    val missingStartup = startupAssets.count { asset ->
        !isPreparedAssetReady(sessionAssetStore, prepared, asset.assetId)
    }
    diagnosticsState.updateStartupGate(
        phase = "启动预热",
        ready = missingStartup == 0,
        detail = if (missingStartup == 0) "启动资源已就绪" else "仍缺少 $missingStartup 个启动资源",
    )
    startupAssets.forEach { asset ->
        if (!isPreparedAssetReady(sessionAssetStore, prepared, asset.assetId)) {
            prepared.prefetchController.enqueueFront(asset.assetId)
        }
    }
}

internal fun refreshPreparedSessionDiagnostics(
    activePreparedSession: PreparedSessionPlayback?,
    diagnosticsState: PlaybackDiagnosticsState,
    proxySettingsState: ProxySettingsState,
    latestPlayerPositionMs: Long?,
    latestBufferedPositionMs: Long?,
    playerIsLoading: Boolean,
) {
    val prepared = activePreparedSession ?: return
    val telemetry = if (latestPlayerPositionMs != null && latestBufferedPositionMs != null) {
        prepared.telemetryBridge.snapshot(
            currentPositionMs = latestPlayerPositionMs,
            bufferedPositionMs = latestBufferedPositionMs,
            isLoading = playerIsLoading,
        )
    } else {
        null
    }
    val currentSlotIndex = telemetry?.playHeadSlotIndex
    val bufferedSlotIndex = telemetry?.bufferHeadSlotIndex
    currentSlotIndex?.let { reprioritizePreparedSessionQueue(prepared, it) }
    diagnosticsState.updatePrefetchStats(
        prefetchConcurrency = proxySettingsState.prefetchConcurrency,
        pendingPrefetchCount = prepared.prefetchController.snapshotQueue().size,
        inFlightCount = prepared.prefetchController.snapshotActiveAssetIds().size,
    )
    val readyAssets = prepared.assetRuntime.values.count { it.state == SessionAssetState.READY }
    val readyBytes = prepared.assetRuntime.values.sumOf { it.localSizeBytes ?: 0L }
    diagnosticsState.updateAssetSummary(
        readyAssetCount = readyAssets,
        totalAssetCount = prepared.assetsById.size,
        readyBytes = readyBytes,
    )
    diagnosticsState.clearSlotDiagnostics(
        currentPlaybackSlotIndex = currentSlotIndex,
        bufferedSlotIndex = bufferedSlotIndex,
    )
    diagnosticsState.setSessionStatus(
        when {
            currentSlotIndex != null -> PlaybackSessionStatus.PLAYING.name
            else -> prepared.session.status.name
        },
    )
}

internal fun isPreparedAssetReady(
    sessionAssetStore: SessionAssetStore,
    prepared: PreparedSessionPlayback,
    assetId: String,
): Boolean {
    val runtime = prepared.assetRuntime[assetId]
    if (runtime?.state == SessionAssetState.READY) return true
    val file = sessionAssetStore.resolveAsset(prepared.session.sessionId, assetId)
    if (!file.exists()) return false
    prepared.assetRuntime.getOrPut(assetId) { SessionAssetRuntime() }.apply {
        state = SessionAssetState.READY
        localFile = file
        localSizeBytes = file.length()
    }
    return true
}

internal fun reprioritizePreparedSessionQueue(
    prepared: PreparedSessionPlayback,
    playHeadSlotIndex: Int,
) {
    val scheduledAssetIds = prepared.assetRuntime
        .filterValues {
            it.state == SessionAssetState.READY ||
                it.state == SessionAssetState.DOWNLOADING
        }
        .keys
    val queue = SessionDownloader.planPlaybackQueue(
        slots = prepared.session.timeline.slots,
        assetsById = prepared.assetsById,
        playHeadSlotIndex = playHeadSlotIndex,
        readyAssetIds = scheduledAssetIds,
    )
    prepared.prefetchController.replaceQueue(queue)
}

internal fun noteRequestedPlaybackSlot(
    prepared: PreparedSessionPlayback,
    slotIndex: Int,
    updatePlaybackPosition: (Long?) -> Unit,
    refreshPreparedSessionDiagnostics: () -> Unit,
) {
    updatePlaybackPosition(
        prepared.session.timeline.slots
            .firstOrNull { it.slotIndex == slotIndex }
            ?.startMs,
    )
    reprioritizePreparedSessionQueue(prepared, slotIndex)
    refreshPreparedSessionDiagnostics()
}
