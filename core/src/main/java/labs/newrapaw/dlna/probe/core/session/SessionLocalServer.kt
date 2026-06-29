package labs.newrapaw.dlna.probe.core.session

import java.net.URI

data class SessionTrackManifest(
    val trackId: String,
    val name: String,
    val language: String?,
    val kind: SessionAssetKind,
    val playlistPath: String,
)

class SessionLocalServer {
    fun masterManifestPath(sessionId: String): String = "/session/$sessionId/manifest.m3u8"

    fun videoPlaylistPath(sessionId: String): String = "/session/$sessionId/video.m3u8"

    fun trackPlaylistPath(sessionId: String, kind: SessionAssetKind, trackId: String): String =
        when (kind) {
            SessionAssetKind.AUDIO_SEGMENT -> "/session/$sessionId/audio/$trackId.m3u8"
            SessionAssetKind.SUBTITLE_SEGMENT -> "/session/$sessionId/subtitle/$trackId.m3u8"
            else -> error("Unsupported track playlist kind: $kind")
        }

    fun assetPath(sessionId: String, asset: SessionAsset): String =
        "/session/$sessionId/asset/${asset.assetId}${assetPathSuffix(asset)}"

    fun buildMasterManifest(
        sessionId: String,
        videoPlaylistPath: String = videoPlaylistPath(sessionId),
        audioTracks: List<SessionTrackManifest> = emptyList(),
        subtitleTracks: List<SessionTrackManifest> = emptyList(),
    ): String = buildString {
        append("#EXTM3U\n")
        audioTracks.forEach { track ->
            append(
                """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="${escape(track.name)}",AUTOSELECT=YES,DEFAULT=${if (track == audioTracks.first()) "YES" else "NO"},URI="${track.playlistPath}"""" +
                    "\n",
            )
        }
        subtitleTracks.forEach { track ->
            append(
                """#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="${escape(track.name)}",AUTOSELECT=YES,DEFAULT=${if (track == subtitleTracks.first()) "YES" else "NO"},URI="${track.playlistPath}"""" +
                    "\n",
            )
        }
        append("#EXT-X-STREAM-INF:BANDWIDTH=1")
        if (audioTracks.isNotEmpty()) append(""",AUDIO="audio"""")
        if (subtitleTracks.isNotEmpty()) append(""",SUBTITLES="subs"""")
        append("\n")
        append(videoPlaylistPath)
        append('\n')
    }

    fun buildMediaPlaylist(
        sessionId: String,
        trackId: String,
        kind: SessionAssetKind,
        slots: List<TimelineSlot>,
        assetsById: Map<String, SessionAsset> = emptyMap(),
    ): String = buildString {
        append("#EXTM3U\n")
        append("#EXT-X-VERSION:3\n")
        val prerequisiteIds = when (kind) {
            SessionAssetKind.VIDEO_SEGMENT -> slots.firstOrNull()?.prerequisiteAssetIds.orEmpty()
            SessionAssetKind.AUDIO_SEGMENT -> slots.firstOrNull()?.audioPrerequisiteAssetIds?.get(trackId).orEmpty()
            SessionAssetKind.SUBTITLE_SEGMENT -> slots.firstOrNull()?.subtitlePrerequisiteAssetIds?.get(trackId).orEmpty()
            else -> emptyList()
        }
        prerequisiteIds.forEach { prerequisiteId ->
            val prerequisiteAsset = assetsById[prerequisiteId]
            when {
                prerequisiteId.startsWith("init-") -> append(
                    """#EXT-X-MAP:URI="${prerequisiteAsset?.let { assetPath(sessionId, it) } ?: "/session/$sessionId/asset/$prerequisiteId.ts"}"""" + "\n",
                )
                prerequisiteId.startsWith("key-") -> {
                    append(
                        buildString {
                            append("#EXT-X-KEY:METHOD=${prerequisiteAsset?.keyMethod ?: "NONE"},URI=\"${prerequisiteAsset?.let { assetPath(sessionId, it) } ?: "/session/$sessionId/asset/$prerequisiteId.key"}\"")
                            prerequisiteAsset?.keyIv?.let { append(",IV=$it") }
                        } + "\n",
                    )
                }
            }
        }
        slots.forEach { slot ->
            val assetId = when (kind) {
                SessionAssetKind.VIDEO_SEGMENT -> slot.videoAssetId
                SessionAssetKind.AUDIO_SEGMENT -> slot.audioAssetIds.firstOrNull { it.startsWith("audio-$trackId-") || it == trackId }
                    ?: slot.audioAssetIds.firstOrNull()
                SessionAssetKind.SUBTITLE_SEGMENT -> slot.subtitleAssetIds.firstOrNull { it.startsWith("subtitle-$trackId-") || it == trackId }
                    ?: slot.subtitleAssetIds.firstOrNull()
                else -> null
            } ?: return@forEach
            append("#EXTINF:${(slot.endMs - slot.startMs) / 1000.0},\n")
            assetsById[assetId]?.let { append(assetPath(sessionId, it)) }
                ?: append("/session/$sessionId/asset/$assetId")
            append('\n')
        }
        append("#EXT-X-ENDLIST\n")
    }

    fun buildManifest(
        sessionId: String,
        slots: List<TimelineSlot>,
        assetsById: Map<String, SessionAsset> = emptyMap(),
    ): String = buildMediaPlaylist(
        sessionId = sessionId,
        trackId = "video-main",
        kind = SessionAssetKind.VIDEO_SEGMENT,
        slots = slots,
        assetsById = assetsById,
    )
}

private fun escape(value: String): String =
    value.replace("\"", "")

private fun assetPathSuffix(asset: SessionAsset): String =
    when (asset.kind) {
        SessionAssetKind.VIDEO_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".ts")
        SessionAssetKind.AUDIO_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".aac")
        SessionAssetKind.SUBTITLE_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".vtt")
        SessionAssetKind.INIT_SEGMENT -> inferSegmentExtension(asset.url, defaultExtension = ".ts")
        SessionAssetKind.KEY -> ".key"
        SessionAssetKind.MANIFEST -> ".m3u8"
    }

private fun inferSegmentExtension(url: String, defaultExtension: String): String {
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
    val fileName = path.substringAfterLast('/')
    val suffix = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
    return suffix ?: defaultExtension
}
