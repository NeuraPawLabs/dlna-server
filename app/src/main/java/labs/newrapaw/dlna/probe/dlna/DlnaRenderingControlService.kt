package labs.newrapaw.dlna.probe.dlna

internal class DlnaRenderingControlService(
    private val state: DlnaRendererState,
) {
    fun handleAction(actionName: String, args: Map<String, String>): Map<String, Any> =
        when (actionName) {
            "GetVolume" -> mapOf("CurrentVolume" to state.volume)
            "SetVolume" -> {
                state.volume = (args["DesiredVolume"]?.toIntOrNull() ?: state.volume).coerceIn(0, 100)
                emptyMap()
            }
            "GetMute" -> mapOf("CurrentMute" to if (state.muted) 1 else 0)
            "SetMute" -> {
                state.muted = args["DesiredMute"] == "1" || args["DesiredMute"].equals("true", ignoreCase = true)
                emptyMap()
            }
            else -> throw IllegalArgumentException("Unsupported RenderingControl action $actionName")
        }
}
