package labs.newrapaw.dlna.probe.proxy

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.boundedExecutor
import labs.newrapaw.dlna.probe.dlna.DlnaEventNotifyRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class LocalHlsProxyEventNotifier(
    client: OkHttpClient,
    private val safeLog: (String) -> Unit,
    private val recordDeliveryResult: (DlnaEventNotifyRequest, Boolean) -> Unit,
) : Closeable {
    private val executor: ExecutorService = boundedExecutor(
        maxThreads = APP_EVENT_NOTIFY_MAX_THREADS,
        queueCapacity = APP_EVENT_NOTIFY_QUEUE_CAPACITY,
    )
    private val eventNotifyClient: OkHttpClient = buildEventNotifyClient(client)

    fun dispatch(request: DlnaEventNotifyRequest) {
        runCatching {
            executor.execute {
                val delivered = runCatching {
                    eventNotifyClient.newCall(
                        Request.Builder()
                            .url(request.callbackUrl)
                            .method(
                                "NOTIFY",
                                request.body.toByteArray(Charsets.UTF_8).toRequestBody(),
                            )
                            .apply {
                                request.headers.forEach { (name: String, value: String) -> addHeader(name, value) }
                            }
                            .build(),
                    ).execute().use { response ->
                        if (!response.isSuccessful) {
                            safeLog("[DLNA] Event notify failed: HTTP ${response.code}")
                        }
                        response.isSuccessful
                    }
                }.onFailure { error ->
                    safeLog("[DLNA] Event notify failed: ${error.message}")
                }.getOrDefault(false)
                recordDeliveryResult(request, delivered)
            }
        }.onFailure { error ->
            if (error is RejectedExecutionException) {
                safeLog("[DLNA] Event notify dropped: executor saturated")
            } else {
                safeLog("[DLNA] Event notify scheduling failed: ${error.message}")
            }
            recordDeliveryResult(request, false)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val APP_EVENT_NOTIFY_MAX_THREADS = 8
        const val APP_EVENT_NOTIFY_QUEUE_CAPACITY = 32
        const val APP_EVENT_NOTIFY_CALL_TIMEOUT_MS = 2_000L

        fun buildEventNotifyClient(client: OkHttpClient): OkHttpClient {
            val existingCallTimeoutMs = client.callTimeoutMillis.toLong()
            if (existingCallTimeoutMs in 1..APP_EVENT_NOTIFY_CALL_TIMEOUT_MS) {
                return client
            }
            return client.newBuilder()
                .callTimeout(APP_EVENT_NOTIFY_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
