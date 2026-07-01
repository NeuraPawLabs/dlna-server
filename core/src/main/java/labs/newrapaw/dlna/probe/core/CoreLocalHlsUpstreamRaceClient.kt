package labs.newrapaw.dlna.probe.core

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import labs.newrapaw.dlna.probe.core.session.SessionCallTracker
import okhttp3.Call

internal class CoreLocalHlsUpstreamRaceClient(
    private val upstreamRaceExecutor: ExecutorService,
    private val raceTimeoutMs: Long = DEFAULT_RACE_TIMEOUT_MS,
) {
    fun race(
        directCall: Call,
        proxyCall: Call,
        callTracker: SessionCallTracker? = null,
        executeCallMeasured: (Call, String) -> UpstreamFetchResult,
    ): UpstreamFetchResult {
        callTracker?.register(directCall)
        callTracker?.register(proxyCall)
        val completion = ExecutorCompletionService<UpstreamRaceResult>(upstreamRaceExecutor)
        val futures = listOf(
            completion.submit(Callable { executeRaceCall("direct", directCall, executeCallMeasured) }),
            completion.submit(Callable { executeRaceCall("proxy", proxyCall, executeCallMeasured) }),
        )
        val failures = mutableListOf<String>()
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(raceTimeoutMs)

        try {
            repeat(futures.size) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) {
                    cancelRaceLosers(futures, directCall, proxyCall)
                    throw UpstreamFetchException(504, "race timeout after ${raceTimeoutMs}ms")
                }
                val completed = completion.poll(remainingNanos, TimeUnit.NANOSECONDS)
                if (completed == null) {
                    cancelRaceLosers(futures, directCall, proxyCall)
                    throw UpstreamFetchException(504, "race timeout after ${raceTimeoutMs}ms")
                }
                val result = completed.getOrFailure()
                if (result.fetchResult != null) {
                    cancelRaceLosers(futures, directCall, proxyCall)
                    return result.fetchResult
                }
                failures.add("${result.source}: ${result.failure}")
            }
            throw UpstreamFetchException(502, failures.joinToString("; "))
        } finally {
            callTracker?.complete(directCall)
            callTracker?.complete(proxyCall)
        }
    }

    private fun executeRaceCall(
        source: String,
        call: Call,
        executeCallMeasured: (Call, String) -> UpstreamFetchResult,
    ): UpstreamRaceResult =
        runCatching {
            UpstreamRaceResult(
                source = source,
                fetchResult = executeCallMeasured(call, source),
                failure = null,
                elapsedMs = -1,
            )
        }.getOrElse {
            UpstreamRaceResult(
                source = source,
                fetchResult = null,
                failure = "${it::class.java.simpleName}: ${it.message}",
                elapsedMs = -1,
            )
        }

    private fun cancelRaceLosers(
        futures: List<Future<UpstreamRaceResult>>,
        directCall: Call,
        proxyCall: Call,
    ) {
        futures.forEach { it.cancel(true) }
        directCall.cancel()
        proxyCall.cancel()
    }

    private fun Future<UpstreamRaceResult>.getOrFailure(): UpstreamRaceResult =
        try {
            get()
        } catch (error: ExecutionException) {
            UpstreamRaceResult(
                source = "unknown",
                fetchResult = null,
                failure = "${error.cause?.javaClass?.simpleName ?: error::class.java.simpleName}: ${error.cause?.message ?: error.message}",
                elapsedMs = -1,
            )
        }

    private companion object {
        const val DEFAULT_RACE_TIMEOUT_MS = 15_000L
    }
}
