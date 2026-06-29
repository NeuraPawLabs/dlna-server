package labs.newrapaw.dlna.probe

data class DlnaControlResponse(
    val statusCode: Int,
    val contentType: String = "text/xml; charset=utf-8",
    val body: String,
)

class DlnaRendererController(
    private val log: (String) -> Unit,
    private val onPlayRequested: (String) -> Unit,
    private val onStopRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
) {
    private var currentUri: String = ""
    private var currentUriMetadata: String = ""
    private var transportState: String = "STOPPED"
    private var transportStatus: String = "OK"
    private var relativeTimePosition: String = "00:00:00"
    private var volume: Int = 50
    private var muted: Boolean = false

    fun handleControlRequest(serviceName: String, soapActionHeader: String?, body: String): DlnaControlResponse {
        return runCatching {
            val action = parseSoapAction(soapActionHeader, body)
            if (shouldLogAction(serviceName, action.actionName)) {
                log("[DLNA] Action: $serviceName.${action.actionName}")
            }
            val values = when (serviceName) {
                "AVTransport" -> handleAvTransport(action.actionName, action.args)
                "RenderingControl" -> handleRenderingControl(action.actionName, action.args)
                "ConnectionManager" -> handleConnectionManager(action.actionName)
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

    private fun handleAvTransport(actionName: String, args: Map<String, String>): Map<String, Any> {
        return when (actionName) {
            "SetAVTransportURI" -> {
                currentUri = args["CurrentURI"].orEmpty()
                currentUriMetadata = args["CurrentURIMetaData"].orEmpty()
                transportState = "STOPPED"
                transportStatus = "OK"
                relativeTimePosition = "00:00:00"
                log("[DLNA] Set URI: $currentUri")
                emptyMap()
            }
            "SetNextAVTransportURI" -> emptyMap()
            "Play" -> {
                if (currentUri.isBlank()) throw IllegalStateException("No current URI")
                onPlayRequested(currentUri)
                transportState = "PLAYING"
                transportStatus = "OK"
                log("[DLNA] Play: $currentUri")
                emptyMap()
            }
            "Pause" -> {
                onPauseRequested()
                transportState = "PAUSED_PLAYBACK"
                transportStatus = "OK"
                log("[DLNA] Pause")
                emptyMap()
            }
            "Stop" -> {
                onStopRequested()
                transportState = "STOPPED"
                transportStatus = "OK"
                relativeTimePosition = "00:00:00"
                log("[DLNA] Stop")
                emptyMap()
            }
            "Seek" -> {
                relativeTimePosition = args["Target"].orEmpty().ifBlank { "00:00:00" }
                log("[DLNA] Seek: $relativeTimePosition")
                emptyMap()
            }
            "GetTransportInfo" -> mapOf(
                "CurrentTransportState" to transportState,
                "CurrentTransportStatus" to transportStatus,
                "CurrentSpeed" to "1",
            )
            "GetMediaInfo" -> mapOf(
                "NrTracks" to 1,
                "MediaDuration" to "00:00:00",
                "CurrentURI" to currentUri,
                "CurrentURIMetaData" to currentUriMetadata,
                "NextURI" to "",
                "NextURIMetaData" to "",
                "PlayMedium" to "NETWORK",
                "RecordMedium" to "NOT_IMPLEMENTED",
                "WriteStatus" to "NOT_IMPLEMENTED",
            )
            "GetPositionInfo" -> mapOf(
                "Track" to 1,
                "TrackDuration" to "00:00:00",
                "TrackMetaData" to currentUriMetadata,
                "TrackURI" to currentUri,
                "RelTime" to relativeTimePosition,
                "AbsTime" to relativeTimePosition,
                "RelCount" to 0,
                "AbsCount" to 0,
            )
            "GetCurrentTransportActions" -> mapOf("Actions" to "Play,Stop,Pause,Seek")
            "GetDeviceCapabilities" -> mapOf(
                "PlayMedia" to "NETWORK",
                "RecMedia" to "NOT_IMPLEMENTED",
                "RecQualityModes" to "NOT_IMPLEMENTED",
            )
            "GetTransportSettings" -> mapOf(
                "PlayMode" to "NORMAL",
                "RecQualityMode" to "NOT_IMPLEMENTED",
            )
            else -> throw IllegalArgumentException("Unsupported AVTransport action $actionName")
        }
    }

    private fun handleRenderingControl(actionName: String, args: Map<String, String>): Map<String, Any> {
        return when (actionName) {
            "GetVolume" -> mapOf("CurrentVolume" to volume)
            "SetVolume" -> {
                volume = (args["DesiredVolume"]?.toIntOrNull() ?: volume).coerceIn(0, 100)
                emptyMap()
            }
            "GetMute" -> mapOf("CurrentMute" to if (muted) 1 else 0)
            "SetMute" -> {
                muted = args["DesiredMute"] == "1" || args["DesiredMute"].equals("true", ignoreCase = true)
                emptyMap()
            }
            else -> throw IllegalArgumentException("Unsupported RenderingControl action $actionName")
        }
    }

    private fun handleConnectionManager(actionName: String): Map<String, Any> {
        return when (actionName) {
            "GetProtocolInfo" -> mapOf(
                "Source" to "",
                "Sink" to listOf(
                    "http-get:*:video/mp4:*",
                    "http-get:*:application/vnd.apple.mpegurl:*",
                    "http-get:*:application/x-mpegURL:*",
                    "http-get:*:video/mp2t:*",
                    "http-get:*:audio/mpeg:*",
                    "http-get:*:image/jpeg:*",
                ).joinToString(","),
            )
            "GetCurrentConnectionIDs" -> mapOf("ConnectionIDs" to "0")
            "GetCurrentConnectionInfo" -> mapOf(
                "RcsID" to 0,
                "AVTransportID" to 0,
                "ProtocolInfo" to "",
                "PeerConnectionManager" to "",
                "PeerConnectionID" to -1,
                "Direction" to "Input",
                "Status" to "OK",
            )
            else -> throw IllegalArgumentException("Unsupported ConnectionManager action $actionName")
        }
    }

    private fun shouldLogAction(serviceName: String, actionName: String): Boolean =
        when (serviceName to actionName) {
            "AVTransport" to "GetPositionInfo",
            "RenderingControl" to "GetVolume" -> false
            else -> true
        }
}

private data class ParsedSoapAction(
    val serviceType: String?,
    val actionName: String,
    val args: Map<String, String>,
)

private fun parseSoapAction(soapActionHeader: String?, body: String): ParsedSoapAction {
    val headerParts = soapActionHeader
        ?.trim()
        ?.trim('"')
        ?.split("#", limit = 2)
        ?.takeIf { it.size == 2 }
    val bodyAction = findSoapBodyAction(body)
    return ParsedSoapAction(
        serviceType = headerParts?.getOrNull(0),
        actionName = headerParts?.getOrNull(1) ?: bodyAction.first,
        args = parseSoapArgs(bodyAction.second),
    )
}

private fun findSoapBodyAction(body: String): Pair<String, String> {
    val bodyXml = Regex("""<[^:>]*:?Body\b[^>]*>([\s\S]*?)</[^:>]*:?Body>""", RegexOption.IGNORE_CASE)
        .find(body)
        ?.groupValues
        ?.get(1)
        ?: body
    val action = Regex("""<(?:(?:[\w.-]+):)?([\w.-]+)\b[^>]*>([\s\S]*?)</(?:(?:[\w.-]+):)?\1>""", RegexOption.IGNORE_CASE)
        .find(bodyXml)
        ?: throw IllegalArgumentException("Could not find SOAP action")
    return action.groupValues[1] to action.groupValues[2]
}

private fun parseSoapArgs(innerXml: String): Map<String, String> {
    val args = mutableMapOf<String, String>()
    val pattern = Regex("""<(?:(?:[\w.-]+):)?([\w.-]+)\b[^>]*>([\s\S]*?)</(?:(?:[\w.-]+):)?\1>""")
    for (match in pattern.findAll(innerXml)) {
        args[match.groupValues[1]] = unescapeXml(match.groupValues[2].trim())
    }
    return args
}

private fun buildSoapResponse(serviceType: String, actionName: String, values: Map<String, Any>): String {
    val entries = values.entries.joinToString("") { (name, value) ->
        "<$name>${escapeXml(value.toString())}</$name>"
    }
    return soapEnvelope(
        """<u:${actionName}Response xmlns:u="${escapeXml(serviceType)}">$entries</u:${actionName}Response>""",
    )
}

private fun buildSoapFault(errorCode: Int, description: String): String = soapEnvelope(
    """
    <s:Fault>
      <faultcode>s:Client</faultcode>
      <faultstring>UPnPError</faultstring>
      <detail>
        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
          <errorCode>$errorCode</errorCode>
          <errorDescription>${escapeXml(description)}</errorDescription>
        </UPnPError>
      </detail>
    </s:Fault>
    """.trimIndent(),
)

private fun soapEnvelope(body: String): String =
    """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>$body</s:Body></s:Envelope>"""

private fun serviceTypeFor(serviceName: String): String = when (serviceName) {
    "AVTransport" -> "urn:schemas-upnp-org:service:AVTransport:1"
    "RenderingControl" -> "urn:schemas-upnp-org:service:RenderingControl:1"
    "ConnectionManager" -> "urn:schemas-upnp-org:service:ConnectionManager:1"
    else -> "urn:schemas-upnp-org:service:$serviceName:1"
}

private fun unescapeXml(value: String): String =
    value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
