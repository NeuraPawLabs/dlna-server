package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlannedTrackManifest
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind

internal data class ResolvedSessionManifestSet(
    val masterPlaylist: HlsMasterPlaylist?,
    val primaryVideoTrackId: String,
    val videoTracks: List<PlannedTrackManifest>,
    val audioTracks: List<PlannedTrackManifest>,
    val subtitleTracks: List<PlannedTrackManifest>,
)

internal class CoreLocalHlsSessionManifestResolver(
    private val fetchManifest: (String) -> String,
) {
    fun resolve(sourceUrl: String): ResolvedSessionManifestSet {
        val sourceManifest = fetchManifest(sourceUrl)
        val master = parseMasterManifest(sourceManifest, sourceUrl)
        if (looksLikeMasterPlaylist(sourceManifest) && master == null) {
            throw UnsupportedSessionSourceException(
                statusCode = 422,
                message = "Unsupported master playlist: no playable variants were found",
            )
        }
        if (master == null) {
            return ResolvedSessionManifestSet(
                masterPlaylist = null,
                primaryVideoTrackId = "video-main",
                videoTracks = listOf(
                    PlannedTrackManifest(
                        trackId = "video-main",
                        kind = SessionAssetKind.VIDEO_SEGMENT,
                        manifestUrl = sourceUrl,
                        manifestBody = sourceManifest,
                        hasEndList = manifestHasEndList(sourceManifest),
                    ),
                ),
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
            )
        }

        val primaryVariant = master.videoVariants.maxWithOrNull(
            compareBy<HlsVariantStream>(
                { it.averageBandwidth ?: it.bandwidth ?: -1L },
                { it.uri },
            ),
        ) ?: throw UnsupportedSessionSourceException(
            statusCode = 422,
            message = "Unsupported master playlist: no playable variants were found",
        )
        val audioTracks = master.audioTracks
            .filter { track ->
                master.videoVariants.any { variant -> variant.audioGroupId == null || variant.audioGroupId == track.groupId }
            }
            .distinctBy { listOf(it.groupId, it.name, it.language, it.uri) }
            .mapIndexed { index, track ->
                val manifestBody = fetchManifest(track.uri)
                PlannedTrackManifest(
                    trackId = buildSessionTrackId("audio", track.name, track.language, index),
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = track.uri,
                    manifestBody = manifestBody,
                    hasEndList = manifestHasEndList(manifestBody),
                    groupId = track.groupId,
                    displayName = track.name,
                    language = track.language,
                    isDefault = track.isDefault,
                )
            }
        val subtitleTracks = master.subtitleTracks
            .filter { track ->
                master.videoVariants.any { variant -> variant.subtitleGroupId == null || variant.subtitleGroupId == track.groupId }
            }
            .distinctBy { listOf(it.groupId, it.name, it.language, it.uri) }
            .mapIndexed { index, track ->
                val manifestBody = fetchManifest(track.uri)
                PlannedTrackManifest(
                    trackId = buildSessionTrackId("subtitle", track.name, track.language, index),
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    manifestUrl = track.uri,
                    manifestBody = manifestBody,
                    hasEndList = manifestHasEndList(manifestBody),
                    groupId = track.groupId,
                    displayName = track.name,
                    language = track.language,
                    isDefault = track.isDefault,
                )
            }
        val videoTracks = master.videoVariants.map { variant ->
            val manifestBody = fetchManifest(variant.uri)
            PlannedTrackManifest(
                trackId = variant.trackId,
                kind = SessionAssetKind.VIDEO_SEGMENT,
                manifestUrl = variant.uri,
                manifestBody = manifestBody,
                hasEndList = manifestHasEndList(manifestBody),
                bandwidth = variant.bandwidth,
                averageBandwidth = variant.averageBandwidth,
                resolution = variant.resolution,
                codecs = variant.codecs,
                groupId = variant.audioGroupId,
            )
        }

        return ResolvedSessionManifestSet(
            masterPlaylist = master,
            primaryVideoTrackId = primaryVariant.trackId,
            videoTracks = videoTracks,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
        )
    }
}

private fun manifestHasEndList(manifestBody: String): Boolean =
    manifestBody.lineSequence().any { it.trim().equals("#EXT-X-ENDLIST", ignoreCase = true) }
