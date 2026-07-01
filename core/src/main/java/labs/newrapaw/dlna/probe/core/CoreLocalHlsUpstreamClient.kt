package labs.newrapaw.dlna.probe.core

import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import okhttp3.Call
import okhttp3.OkHttpClient

internal class CoreLocalHlsUpstreamClient(
    private val client: OkHttpClient,
    private val proxySettingsStore: ProxySettingsStore,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val upstreamRaceClient: CoreLocalHlsUpstreamRaceClient,
    private val refreshDiagnosticsSnapshot: () -> Unit,
    private val log: (String) -> Unit,
) {
    private val callExecutor = CoreLocalHlsUpstreamCallExecutor(client)

    fun openStreamingCall(upstreamUrl: String): Pair<String, Call> {
        val proxy = proxySettingsStore.load().selectedProxy()
        return callExecutor.openStreamingCall(upstreamUrl, proxy)
    }

    fun fetchUpstreamBytes(upstreamUrl: String): ByteArray =
        fetchUpstreamBytesMeasured(upstreamUrl).bytes

    fun fetchSegmentBytesMeasured(
        upstreamUrl: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val result = fetchUpstreamBytesMeasured(upstreamUrl, callTracker)
        return if (looksLikeTransportStream(upstreamUrl)) {
            result.copy(bytes = stripPngWrapperFromSegment(result.bytes))
        } else {
            result
        }
    }

    fun fetchUpstreamBytesMeasured(
        upstreamUrl: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val settings = proxySettingsStore.load()
        diagnosticsState.setUpstreamSettings(settings)
        val proxy = settings.selectedProxy()
        return when {
            proxy == null -> executeMeasuredFetch(upstreamUrl, proxy = null, source = "direct", callTracker = callTracker)
            settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY -> executeRaceUpstreamCall(upstreamUrl, proxy, callTracker)
            else -> {
                log("Using proxy: ${proxy.displayUrl()}")
                executeMeasuredFetch(upstreamUrl, proxy = proxy, source = "proxy", callTracker = callTracker)
            }
        }
    }

    private fun executeMeasuredFetch(
        upstreamUrl: String,
        proxy: ProxyConfig?,
        source: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult =
        runCatching {
            callExecutor.executeUpstreamCall(
                upstreamUrl = upstreamUrl,
                proxy = proxy,
                source = source,
                callTracker = callTracker,
            )
        }
            .onSuccess { result ->
                diagnosticsState.onSegmentResult(upstreamUrl, source, result.completeMs, success = true)
                refreshDiagnosticsSnapshot()
            }
            .getOrElse { error ->
                val failure = when (error) {
                    is UpstreamFetchException -> error.failure
                    else -> "$source: ${error::class.java.simpleName}: ${error.message}"
                }
                diagnosticsState.onSegmentResult(
                    url = upstreamUrl,
                    source = source,
                    elapsedMs = -1,
                    success = false,
                    fallbackReason = failure,
                )
                refreshDiagnosticsSnapshot()
                if (error is UpstreamFetchException) throw error
                throw UpstreamFetchException(502, failure)
            }

    private fun executeRaceUpstreamCall(
        upstreamUrl: String,
        proxy: ProxyConfig,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val directCall = callExecutor.newDirectCall(upstreamUrl)
        val proxyCall = callExecutor.newProxyCall(upstreamUrl, proxy)
        return runCatching {
            upstreamRaceClient.race(
                directCall = directCall,
                proxyCall = proxyCall,
                callTracker = callTracker,
                executeCallMeasured = callExecutor::executeCallMeasured,
            )
        }.onSuccess { result ->
            diagnosticsState.onSegmentResult(upstreamUrl, result.source, result.completeMs, success = true)
            refreshDiagnosticsSnapshot()
        }.getOrElse { error ->
            val failure = when (error) {
                is UpstreamFetchException -> error.failure
                else -> "${error::class.java.simpleName}: ${error.message}"
            }
            diagnosticsState.onSegmentResult(
                url = upstreamUrl,
                source = "race",
                elapsedMs = -1,
                success = false,
                fallbackReason = failure,
            )
            refreshDiagnosticsSnapshot()
            if (error is UpstreamFetchException) throw error
            throw UpstreamFetchException(502, failure)
        }
    }
}
