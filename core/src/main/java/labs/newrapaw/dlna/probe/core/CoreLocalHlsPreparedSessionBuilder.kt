package labs.newrapaw.dlna.probe.core

import java.util.concurrent.ExecutorService
import labs.newrapaw.dlna.probe.core.session.PlannedSessionTimeline
import labs.newrapaw.dlna.probe.core.session.PlaybackSession
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import labs.newrapaw.dlna.probe.core.session.SessionLocalServer
import labs.newrapaw.dlna.probe.core.session.SessionPrefetchController
import labs.newrapaw.dlna.probe.core.session.SessionTimeline
import labs.newrapaw.dlna.probe.core.session.SessionTrackManifest

internal class CoreLocalHlsPreparedSessionBuilder(
    private val sessionLocalServer: SessionLocalServer,
    private val sessionPrefetchExecutor: ExecutorService,
) {
    fun buildPreparedSession(
        session: PlaybackSession,
        manifestSet: ResolvedSessionManifestSet,
        plan: PlannedSessionTimeline,
        prefetchConcurrency: Int,
    ): PreparedSessionPlayback {
        val master = manifestSet.masterPlaylist
        val audioTracks = manifestSet.audioTracks
        val subtitleTracks = manifestSet.subtitleTracks
        val preparedSession = session.copy(
            status = PlaybackSessionStatus.READY,
            timeline = SessionTimeline(slots = plan.slots, assets = plan.assets),
        )
        val assetsById = plan.assets.associateBy { it.assetId }
        return PreparedSessionPlayback(
            session = preparedSession,
            masterManifest = sessionLocalServer.buildMasterManifest(
                sessionId = session.sessionId,
                audioTracks = audioTracks.mapIndexed { index, track ->
                    SessionTrackManifest(
                        trackId = track.trackId,
                        name = master?.audioTracks?.getOrNull(index)?.name ?: track.trackId,
                        language = master?.audioTracks?.getOrNull(index)?.language,
                        kind = SessionAssetKind.AUDIO_SEGMENT,
                        playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.AUDIO_SEGMENT, track.trackId),
                    )
                },
                subtitleTracks = subtitleTracks.mapIndexed { index, track ->
                    SessionTrackManifest(
                        trackId = track.trackId,
                        name = master?.subtitleTracks?.getOrNull(index)?.name ?: track.trackId,
                        language = master?.subtitleTracks?.getOrNull(index)?.language,
                        kind = SessionAssetKind.SUBTITLE_SEGMENT,
                        playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.SUBTITLE_SEGMENT, track.trackId),
                    )
                },
            ),
            videoPlaylist = sessionLocalServer.buildMediaPlaylist(
                sessionId = session.sessionId,
                trackId = "video-main",
                kind = SessionAssetKind.VIDEO_SEGMENT,
                slots = plan.slots,
                assetsById = assetsById,
            ),
            audioPlaylists = audioTracks.associate { track ->
                track.trackId to sessionLocalServer.buildMediaPlaylist(
                    sessionId = session.sessionId,
                    trackId = track.trackId,
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    slots = plan.slots,
                    assetsById = assetsById,
                )
            },
            subtitlePlaylists = subtitleTracks.associate { track ->
                track.trackId to sessionLocalServer.buildMediaPlaylist(
                    sessionId = session.sessionId,
                    trackId = track.trackId,
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    slots = plan.slots,
                    assetsById = assetsById,
                )
            },
            assetsById = assetsById,
            assetRuntime = plan.assets.associate { it.assetId to SessionAssetRuntime() }.toMutableMap(),
            telemetryBridge = PlaybackTelemetryBridge(plan.slots),
            callTracker = SessionCallTracker(),
            prefetchController = SessionPrefetchController(
                queue = buildSessionPrefetchQueue(plan.assets),
                executor = sessionPrefetchExecutor,
                initialConcurrency = prefetchConcurrency,
                loadAsset = {},
            ),
            preparationFailure = null,
        )
    }

    fun buildFailedPreparedSession(
        session: PlaybackSession,
        error: UnsupportedSessionSourceException,
    ): PreparedSessionPlayback = PreparedSessionPlayback(
        session = session.copy(status = PlaybackSessionStatus.FAILED),
        masterManifest = "",
        videoPlaylist = "",
        audioPlaylists = emptyMap(),
        subtitlePlaylists = emptyMap(),
        assetsById = emptyMap(),
        assetRuntime = mutableMapOf(),
        telemetryBridge = PlaybackTelemetryBridge(emptyList()),
        callTracker = SessionCallTracker(),
        prefetchController = SessionPrefetchController(
            queue = java.util.ArrayDeque(),
            executor = sessionPrefetchExecutor,
            initialConcurrency = 1,
            loadAsset = {},
        ),
        preparationFailure = error,
    )
}
