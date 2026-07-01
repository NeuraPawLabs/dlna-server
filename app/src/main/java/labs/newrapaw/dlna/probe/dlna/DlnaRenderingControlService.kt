package labs.newrapaw.dlna.probe.dlna

internal class DlnaRenderingControlService(
    private val state: DlnaRendererState,
    private val onStateChanged: (DlnaRendererSnapshot) -> Unit = {},
) {
    fun handleAction(actionName: String, args: Map<String, String>): Map<String, Any> =
        when (actionName) {
            "GetVolume" -> mapOf("CurrentVolume" to state.snapshot().volume)
            "SetVolume" -> {
                val currentVolume = state.snapshot().volume
                onStateChanged(
                    state.updateRendering(
                        volume = (args["DesiredVolume"]?.toIntOrNull() ?: currentVolume).coerceIn(0, 100),
                    ),
                )
                emptyMap()
            }
            "GetMute" -> mapOf("CurrentMute" to if (state.snapshot().muted) 1 else 0)
            "SetMute" -> {
                onStateChanged(
                    state.updateRendering(
                        muted = args["DesiredMute"] == "1" || args["DesiredMute"].equals("true", ignoreCase = true),
                    ),
                )
                emptyMap()
            }
            else -> throw IllegalArgumentException("Unsupported RenderingControl action $actionName")
        }
}
