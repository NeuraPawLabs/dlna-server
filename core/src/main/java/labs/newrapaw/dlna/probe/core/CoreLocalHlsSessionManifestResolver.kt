package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlannedTrackManifest
import labs.newrapaw.dlna.probe.core.session.SessionAssetKind

internal data class ResolvedSessionManifestSet(
    val masterPlaylist: SingleVariantMasterPlaylist?,
    val videoManifestUrl: String,
    val videoManifestBody: String,
    val audioTracks: List<PlannedTrackManifest>,
    val subtitleTracks: List<PlannedTrackManifest>,
)

internal class CoreLocalHlsSessionManifestResolver(
    private val fetchManifest: (String) -> String,
) {
    fun resolve(sourceUrl: String): ResolvedSessionManifestSet {
        val sourceManifest = fetchManifest(sourceUrl)
        val master = parseSingleVariantMasterManifest(sourceManifest, sourceUrl)
        if (looksLikeMasterPlaylist(sourceManifest) && master == null) {
            throw UnsupportedSessionSourceException(
                statusCode = 422,
                message = "Unsupported master playlist: multiple variants are not supported in session mode",
            )
        }
        if (master == null) {
            return ResolvedSessionManifestSet(
                masterPlaylist = null,
                videoManifestUrl = sourceUrl,
                videoManifestBody = sourceManifest,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
            )
        }

        return ResolvedSessionManifestSet(
            masterPlaylist = master,
            videoManifestUrl = master.variantUrl,
            videoManifestBody = fetchManifest(master.variantUrl),
            audioTracks = master.audioTracks.mapIndexed { index, track ->
                PlannedTrackManifest(
                    trackId = buildSessionTrackId("audio", track.name, track.language, index),
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = track.uri,
                    manifestBody = fetchManifest(track.uri),
                )
            },
            subtitleTracks = master.subtitleTracks.mapIndexed { index, track ->
                PlannedTrackManifest(
                    trackId = buildSessionTrackId("subtitle", track.name, track.language, index),
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    manifestUrl = track.uri,
                    manifestBody = fetchManifest(track.uri),
                )
            },
        )
    }
}
