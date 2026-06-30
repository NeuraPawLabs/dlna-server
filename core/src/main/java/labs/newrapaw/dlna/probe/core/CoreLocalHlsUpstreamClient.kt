package labs.newrapaw.dlna.probe.core

import java.net.Proxy
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

internal class CoreLocalHlsUpstreamClient(
    private val client: OkHttpClient,
    private val proxySettingsStore: ProxySettingsStore,
    private val diagnosticsState: PlaybackDiagnosticsState,
    private val upstreamRaceClient: CoreLocalHlsUpstreamRaceClient,
    private val refreshDiagnosticsSnapshot: () -> Unit,
    private val log: (String) -> Unit,
) {
    fun openStreamingCall(upstreamUrl: String): Pair<String, Call> {
        val proxy = proxySettingsStore.load().selectedProxy()
        val source = if (proxy == null) "direct" else "proxy"
        val client = if (proxy == null) directClient() else proxyClient(proxy)
        return source to client.newCall(Request.Builder().url(upstreamUrl).build())
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
            proxy == null -> executeUpstreamCall(upstreamUrl, directClient(), "direct", callTracker)
            settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY -> executeRaceUpstreamCall(upstreamUrl, proxy, callTracker)
            else -> {
                log("Using proxy: ${proxy.displayUrl()}")
                executeUpstreamCall(upstreamUrl, proxyClient(proxy), "proxy", callTracker)
            }
        }
    }

    private fun executeUpstreamCall(
        upstreamUrl: String,
        client: OkHttpClient,
        source: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val call = client.newCall(Request.Builder().url(upstreamUrl).build())
        callTracker?.register(call)
        val startedAt = System.nanoTime()
        return runCatching { executeCallMeasured(call, source) }
            .onSuccess { result ->
                diagnosticsState.onSegmentResult(upstreamUrl, source, result.completeMs, success = true)
                refreshDiagnosticsSnapshot()
            }
            .getOrElse { error ->
                val elapsedMs = nanosToMillis(startedAt)
                diagnosticsState.onSegmentResult(
                    url = upstreamUrl,
                    source = source,
                    elapsedMs = elapsedMs,
                    success = false,
                    fallbackReason = "${error::class.java.simpleName}: ${error.message}",
                )
                refreshDiagnosticsSnapshot()
                if (error is UpstreamFetchException) throw error
                throw UpstreamFetchException(502, "$source: ${error::class.java.simpleName}: ${error.message}")
            }
            .also {
                callTracker?.complete(call)
            }
    }

    private fun executeCallMeasured(call: Call, source: String): UpstreamFetchResult {
        val startedAt = System.nanoTime()
        val response = call.execute()
        response.use {
            if (!it.isSuccessful) {
                val failure = formatUpstreamFailure(
                    statusCode = it.code,
                    statusMessage = it.message,
                    body = it.body?.string().orEmpty(),
                )
                throw UpstreamFetchException(it.code, failure)
            }
            val stream = it.body?.byteStream()
            if (stream == null) {
                return UpstreamFetchResult(
                    source = source,
                    bytes = ByteArray(0),
                    firstByteMs = 0L,
                    completeMs = nanosToMillis(startedAt),
                )
            }
            val buffer = ByteArray(8 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var firstByteAt: Long? = null
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (firstByteAt == null) {
                    firstByteAt = System.nanoTime()
                }
                output.write(buffer, 0, count)
            }
            return UpstreamFetchResult(
                source = source,
                bytes = output.toByteArray(),
                firstByteMs = firstByteAt?.let { TimeUnit.NANOSECONDS.toMillis(it - startedAt) } ?: 0L,
                completeMs = nanosToMillis(startedAt),
            )
        }
    }

    private fun executeRaceUpstreamCall(
        upstreamUrl: String,
        proxy: ProxyConfig,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val directCall = directClient().newCall(Request.Builder().url(upstreamUrl).build())
        val proxyCall = proxyClient(proxy).newCall(Request.Builder().url(upstreamUrl).build())
        return runCatching {
            upstreamRaceClient.race(
                directCall = directCall,
                proxyCall = proxyCall,
                callTracker = callTracker,
                executeCallMeasured = ::executeCallMeasured,
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

    private fun directClient(): OkHttpClient =
        client.newBuilder()
            .proxy(Proxy.NO_PROXY)
            .build()

    private fun proxyClient(proxy: ProxyConfig): OkHttpClient =
        client.newBuilder()
            .proxy(proxy.toJavaProxy())
            .build()

    private fun nanosToMillis(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
}
