package labs.newrapaw.dlna.probe.core

import java.io.File
import java.util.concurrent.CancellationException
import labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridge
import labs.newrapaw.dlna.probe.core.session.SessionAsset
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import labs.newrapaw.dlna.probe.core.session.SessionPrefetchController
import labs.newrapaw.dlna.probe.core.session.PlaybackSession

internal data class UpstreamRaceResult(
    val source: String,
    val fetchResult: UpstreamFetchResult?,
    val failure: String?,
    val elapsedMs: Long,
)

internal data class UpstreamFetchResult(
    val source: String,
    val bytes: ByteArray,
    val firstByteMs: Long,
    val completeMs: Long,
)

internal data class ResponseWriteTiming(
    val firstByteMs: Long,
    val completeMs: Long,
)

internal data class PreparedSessionPlayback(
    val session: PlaybackSession,
    val masterManifest: String,
    val videoPlaylist: String,
    val primaryVideoTrackId: String,
    val videoPlaylists: Map<String, String>,
    val audioPlaylists: Map<String, String>,
    val subtitlePlaylists: Map<String, String>,
    val assetsById: Map<String, SessionAsset>,
    val assetRuntime: MutableMap<String, SessionAssetRuntime>,
    val telemetryBridge: PlaybackTelemetryBridge,
    val callTracker: SessionCallTracker,
    val prefetchController: SessionPrefetchController,
    val preparationFailure: UnsupportedSessionSourceException?,
)

internal class SessionAssetRuntime(
    var state: SessionAssetState = SessionAssetState.NOT_STARTED,
    var localFile: File? = null,
    var localSizeBytes: Long? = null,
    var lastError: String? = null,
    var lastElapsedMs: Long? = null,
    var lastSource: String? = null,
    var upstreamFirstByteMs: Long? = null,
    var upstreamCompleteMs: Long? = null,
    var diskWriteMs: Long? = null,
    var retryCount: Int = 0,
    val lock: Object = Object(),
)

internal class UpstreamFetchException(
    val statusCode: Int,
    val failure: String,
) : RuntimeException(failure)

internal class UnsupportedSessionSourceException(
    val statusCode: Int,
    override val message: String,
) : RuntimeException(message)

internal class SessionCancelledException(
    override val message: String,
) : CancellationException(message)
