package labs.newrapaw.dlna.probe.core.session

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call

class SessionCallTracker {
    private val cancelled = AtomicBoolean(false)
    private val lock = Object()
    private val activeCalls = linkedSetOf<Call>()

    fun register(call: Call) {
        if (cancelled.get()) {
            call.cancel()
            throw CancellationException("session already cancelled")
        }
        synchronized(lock) {
            if (cancelled.get()) {
                call.cancel()
                throw CancellationException("session already cancelled")
            }
            activeCalls += call
        }
    }

    fun complete(call: Call) {
        synchronized(lock) {
            activeCalls.remove(call)
        }
    }

    fun cancel() {
        cancelled.set(true)
        val calls = synchronized(lock) {
            activeCalls.toList().also { activeCalls.clear() }
        }
        calls.forEach { it.cancel() }
    }

    fun isCancelled(): Boolean = cancelled.get()
}
