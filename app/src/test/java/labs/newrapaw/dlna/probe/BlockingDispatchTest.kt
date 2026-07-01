package labs.newrapaw.dlna.probe

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import labs.newrapaw.dlna.probe.platform.awaitCountDownOrThrow
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingDispatchTest {
    @Test
    fun awaitCountDownOrThrowReturnsWhenLatchCompletesInTime() {
        val latch = CountDownLatch(1)
        Thread {
            Thread.sleep(25L)
            latch.countDown()
        }.start()

        awaitCountDownOrThrow(
            completion = latch,
            operation = "prepare playback switch",
            timeoutMs = 250L,
        )
    }

    @Test
    fun awaitCountDownOrThrowFailsFastWhenLatchNeverCompletes() {
        val latch = CountDownLatch(1)
        val startedAt = System.nanoTime()

        val failure = runCatching {
            awaitCountDownOrThrow(
                completion = latch,
                operation = "prepare playback switch",
                timeoutMs = 50L,
            )
        }.exceptionOrNull()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(failure is TimeoutException)
        assertTrue(failure?.message.orEmpty().contains("prepare playback switch"))
        assertTrue(elapsedMs in 40L..500L)
    }
}
