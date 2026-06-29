package labs.newrapaw.dlna.probe.desktop

enum class PlayerMode {
    AUTO,
    MPV,
    VLC,
    NONE,
}

data class DesktopCliArgs(
    val url: String,
    val playerMode: PlayerMode,
) {
    companion object {
        fun parse(args: List<String>): DesktopCliArgs {
            require(args.isNotEmpty() && args.first() == "play") { "Usage: play <m3u8-url> [--player=auto|mpv|vlc|none]" }
            val url = args.getOrNull(1) ?: error("Missing m3u8 url")
            val options = args.drop(2)
            val playerMode = options
                .firstOrNull { it.startsWith("--player=") }
                ?.substringAfter("=")
                ?.let(::parsePlayerMode)
                ?: PlayerMode.AUTO
            val unknownOptions = options.filterNot { it.startsWith("--player=") }
            require(unknownOptions.isEmpty()) { "Unknown option(s): ${unknownOptions.joinToString(" ")}" }
            return DesktopCliArgs(
                url = url,
                playerMode = playerMode,
            )
        }

        private fun parsePlayerMode(value: String): PlayerMode =
            when (value.lowercase()) {
                "auto" -> PlayerMode.AUTO
                "mpv" -> PlayerMode.MPV
                "vlc" -> PlayerMode.VLC
                "none" -> PlayerMode.NONE
                else -> error("Unknown player mode: $value")
            }
    }
}
