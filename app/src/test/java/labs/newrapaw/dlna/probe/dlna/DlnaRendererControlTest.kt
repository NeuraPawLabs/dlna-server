package labs.newrapaw.dlna.probe.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaRendererControlTest {
    @Test
    fun setUriThenPlayInvokesPlaybackCallback() {
        val played = mutableListOf<String>()
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = { played.add(it) },
            onStopRequested = {},
            onPauseRequested = {},
        )
        val url = "https://cdn.example/video.m3u8?token=abc"

        val setUri = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to ""),
        )
        val play = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play", "Speed" to "1"),
        )

        assertEquals(200, setUri.statusCode)
        assertEquals(200, play.statusCode)
        assertEquals(listOf(url), played)
        assertTrue(play.body.contains("PlayResponse"))
    }

    @Test
    fun getTransportInfoReportsPlayingAfterPlay() {
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = {},
            onStopRequested = {},
            onPauseRequested = {},
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to "https://example/video.mp4"),
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play", "Speed" to "1"),
        )

        val response = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"",
            body = soapAction("GetTransportInfo"),
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("<CurrentTransportState>PLAYING</CurrentTransportState>"))
        assertTrue(response.body.contains("<CurrentTransportStatus>OK</CurrentTransportStatus>"))
    }

    @Test
    fun getCurrentTransportActionsReportsSupportedActions() {
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = {},
            onStopRequested = {},
            onPauseRequested = {},
        )

        val response = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetCurrentTransportActions\"",
            body = soapAction("GetCurrentTransportActions"),
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("<Actions>Play,Stop,Pause,Seek</Actions>"))
    }

    @Test
    fun pauseAndStopInvokeControlCallbacksAndUpdateState() {
        var pauses = 0
        var stops = 0
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = {},
            onStopRequested = { stops += 1 },
            onPauseRequested = { pauses += 1 },
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to "https://example/video.mp4"),
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play"),
        )

        val pause = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"",
            body = soapAction("Pause"),
        )
        val pausedInfo = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"",
            body = soapAction("GetTransportInfo"),
        )
        val stop = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"",
            body = soapAction("Stop"),
        )
        val stoppedInfo = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"",
            body = soapAction("GetTransportInfo"),
        )

        assertEquals(200, pause.statusCode)
        assertEquals(200, stop.statusCode)
        assertEquals(1, pauses)
        assertEquals(1, stops)
        assertTrue(pausedInfo.body.contains("<CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState>"))
        assertTrue(stoppedInfo.body.contains("<CurrentTransportState>STOPPED</CurrentTransportState>"))
    }

    @Test
    fun secondSetUriOverridesPreviousPlaybackTarget() {
        val played = mutableListOf<String>()
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = { played.add(it) },
            onStopRequested = {},
            onPauseRequested = {},
        )

        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to "https://example/first.m3u8"),
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play"),
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to "https://example/second.m3u8"),
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play"),
        )

        assertEquals(listOf("https://example/first.m3u8", "https://example/second.m3u8"), played)
    }

    @Test
    fun playbackCallbackFailureReturnsSoapFaultInsteadOfThrowing() {
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = { error("player exploded") },
            onStopRequested = {},
            onPauseRequested = {},
        )
        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            body = soapAction("SetAVTransportURI", "CurrentURI" to "https://example/video.mp4"),
        )

        val response = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            body = soapAction("Play"),
        )

        assertEquals(500, response.statusCode)
        assertTrue(response.body.contains("player exploded"))
    }

    @Test
    fun logsMeaningfulSoapActions() {
        val logs = mutableListOf<String>()
        val renderer = DlnaRendererController(
            log = { logs.add(it) },
            onPlayRequested = {},
            onStopRequested = {},
            onPauseRequested = {},
        )

        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo\"",
            body = soapAction("GetTransportInfo"),
        )
        renderer.handleControlRequest(
            serviceName = "ConnectionManager",
            soapActionHeader = "\"urn:schemas-upnp-org:service:ConnectionManager:1#GetProtocolInfo\"",
            body = soapAction("GetProtocolInfo"),
        )

        assertTrue(logs.contains("[DLNA] Action: AVTransport.GetTransportInfo"))
        assertTrue(logs.contains("[DLNA] Action: ConnectionManager.GetProtocolInfo"))
    }

    @Test
    fun skipsLoggingNoisyPollingSoapActions() {
        val logs = mutableListOf<String>()
        val renderer = DlnaRendererController(
            log = { logs.add(it) },
            onPlayRequested = {},
            onStopRequested = {},
            onPauseRequested = {},
        )

        renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"",
            body = soapAction("GetPositionInfo"),
        )
        renderer.handleControlRequest(
            serviceName = "RenderingControl",
            soapActionHeader = "\"urn:schemas-upnp-org:service:RenderingControl:1#GetVolume\"",
            body = soapAction("GetVolume"),
        )

        assertTrue(logs.none { it.contains("GetPositionInfo") })
        assertTrue(logs.none { it.contains("GetVolume") })
    }

    @Test
    fun reportsCommonAvTransportCapabilityQueries() {
        val renderer = DlnaRendererController(
            log = {},
            onPlayRequested = {},
            onStopRequested = {},
            onPauseRequested = {},
        )

        val capabilities = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetDeviceCapabilities\"",
            body = soapAction("GetDeviceCapabilities"),
        )
        val settings = renderer.handleControlRequest(
            serviceName = "AVTransport",
            soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#GetTransportSettings\"",
            body = soapAction("GetTransportSettings"),
        )

        assertEquals(200, capabilities.statusCode)
        assertTrue(capabilities.body.contains("<PlayMedia>NETWORK</PlayMedia>"))
        assertEquals(200, settings.statusCode)
        assertTrue(settings.body.contains("<PlayMode>NORMAL</PlayMode>"))
    }

    private fun soapAction(actionName: String, vararg values: Pair<String, String>): String {
        val args = values.joinToString("") { (name, value) -> "<$name>$value</$name>" }
        return """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:$actionName xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$args</u:$actionName>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}
