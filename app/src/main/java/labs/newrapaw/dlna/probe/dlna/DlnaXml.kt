package labs.newrapaw.dlna.probe.dlna

data class DlnaDeviceConfig(
    val baseUrl: String,
    val deviceName: String,
    val uuid: String,
    val manufacturer: String = "NewraPaw Labs",
    val modelName: String = "PawCast",
)

fun buildDeviceDescriptionXml(config: DlnaDeviceConfig): String {
    val services = listOf(
        serviceXml(
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            serviceId = "urn:upnp-org:serviceId:AVTransport",
            controlPath = "/upnp/control/AVTransport",
            eventPath = "/upnp/event/AVTransport",
            scpdPath = "/upnp/service/AVTransport.xml",
        ),
        serviceXml(
            serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
            serviceId = "urn:upnp-org:serviceId:RenderingControl",
            controlPath = "/upnp/control/RenderingControl",
            eventPath = "/upnp/event/RenderingControl",
            scpdPath = "/upnp/service/RenderingControl.xml",
        ),
        serviceXml(
            serviceType = "urn:schemas-upnp-org:service:ConnectionManager:1",
            serviceId = "urn:upnp-org:serviceId:ConnectionManager",
            controlPath = "/upnp/control/ConnectionManager",
            eventPath = "/upnp/event/ConnectionManager",
            scpdPath = "/upnp/service/ConnectionManager.xml",
        ),
    ).joinToString("")

    return xmlDocument(
        """
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion>
            <major>1</major>
            <minor>0</minor>
          </specVersion>
          <URLBase>${escapeXml(config.baseUrl)}</URLBase>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>${escapeXml(config.deviceName)}</friendlyName>
            <manufacturer>${escapeXml(config.manufacturer)}</manufacturer>
            <modelName>${escapeXml(config.modelName)}</modelName>
            <UDN>uuid:${escapeXml(config.uuid)}</UDN>
            <serviceList>$services</serviceList>
          </device>
        </root>
        """.trimIndent(),
    )
}

fun buildAvTransportScpdXml(): String = buildScpdXml(
    listOf(
        "SetAVTransportURI",
        "GetMediaInfo",
        "GetTransportInfo",
        "GetPositionInfo",
        "GetCurrentTransportActions",
        "GetDeviceCapabilities",
        "GetTransportSettings",
        "SetNextAVTransportURI",
        "Play",
        "Pause",
        "Stop",
        "Seek",
    ),
)

fun buildRenderingControlScpdXml(): String = buildScpdXml(
    listOf("GetVolume", "SetVolume", "GetMute", "SetMute"),
)

fun buildConnectionManagerScpdXml(): String = buildScpdXml(
    listOf("GetProtocolInfo", "GetCurrentConnectionIDs", "GetCurrentConnectionInfo"),
)

private fun serviceXml(
    serviceType: String,
    serviceId: String,
    controlPath: String,
    eventPath: String,
    scpdPath: String,
): String = """
    <service>
      <serviceType>$serviceType</serviceType>
      <serviceId>$serviceId</serviceId>
      <SCPDURL>$scpdPath</SCPDURL>
      <controlURL>$controlPath</controlURL>
      <eventSubURL>$eventPath</eventSubURL>
    </service>
""".trimIndent()

private fun buildScpdXml(actionNames: List<String>): String {
    val actions = actionNames.joinToString("") { "<action><name>$it</name></action>" }
    return xmlDocument(
        """
        <scpd xmlns="urn:schemas-upnp-org:service-1-0">
          <specVersion>
            <major>1</major>
            <minor>0</minor>
          </specVersion>
          <actionList>$actions</actionList>
          <serviceStateTable>
            <stateVariable sendEvents="no">
              <name>A_ARG_TYPE_InstanceID</name>
              <dataType>ui4</dataType>
            </stateVariable>
          </serviceStateTable>
        </scpd>
        """.trimIndent(),
    )
}

private fun xmlDocument(body: String): String =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$body"

fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
