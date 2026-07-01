package labs.newrapaw.dlna.probe.dlna

data class DlnaControlResponse(
    val statusCode: Int,
    val contentType: String = "text/xml; charset=utf-8",
    val body: String,
)

internal class DlnaRendererController(
    private val log: (String) -> Unit,
    private val onPlayRequested: (String) -> Unit,
    private val onStopRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onSeekRequested: (Long) -> Unit = {},
    private val onAvTransportStateChanged: (DlnaRendererSnapshot) -> Unit = {},
    private val onRenderingControlStateChanged: (DlnaRendererSnapshot) -> Unit = {},
) {
    private val state = DlnaRendererState()
    private val avTransportService = DlnaAvTransportService(
        state = state,
        log = log,
        onPlayRequested = onPlayRequested,
        onStopRequested = onStopRequested,
        onPauseRequested = onPauseRequested,
        onSeekRequested = onSeekRequested,
        onStateChanged = onAvTransportStateChanged,
    )
    private val renderingControlService = DlnaRenderingControlService(
        state = state,
        onStateChanged = onRenderingControlStateChanged,
    )
    private val connectionManagerService = DlnaConnectionManagerService()

    fun handleControlRequest(serviceName: String, soapActionHeader: String?, body: String): DlnaControlResponse {
        return runCatching {
            val action = parseSoapAction(soapActionHeader, body)
            if (shouldLogAction(serviceName, action.actionName)) {
                log("[DLNA] Action: $serviceName.${action.actionName}")
            }
            val values = when (serviceName) {
                "AVTransport" -> avTransportService.handleAction(action.actionName, action.args)
                "RenderingControl" -> renderingControlService.handleAction(action.actionName, action.args)
                "ConnectionManager" -> connectionManagerService.handleAction(action.actionName)
                else -> throw IllegalArgumentException("Unsupported service $serviceName")
            }
            DlnaControlResponse(
                statusCode = 200,
                body = buildSoapResponse(serviceTypeFor(serviceName), action.actionName, values),
            )
        }.getOrElse {
            DlnaControlResponse(
                statusCode = 500,
                body = buildSoapFault(701, it.message ?: "Action failed"),
            )
        }
    }

    fun syncPlayerState(
        transportState: String,
        transportStatus: String = "OK",
        positionMs: Long? = null,
        durationMs: Long? = null,
    ) {
        onAvTransportStateChanged(
            state.updateTransport(
                transportState = transportState,
                transportStatus = transportStatus,
                relativeTimePosition = positionMs?.let(::formatDlnaTime),
                mediaDurationMs = durationMs,
            ),
        )
    }

    fun syncPlayerPosition(positionMs: Long) {
        onAvTransportStateChanged(
            state.updatePosition(formatDlnaTime(positionMs)),
        )
    }

    fun snapshot(): DlnaRendererSnapshot = state.snapshot()

    private fun shouldLogAction(serviceName: String, actionName: String): Boolean =
        when (serviceName to actionName) {
            "AVTransport" to "GetPositionInfo",
            "RenderingControl" to "GetVolume" -> false
            else -> true
        }
}
