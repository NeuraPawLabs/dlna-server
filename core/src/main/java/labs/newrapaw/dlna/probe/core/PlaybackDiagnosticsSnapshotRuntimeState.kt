package labs.newrapaw.dlna.probe.core

internal data class PlaybackDiagnosticsSnapshotRuntimeState(
    val snapshot: PlaybackDiagnosticsSnapshot = PlaybackDiagnosticsSnapshot.empty(),
    val cachedSnapshot: PlaybackDiagnosticsSnapshot? = null,
    val cachedSnapshotVersion: Long = -1L,
    val snapshotDirty: Boolean = true,
    val snapshotVersion: Long = 0L,
    val lastDerivedAtMs: Long? = null,
    val canThrottleDerivedSnapshot: Boolean = false,
)
