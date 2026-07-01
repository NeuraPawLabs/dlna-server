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
import labs.newrapaw.dlna.probe.core.session.SessionVideoVariantManifest
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
        val primaryVideoTrackId = manifestSet.primaryVideoTrackId
        val videoTracks = manifestSet.videoTracks
        val audioTracks = manifestSet.audioTracks
        val subtitleTracks = manifestSet.subtitleTracks
        val preparedSession = session.copy(
            status = PlaybackSessionStatus.READY,
            timeline = SessionTimeline(slots = plan.slots, assets = plan.assets),
        )
        val assetsById = plan.assets.associateBy { it.assetId }
        val videoPlaylists = videoTracks.associate { track ->
            track.trackId to sessionLocalServer.buildMediaPlaylist(
                sessionId = session.sessionId,
                trackId = track.trackId,
                kind = SessionAssetKind.VIDEO_SEGMENT,
                slots = plan.slots,
                assetsById = assetsById,
                includeEndList = track.hasEndList,
            )
        }
        return PreparedSessionPlayback(
            session = preparedSession,
            masterManifest = sessionLocalServer.buildMasterManifest(
                sessionId = session.sessionId,
                videoVariants = if (master != null) {
                    master.videoVariants.map { variant ->
                        SessionVideoVariantManifest(
                            trackId = variant.trackId,
                            playlistPath = if (variant.trackId == primaryVideoTrackId && master.videoVariants.size == 1) {
                                sessionLocalServer.videoPlaylistPath(session.sessionId)
                            } else {
                                sessionLocalServer.videoPlaylistPath(session.sessionId, variant.trackId)
                            },
                            bandwidth = variant.bandwidth,
                            averageBandwidth = variant.averageBandwidth,
                            resolution = variant.resolution,
                            codecs = variant.codecs,
                            audioGroupId = variant.audioGroupId,
                            subtitleGroupId = variant.subtitleGroupId,
                        )
                    }
                } else {
                    listOf(
                        SessionVideoVariantManifest(
                            trackId = primaryVideoTrackId,
                            playlistPath = sessionLocalServer.videoPlaylistPath(session.sessionId),
                            bandwidth = videoTracks.firstOrNull()?.bandwidth ?: 1L,
                            averageBandwidth = videoTracks.firstOrNull()?.averageBandwidth,
                            resolution = videoTracks.firstOrNull()?.resolution,
                            codecs = videoTracks.firstOrNull()?.codecs,
                        ),
                    )
                },
                audioTracks = audioTracks.mapIndexed { index, track ->
                    SessionTrackManifest(
                        trackId = track.trackId,
                        name = track.displayName ?: master?.audioTracks?.getOrNull(index)?.name ?: track.trackId,
                        language = track.language ?: master?.audioTracks?.getOrNull(index)?.language,
                        kind = SessionAssetKind.AUDIO_SEGMENT,
                        playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.AUDIO_SEGMENT, track.trackId),
                        groupId = track.groupId,
                        isDefault = track.isDefault,
                    )
                },
                subtitleTracks = subtitleTracks.mapIndexed { index, track ->
                    SessionTrackManifest(
                        trackId = track.trackId,
                        name = track.displayName ?: master?.subtitleTracks?.getOrNull(index)?.name ?: track.trackId,
                        language = track.language ?: master?.subtitleTracks?.getOrNull(index)?.language,
                        kind = SessionAssetKind.SUBTITLE_SEGMENT,
                        playlistPath = sessionLocalServer.trackPlaylistPath(session.sessionId, SessionAssetKind.SUBTITLE_SEGMENT, track.trackId),
                        groupId = track.groupId,
                        isDefault = track.isDefault,
                    )
                },
            ),
            videoPlaylist = videoPlaylists.getValue(primaryVideoTrackId),
            primaryVideoTrackId = primaryVideoTrackId,
            videoPlaylists = videoPlaylists,
            audioPlaylists = audioTracks.associate { track ->
                track.trackId to sessionLocalServer.buildMediaPlaylist(
                    sessionId = session.sessionId,
                    trackId = track.trackId,
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    slots = plan.slots,
                    assetsById = assetsById,
                    includeEndList = track.hasEndList,
                )
            },
            subtitlePlaylists = subtitleTracks.associate { track ->
                track.trackId to sessionLocalServer.buildMediaPlaylist(
                    sessionId = session.sessionId,
                    trackId = track.trackId,
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    slots = plan.slots,
                    assetsById = assetsById,
                    includeEndList = track.hasEndList,
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
        primaryVideoTrackId = "video-main",
        videoPlaylists = emptyMap(),
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
