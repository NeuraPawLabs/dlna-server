package labs.newrapaw.dlna.probe.admin

import labs.newrapaw.dlna.probe.core.AssetDiagnosticsItem
import labs.newrapaw.dlna.probe.core.DiagnosticsInsight
import labs.newrapaw.dlna.probe.core.DiagnosticsSeverity
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsStatus
import labs.newrapaw.dlna.probe.core.SegmentSample
import labs.newrapaw.dlna.probe.core.SlotDiagnosticsItem
import labs.newrapaw.dlna.probe.core.SlotDiagnosticsState
import labs.newrapaw.dlna.probe.core.UpstreamMode
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState

internal fun PlaybackDiagnosticsSnapshot.toAdminPlaybackDiagnosticsSnapshot(): AdminPlaybackDiagnosticsSnapshot =
    AdminPlaybackDiagnosticsSnapshot(
        playbackStatus = playbackStatus.toAdminPlaybackDiagnosticsStatus(),
        sessionStatus = sessionStatus,
        sessionStartedAtMs = sessionStartedAtMs,
        sourceUrl = sourceUrl,
        localProxyUrl = localProxyUrl,
        lastUpdatedAtMs = lastUpdatedAtMs,
        upstreamMode = upstreamMode.toAdminUpstreamMode(),
        activeProxy = activeProxy,
        lastError = lastError,
        recentSegmentSamples = recentSegmentSamples.map { it.toAdminSegmentSample() },
        prefetchConcurrency = prefetchConcurrency,
        pendingPrefetchCount = pendingPrefetchCount,
        inFlightCount = inFlightCount,
        currentLoadingAssetId = currentLoadingAssetId,
        currentLoadingAssetKind = currentLoadingAssetKind,
        currentLoadingTrackId = currentLoadingTrackId,
        currentLoadingSource = currentLoadingSource,
        slotStates = slotStates.map { it.toAdminSlotDiagnosticsItem() },
        assetDiagnostics = assetDiagnostics.map { it.toAdminAssetDiagnosticsItem() },
        currentPlaybackSlotIndex = currentPlaybackSlotIndex,
        currentPlaybackSlotReady = currentPlaybackSlotReady,
        bufferedSlotIndex = bufferedSlotIndex,
        startupGatePhase = startupGatePhase,
        startupGateReady = startupGateReady,
        startupGateDetail = startupGateDetail,
        currentStallReason = currentStallReason,
        continuousReadySlotCount = continuousReadySlotCount,
        continuousReadySlotDurationMs = continuousReadySlotDurationMs,
        sessionReadyAssetCount = sessionReadyAssetCount,
        sessionTotalAssetCount = sessionTotalAssetCount,
        sessionReadyBytes = sessionReadyBytes,
        directWinCount = directWinCount,
        proxyWinCount = proxyWinCount,
        directAverageElapsedMs = directAverageElapsedMs,
        proxyAverageElapsedMs = proxyAverageElapsedMs,
        severity = severity.toAdminDiagnosticsSeverity(),
        isStale = isStale,
        insights = insights.map { it.toAdminDiagnosticsInsight() },
        primaryBottleneck = primaryBottleneck?.toAdminDiagnosticsInsight(),
        timeoutCount = timeoutCount,
        fallbackCount = fallbackCount,
        lastFallbackReason = lastFallbackReason,
    )

private fun PlaybackDiagnosticsStatus.toAdminPlaybackDiagnosticsStatus(): AdminPlaybackDiagnosticsStatus =
    when (this) {
        PlaybackDiagnosticsStatus.IDLE -> AdminPlaybackDiagnosticsStatus.IDLE
        PlaybackDiagnosticsStatus.BUFFERING -> AdminPlaybackDiagnosticsStatus.BUFFERING
        PlaybackDiagnosticsStatus.PLAYING -> AdminPlaybackDiagnosticsStatus.PLAYING
        PlaybackDiagnosticsStatus.PAUSED -> AdminPlaybackDiagnosticsStatus.PAUSED
        PlaybackDiagnosticsStatus.STOPPED -> AdminPlaybackDiagnosticsStatus.STOPPED
        PlaybackDiagnosticsStatus.FAILED -> AdminPlaybackDiagnosticsStatus.FAILED
    }

private fun DiagnosticsSeverity.toAdminDiagnosticsSeverity(): AdminDiagnosticsSeverity =
    when (this) {
        DiagnosticsSeverity.OK -> AdminDiagnosticsSeverity.OK
        DiagnosticsSeverity.WARN -> AdminDiagnosticsSeverity.WARN
        DiagnosticsSeverity.CRITICAL -> AdminDiagnosticsSeverity.CRITICAL
    }

private fun UpstreamMode.toAdminUpstreamMode(): AdminUpstreamMode =
    when (this) {
        UpstreamMode.PROXY_ONLY -> AdminUpstreamMode.PROXY_ONLY
        UpstreamMode.RACE_DIRECT_AND_PROXY -> AdminUpstreamMode.RACE_DIRECT_AND_PROXY
    }

private fun SegmentSample.toAdminSegmentSample(): AdminSegmentSample =
    AdminSegmentSample(
        url = url,
        source = source,
        elapsedMs = elapsedMs,
        success = success,
        reason = reason,
    )

private fun SlotDiagnosticsItem.toAdminSlotDiagnosticsItem(): AdminSlotDiagnosticsItem =
    AdminSlotDiagnosticsItem(
        slotIndex = slotIndex,
        startMs = startMs,
        endMs = endMs,
        state = state.toAdminSlotDiagnosticsState(),
        videoReady = videoReady,
        audioReady = audioReady,
        subtitleReady = subtitleReady,
        blockedAssetKinds = blockedAssetKinds.map { it.toAdminSessionAssetKind() },
        degradedAssetKinds = degradedAssetKinds.map { it.toAdminSessionAssetKind() },
        videoAssetIdRef = videoAssetIdRef,
        audioAssetIdRefs = audioAssetIdRefs,
        subtitleAssetIdRefs = subtitleAssetIdRefs,
        prerequisiteAssetIdRefs = prerequisiteAssetIdRefs,
    )

private fun AssetDiagnosticsItem.toAdminAssetDiagnosticsItem(): AdminAssetDiagnosticsItem =
    AdminAssetDiagnosticsItem(
        assetId = assetId,
        kind = kind.toAdminSessionAssetKind(),
        trackId = trackId,
        state = state.toAdminSessionAssetState(),
        localReady = localReady,
        sizeBytes = sizeBytes,
        lastElapsedMs = lastElapsedMs,
        lastSource = lastSource,
        retryCount = retryCount,
        failureReason = failureReason,
    )

private fun DiagnosticsInsight.toAdminDiagnosticsInsight(): AdminDiagnosticsInsight =
    AdminDiagnosticsInsight(
        code = code,
        message = message,
        detail = detail,
    )

private fun SlotDiagnosticsState.toAdminSlotDiagnosticsState(): AdminSlotDiagnosticsState =
    when (this) {
        SlotDiagnosticsState.NOT_READY -> AdminSlotDiagnosticsState.NOT_READY
        SlotDiagnosticsState.READY -> AdminSlotDiagnosticsState.READY
        SlotDiagnosticsState.PLAYING -> AdminSlotDiagnosticsState.PLAYING
        SlotDiagnosticsState.BLOCKED -> AdminSlotDiagnosticsState.BLOCKED
        SlotDiagnosticsState.DEGRADED -> AdminSlotDiagnosticsState.DEGRADED
    }

private fun SessionAssetKind.toAdminSessionAssetKind(): AdminSessionAssetKind =
    when (this) {
        SessionAssetKind.MANIFEST -> AdminSessionAssetKind.MANIFEST
        SessionAssetKind.VIDEO_SEGMENT -> AdminSessionAssetKind.VIDEO_SEGMENT
        SessionAssetKind.AUDIO_SEGMENT -> AdminSessionAssetKind.AUDIO_SEGMENT
        SessionAssetKind.SUBTITLE_SEGMENT -> AdminSessionAssetKind.SUBTITLE_SEGMENT
        SessionAssetKind.INIT_SEGMENT -> AdminSessionAssetKind.INIT_SEGMENT
        SessionAssetKind.KEY -> AdminSessionAssetKind.KEY
    }

private fun SessionAssetState.toAdminSessionAssetState(): AdminSessionAssetState =
    when (this) {
        SessionAssetState.NOT_STARTED -> AdminSessionAssetState.NOT_STARTED
        SessionAssetState.QUEUED -> AdminSessionAssetState.QUEUED
        SessionAssetState.DOWNLOADING -> AdminSessionAssetState.DOWNLOADING
        SessionAssetState.READY -> AdminSessionAssetState.READY
        SessionAssetState.FAILED -> AdminSessionAssetState.FAILED
    }
