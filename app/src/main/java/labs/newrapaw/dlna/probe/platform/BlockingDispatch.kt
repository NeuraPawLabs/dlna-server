package labs.newrapaw.dlna.probe.platform

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object BlockingDispatch {
    fun awaitCountDownOrThrow(
        completion: CountDownLatch,
        operation: String,
        timeoutMs: Long = BLOCKING_DISPATCH_TIMEOUT_MS,
    ) {
        val completed = try {
            completion.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for $operation", error)
        }
        if (!completed) {
            throw TimeoutException("$operation timed out after ${timeoutMs}ms")
        }
    }
}

internal fun awaitCountDownOrThrow(
    completion: CountDownLatch,
    operation: String,
    timeoutMs: Long = BLOCKING_DISPATCH_TIMEOUT_MS,
): Unit = BlockingDispatch.awaitCountDownOrThrow(completion, operation, timeoutMs)

private const val BLOCKING_DISPATCH_TIMEOUT_MS = 5_000L
