package labs.newrapaw.dlna.probe.core.session

data class SessionTrackManifest(
    val trackId: String,
    val name: String,
    val language: String?,
    val kind: SessionAssetKind,
    val playlistPath: String,
    val groupId: String? = null,
    val isDefault: Boolean = false,
)

data class SessionVideoVariantManifest(
    val trackId: String,
    val playlistPath: String,
    val bandwidth: Long?,
    val averageBandwidth: Long?,
    val resolution: String?,
    val codecs: String?,
    val audioGroupId: String? = null,
    val subtitleGroupId: String? = null,
)

class SessionLocalServer {
    fun masterManifestPath(sessionId: String): String = "/session/$sessionId/manifest.m3u8"

    fun videoPlaylistPath(sessionId: String): String = "/session/$sessionId/video.m3u8"

    fun videoPlaylistPath(sessionId: String, trackId: String): String = "/session/$sessionId/video/$trackId.m3u8"

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
        videoVariants: List<SessionVideoVariantManifest> = listOf(
            SessionVideoVariantManifest(
                trackId = "video-main",
                playlistPath = videoPlaylistPath,
                bandwidth = 1L,
                averageBandwidth = null,
                resolution = null,
                codecs = null,
            ),
        ),
        audioTracks: List<SessionTrackManifest> = emptyList(),
        subtitleTracks: List<SessionTrackManifest> = emptyList(),
    ): String = buildString {
        append("#EXTM3U\n")
        audioTracks.forEach { track ->
            append(
                """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="${escape(track.groupId ?: "audio")}",NAME="${escape(track.name)}",AUTOSELECT=YES,DEFAULT=${if (track.isDefault) "YES" else "NO"},URI="${track.playlistPath}"""" +
                    "\n",
            )
        }
        subtitleTracks.forEach { track ->
            append(
                """#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="${escape(track.groupId ?: "subs")}",NAME="${escape(track.name)}",AUTOSELECT=YES,DEFAULT=${if (track.isDefault) "YES" else "NO"},URI="${track.playlistPath}"""" +
                    "\n",
            )
        }
        videoVariants.forEach { variant ->
            append("#EXT-X-STREAM-INF:BANDWIDTH=${variant.bandwidth ?: 1L}")
            variant.averageBandwidth?.let { append(",AVERAGE-BANDWIDTH=$it") }
            variant.resolution?.takeIf { it.isNotBlank() }?.let { append(""",RESOLUTION="$it"""") }
            variant.codecs?.takeIf { it.isNotBlank() }?.let { append(""",CODECS="${escape(it)}"""") }
            variant.audioGroupId?.takeIf { it.isNotBlank() }?.let { append(""",AUDIO="${escape(it)}"""") }
            variant.subtitleGroupId?.takeIf { it.isNotBlank() }?.let { append(""",SUBTITLES="${escape(it)}"""") }
            append("\n")
            append(variant.playlistPath)
            append('\n')
        }
    }

    fun buildMediaPlaylist(
        sessionId: String,
        trackId: String,
        kind: SessionAssetKind,
        slots: List<TimelineSlot>,
        assetsById: Map<String, SessionAsset> = emptyMap(),
        includeEndList: Boolean = true,
    ): String = buildString {
        append("#EXTM3U\n")
        append("#EXT-X-VERSION:3\n")
        append("#EXT-X-TARGETDURATION:${targetDurationSeconds(slots)}\n")
        var previousMapAssetId: String? = null
        var previousKeyAssetId: String? = null
        slots.forEach { slot ->
            val prerequisiteIds = prerequisiteIdsForSlot(slot, kind, trackId)
            val mapAssetId = prerequisiteIds.firstOrNull { it.startsWith("init-") }
            val keyAssetId = prerequisiteIds.firstOrNull { it.startsWith("key-") }
            if (mapAssetId != previousMapAssetId && mapAssetId != null) {
                appendMapTag(sessionId, assetsById, mapAssetId)
            }
            if (keyAssetId != previousKeyAssetId) {
                appendKeyTag(sessionId, assetsById, keyAssetId)
            }
            previousMapAssetId = mapAssetId
            previousKeyAssetId = keyAssetId
            val assetId = when (kind) {
                SessionAssetKind.VIDEO_SEGMENT -> slot.videoAssetIdsByTrack[trackId]
                    ?: slot.videoAssetId.takeIf { slot.videoAssetIdsByTrack.isEmpty() }
                SessionAssetKind.AUDIO_SEGMENT -> slot.audioAssetIds.firstOrNull { it.startsWith("audio-$trackId-") || it == trackId }
                SessionAssetKind.SUBTITLE_SEGMENT -> slot.subtitleAssetIds.firstOrNull { it.startsWith("subtitle-$trackId-") || it == trackId }
                else -> null
            } ?: return@forEach
            if (kind == SessionAssetKind.VIDEO_SEGMENT && slot.videoDiscontinuityBefore) {
                append("#EXT-X-DISCONTINUITY\n")
            }
            append("#EXTINF:${(slot.endMs - slot.startMs) / 1000.0},\n")
            assetsById[assetId]?.let { append(assetPath(sessionId, it)) }
                ?: append("/session/$sessionId/asset/$assetId")
            append('\n')
        }
        if (includeEndList) {
            append("#EXT-X-ENDLIST\n")
        }
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
