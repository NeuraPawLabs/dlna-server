package labs.newrapaw.dlna.probe.platform

class RendererLogState {
    private val logs = ArrayDeque<String>()
    private val lock = Any()

    fun append(message: String): List<String> = synchronized(lock) {
        logs.addLast(message)
        while (logs.size > MAX_ENTRIES) {
            logs.removeFirst()
        }
        logs.toList()
    }

    fun snapshot(): List<String> = synchronized(lock) {
        logs.toList()
    }

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}
