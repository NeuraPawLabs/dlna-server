package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.ManifestPlanner
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.SessionAssetStore

internal class CoreLocalHlsSessionPreparer(
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val sessionManifestResolver: CoreLocalHlsSessionManifestResolver,
    private val manifestPlanner: ManifestPlanner,
    private val preparedSessionBuilder: CoreLocalHlsPreparedSessionBuilder,
    private val proxySettingsStore: ProxySettingsStore,
    private val sessionAssetLoader: CoreLocalHlsSessionAssetLoader,
    private val sessionAssetStore: SessionAssetStore,
    private val refreshDiagnosticsSnapshot: () -> Unit,
    private val safeLog: (String) -> Unit,
) {
    private val preparedSessionLock = Any()

    fun ensurePreparedSession(
        session: PlaybackSession,
        getActivePreparedSession: () -> PreparedSessionPlayback?,
        setActivePreparedSession: (PreparedSessionPlayback?) -> Unit,
    ): PreparedSessionPlayback? {
        getActivePreparedSession()
            ?.takeIf { it.session.sessionId == session.sessionId }
            ?.let { return it }
        return synchronized(preparedSessionLock) {
            getActivePreparedSession()
                ?.takeIf { it.session.sessionId == session.sessionId }
                ?.let { return@synchronized it }
            runCatching {
                diagnosticsState.setSessionStatus(PlaybackSessionStatus.PREPARING.name)
                val manifestSet = sessionManifestResolver.resolve(session.sourceUrl)
                diagnosticsState.updateStartupGate(
                    phase = "启动预热",
                    ready = false,
                    detail = "正在构建会话资源清单",
                )
                diagnosticsState.setSessionStatus(PlaybackSessionStatus.PRIMING.name)
                val videoTracks = manifestSet.videoTracks
                val audioTracks = manifestSet.audioTracks
                val subtitleTracks = manifestSet.subtitleTracks

                val plan = manifestPlanner.plan(
                    videoTracks = videoTracks,
                    primaryVideoTrackId = manifestSet.primaryVideoTrackId,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                )
                val prepared = preparedSessionBuilder.buildPreparedSession(
                    session = session,
                    manifestSet = manifestSet,
                    plan = plan,
                    prefetchConcurrency = proxySettingsStore.load().prefetchConcurrency,
                )
                prepared.prefetchController.replaceLoadAsset { assetId ->
                    sessionAssetLoader.loadSessionAsset(
                        prepared = prepared,
                        asset = prepared.assetsById.getValue(assetId),
                    )
                }
                setActivePreparedSession(prepared)
                prepared.prefetchController.start()
                ensureStartupAssetsReady(
                    prepared = prepared,
                    sessionAssetStore = sessionAssetStore,
                    diagnosticsState = diagnosticsState,
                )
                diagnosticsState.setSessionStatus(prepared.session.status.name)
                refreshDiagnosticsSnapshot()
                prepared
            }.recover { error ->
                if (error is UnsupportedSessionSourceException) {
                    preparedSessionBuilder.buildFailedPreparedSession(
                        session = session,
                        error = error,
                    ).also {
                        setActivePreparedSession(it)
                        diagnosticsState.setSessionStatus(PlaybackSessionStatus.FAILED.name)
                        diagnosticsState.setLastError(error.message)
                    }
                } else {
                    throw error
                }
            }.getOrElse { error ->
                safeLog("Session prepare failed: ${error::class.java.simpleName}: ${error.message}")
                null
            }
        }
    }
}
