package labs.newrapaw.dlna.probe

import java.net.InetSocketAddress
import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigTest {
    @Test
    fun parseProxyConfigAcceptsHttpSocks5AndSocks5hUrls() {
        assertEquals(
            ProxyConfig(id = "http-127.0.0.1-8080", type = ProxyType.HTTP, host = "127.0.0.1", port = 8080),
            parseProxyConfig("http://127.0.0.1:8080"),
        )
        assertEquals(
            ProxyConfig(id = "socks5-10.0.0.2-1080", type = ProxyType.SOCKS5, host = "10.0.0.2", port = 1080),
            parseProxyConfig("socks5://10.0.0.2:1080"),
        )
        assertEquals(
            ProxyConfig(id = "socks5h-proxy.example-1080", type = ProxyType.SOCKS5H, host = "proxy.example", port = 1080),
            parseProxyConfig("socks5h://proxy.example:1080"),
        )
    }

    @Test
    fun parseProxyConfigRejectsUnsupportedOrIncompleteUrls() {
        assertNull(parseProxyConfig(""))
        assertNull(parseProxyConfig("https://proxy.example:443"))
        assertNull(parseProxyConfig("http://proxy.example"))
        assertNull(parseProxyConfig("socks4://proxy.example:1080"))
    }

    @Test
    fun toJavaProxyUsesExpectedProxyTypesAndDnsMode() {
        val http = ProxyConfig("a", ProxyType.HTTP, "127.0.0.1", 8080).toJavaProxy()
        val socks5 = ProxyConfig("b", ProxyType.SOCKS5, "127.0.0.1", 1080).toJavaProxy()
        val socks5h = ProxyConfig("c", ProxyType.SOCKS5H, "proxy.example", 1080).toJavaProxy()

        assertEquals(Proxy.Type.HTTP, http.type())
        assertEquals(Proxy.Type.SOCKS, socks5.type())
        assertEquals(Proxy.Type.SOCKS, socks5h.type())
        assertFalse((socks5.address() as InetSocketAddress).isUnresolved)
        assertTrue((socks5h.address() as InetSocketAddress).isUnresolved)
    }

    @Test
    fun proxyStateCanAddSelectAndRemoveConfigs() {
        val http = ProxyConfig("a", ProxyType.HTTP, "127.0.0.1", 8080)
        val socks = ProxyConfig("b", ProxyType.SOCKS5H, "proxy.example", 1080)
        val state = ProxySettingsState().add(http).add(socks).select("b")

        assertEquals(socks, state.selectedProxy())
        assertEquals("b", state.selectedProxyId)

        val removed = state.remove("b")
        assertNull(removed.selectedProxy())
        assertEquals("direct", removed.selectedProxyId)
        assertEquals(listOf(http), removed.proxies)
    }
}
