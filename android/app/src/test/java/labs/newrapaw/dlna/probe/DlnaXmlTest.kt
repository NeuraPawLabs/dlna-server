package labs.newrapaw.dlna.probe

import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaXmlTest {
    @Test
    fun deviceDescriptionExposesMediaRendererServices() {
        val xml = buildDeviceDescriptionXml(
            DlnaDeviceConfig(
                baseUrl = "http://192.168.1.30:45111",
                deviceName = "Honor Screen",
                uuid = "12345678-1234-1234-1234-123456789abc",
            ),
        )

        assertTrue(xml.contains("urn:schemas-upnp-org:device:MediaRenderer:1"))
        assertTrue(xml.contains("<friendlyName>Honor Screen</friendlyName>"))
        assertTrue(xml.contains("<UDN>uuid:12345678-1234-1234-1234-123456789abc</UDN>"))
        assertTrue(xml.contains("<controlURL>/upnp/control/AVTransport</controlURL>"))
        assertTrue(xml.contains("<controlURL>/upnp/control/RenderingControl</controlURL>"))
        assertTrue(xml.contains("<controlURL>/upnp/control/ConnectionManager</controlURL>"))
    }

    @Test
    fun avTransportScpdListsPlaybackActions() {
        val xml = buildAvTransportScpdXml()

        assertTrue(xml.contains("<name>SetAVTransportURI</name>"))
        assertTrue(xml.contains("<name>Play</name>"))
        assertTrue(xml.contains("<name>Pause</name>"))
        assertTrue(xml.contains("<name>Stop</name>"))
        assertTrue(xml.contains("<name>GetCurrentTransportActions</name>"))
    }
}
