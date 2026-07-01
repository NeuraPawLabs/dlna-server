package labs.newrapaw.dlna.probe.core

internal class PlaybackDiagnosticsSnapshotUpdater(
    private val snapshotRuntime: PlaybackDiagnosticsSnapshotRuntime,
    private val segmentTracker: PlaybackDiagnosticsSegmentTracker,
    private val nowMs: () -> Long,
) {
    fun resetForPlayback(
        sourceUrl: String,
        localProxyUrl: String,
        settings: ProxySettingsState,
    ) {
        segmentTracker.reset()
        val currentTimeMs = nowMs()
        snapshotRuntime.reset(
            PlaybackDiagnosticsSnapshot.empty().copy(
                playbackStatus = PlaybackDiagnosticsStatus.BUFFERING,
                sessionStartedAtMs = currentTimeMs,
                sourceUrl = sourceUrl,
                localProxyUrl = localProxyUrl,
                lastUpdatedAtMs = currentTimeMs,
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setPlaybackStatus(status: PlaybackDiagnosticsStatus) {
        val snapshot = snapshotRuntime.current()
        val clearedRecoverableError = if (status.clearsRecoverablePlaybackError()) {
            snapshot.lastError?.takeUnless(::isRecoverablePlaybackErrorMessage)
        } else {
            snapshot.lastError
        }
        snapshotRuntime.touch(
            snapshot.copy(
                playbackStatus = status,
                lastError = clearedRecoverableError,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setSessionStatus(status: String?) {
        snapshotRuntime.touch(snapshotRuntime.current().copy(sessionStatus = status), allowDerivedThrottle = false)
    }

    fun setLastError(message: String?) {
        val snapshot = snapshotRuntime.current()
        val nextStatus = when {
            message.isNullOrBlank() -> snapshot.playbackStatus
            isRecoverablePlaybackErrorMessage(message) -> snapshot.playbackStatus
            else -> PlaybackDiagnosticsStatus.FAILED
        }
        snapshotRuntime.touch(
            snapshot.copy(
                lastError = message,
                playbackStatus = nextStatus,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun setUpstreamSettings(settings: ProxySettingsState) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                upstreamMode = settings.upstreamMode,
                activeProxy = settings.selectedProxy()?.displayUrl(),
                prefetchConcurrency = settings.prefetchConcurrency,
            ),
            allowDerivedThrottle = false,
        )
    }

    fun updatePrefetchStats(
        prefetchConcurrency: Int,
        pendingPrefetchCount: Int,
        inFlightCount: Int,
    ) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                prefetchConcurrency = prefetchConcurrency,
                pendingPrefetchCount = pendingPrefetchCount,
                inFlightCount = inFlightCount,
            ),
            allowDerivedThrottle = true,
        )
    }

    fun updatePlayerTelemetry(
        positionMs: Long?,
        bufferedPositionMs: Long?,
        isLoading: Boolean?,
    ) {
        snapshotRuntime.touch(
            snapshotRuntime.current().copy(
                playerPositionMs = positionMs,
                playerBufferedPositionMs = bufferedPositionMs,
                playerIsLoading = isLoading,
            ),
            allowDerivedThrottle = true,
        )
    }
}

private fun isRecoverablePlaybackErrorMessage(message: String): Boolean =
    message.startsWith("Recovering from ") ||
        message.startsWith("Rebuilding session after ") ||
        message.startsWith("Session gone while loading asset: ")

private fun PlaybackDiagnosticsStatus.clearsRecoverablePlaybackError(): Boolean =
    this == PlaybackDiagnosticsStatus.PLAYING ||
        this == PlaybackDiagnosticsStatus.PAUSED ||
        this == PlaybackDiagnosticsStatus.STOPPED ||
        this == PlaybackDiagnosticsStatus.IDLE
