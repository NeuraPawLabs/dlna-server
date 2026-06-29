package labs.newrapaw.dlna.probe.core.session

enum class PlaybackSessionStatus {
    PREPARING,
    PRIMING,
    READY,
    PLAYING,
    DEGRADED,
    STALLED,
    COMPLETED,
    FAILED,
    CLOSED,
}

data class PlaybackSession(
    val sessionId: String,
    val sourceUrl: String,
    val entryManifestUrl: String,
    val localRootDir: String,
    val createdAtMs: Long,
    val status: PlaybackSessionStatus,
    val timeline: SessionTimeline,
) {
    companion object {
        fun create(
            sessionId: String,
            sourceUrl: String,
            entryManifestUrl: String,
            localRootDir: String,
        ): PlaybackSession = PlaybackSession(
            sessionId = sessionId,
            sourceUrl = sourceUrl,
            entryManifestUrl = entryManifestUrl,
            localRootDir = localRootDir,
            createdAtMs = System.currentTimeMillis(),
            status = PlaybackSessionStatus.PREPARING,
            timeline = SessionTimeline(),
        )
    }
}
