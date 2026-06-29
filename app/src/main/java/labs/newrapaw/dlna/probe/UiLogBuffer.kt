package labs.newrapaw.dlna.probe

class UiLogBuffer(
    private val maxEntries: Int,
) {
    private val entries = ArrayDeque<String>()

    fun append(message: String): List<String> {
        entries.addLast(message)
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
        return snapshot()
    }

    fun snapshot(): List<String> = entries.toList()
}
