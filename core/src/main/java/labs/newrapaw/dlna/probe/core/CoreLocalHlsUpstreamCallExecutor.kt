package labs.newrapaw.dlna.probe.core

import java.net.Proxy
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

internal class CoreLocalHlsUpstreamCallExecutor(
    private val client: OkHttpClient,
) {
    fun openStreamingCall(
        upstreamUrl: String,
        proxy: ProxyConfig?,
    ): Pair<String, Call> =
        if (proxy == null) {
            "direct" to newDirectCall(upstreamUrl)
        } else {
            "proxy" to newProxyCall(upstreamUrl, proxy)
        }

    fun executeUpstreamCall(
        upstreamUrl: String,
        proxy: ProxyConfig?,
        source: String,
        callTracker: SessionCallTracker? = null,
    ): UpstreamFetchResult {
        val call = if (proxy == null) newDirectCall(upstreamUrl) else newProxyCall(upstreamUrl, proxy)
        callTracker?.register(call)
        val startedAt = System.nanoTime()
        return runCatching { executeCallMeasured(call, source) }
            .getOrElse { error ->
                if (error is UpstreamFetchException) throw error
                throw UpstreamFetchException(502, "$source: ${error::class.java.simpleName}: ${error.message}")
            }
            .also {
                callTracker?.complete(call)
            }
    }

    fun newDirectCall(upstreamUrl: String): Call =
        directClient().newCall(Request.Builder().url(upstreamUrl).build())

    fun newProxyCall(
        upstreamUrl: String,
        proxy: ProxyConfig,
    ): Call = proxyClient(proxy).newCall(Request.Builder().url(upstreamUrl).build())

    fun executeCallMeasured(call: Call, source: String): UpstreamFetchResult {
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

    private fun directClient(): OkHttpClient =
        client.newBuilder()
            .proxy(Proxy.NO_PROXY)
            .callTimeout(DEFAULT_UPSTREAM_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    private fun proxyClient(proxy: ProxyConfig): OkHttpClient =
        client.newBuilder()
            .proxy(proxy.toJavaProxy())
            .callTimeout(DEFAULT_UPSTREAM_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

    private fun nanosToMillis(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

    private companion object {
        const val DEFAULT_UPSTREAM_CALL_TIMEOUT_MS = 15_000L
    }
}
