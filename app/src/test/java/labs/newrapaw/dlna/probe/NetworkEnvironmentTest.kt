package labs.newrapaw.dlna.probe

import java.net.SocketException
import labs.newrapaw.dlna.probe.platform.resolveRendererServiceIpAddress
import labs.newrapaw.dlna.probe.ui.resolveLocalIpAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkEnvironmentTest {
    @Test
    fun resolveLocalIpAddressReturnsUnknownWhenInterfaceEnumerationFails() {
        assertEquals(
            "unknown",
            resolveLocalIpAddress(
                networkInterfaces = { throw SocketException("boom") },
            ),
        )
    }

    @Test
    fun resolveRendererServiceIpAddressReturnsUnknownWhenInterfaceEnumerationFails() {
        assertEquals(
            "unknown",
            resolveRendererServiceIpAddress(
                networkInterfaces = { throw SocketException("boom") },
            ),
        )
    }
}
