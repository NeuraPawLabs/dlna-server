package labs.newrapaw.dlna.probe.ui

class MainActivityLogState {
    private val logs = UiLogBuffer(maxEntries = 1000)
    private val lock = Any()

    fun append(message: String): List<String> = synchronized(lock) {
        logs.append(message)
    }

    fun snapshot(): List<String> = synchronized(lock) {
        logs.snapshot()
    }
}
