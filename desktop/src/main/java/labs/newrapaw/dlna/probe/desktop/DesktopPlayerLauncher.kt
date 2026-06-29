package labs.newrapaw.dlna.probe.desktop

class DesktopPlayerLauncher(
    private val commandExists: (String) -> Boolean = { command ->
        runCatching {
            ProcessBuilder("sh", "-lc", "command -v $command >/dev/null 2>&1").start().waitFor() == 0
        }.getOrDefault(false)
    },
    private val spawn: (List<String>) -> List<String> = { command ->
        ProcessBuilder(command).inheritIO().start()
        command
    },
) {
    fun launch(mode: PlayerMode, url: String): List<String>? =
        when (mode) {
            PlayerMode.NONE -> null
            PlayerMode.MPV -> launchIfAvailable("mpv", url)
            PlayerMode.VLC -> launchIfAvailable("vlc", url)
            PlayerMode.AUTO -> launchIfAvailable("mpv", url) ?: launchIfAvailable("vlc", url)
        }

    private fun launchIfAvailable(command: String, url: String): List<String>? {
        if (!commandExists(command)) return null
        return spawn(listOf(command, url))
    }
}
