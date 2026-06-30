package labs.newrapaw.dlna.probe.dlna

internal data class ParsedSoapAction(
    val serviceType: String?,
    val actionName: String,
    val args: Map<String, String>,
)

internal fun parseSoapAction(soapActionHeader: String?, body: String): ParsedSoapAction {
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

internal fun buildSoapResponse(serviceType: String, actionName: String, values: Map<String, Any>): String {
    val entries = values.entries.joinToString("") { (name, value) ->
        "<$name>${escapeXml(value.toString())}</$name>"
    }
    return soapEnvelope(
        """<u:${actionName}Response xmlns:u="${escapeXml(serviceType)}">$entries</u:${actionName}Response>""",
    )
}

internal fun buildSoapFault(errorCode: Int, description: String): String = soapEnvelope(
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

internal fun serviceTypeFor(serviceName: String): String = when (serviceName) {
    "AVTransport" -> "urn:schemas-upnp-org:service:AVTransport:1"
    "RenderingControl" -> "urn:schemas-upnp-org:service:RenderingControl:1"
    "ConnectionManager" -> "urn:schemas-upnp-org:service:ConnectionManager:1"
    else -> "urn:schemas-upnp-org:service:$serviceName:1"
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

private fun soapEnvelope(body: String): String =
    """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>$body</s:Body></s:Envelope>"""

private fun unescapeXml(value: String): String =
    value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
