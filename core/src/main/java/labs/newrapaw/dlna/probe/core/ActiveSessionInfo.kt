package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus

data class ActiveSessionInfo(
    val sessionId: String,
    val status: PlaybackSessionStatus,
    val sourceUrl: String,
    val localManifestUrl: String,
    val slotCount: Int,
    val assetCount: Int,
    val prepared: Boolean,
    val pendingPrefetchAssetIds: List<String>,
)
