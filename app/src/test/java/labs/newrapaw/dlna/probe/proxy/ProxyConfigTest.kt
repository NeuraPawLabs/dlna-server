package labs.newrapaw.dlna.probe.proxy

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
            labs.newrapaw.dlna.probe.core.ProxyConfig(id = "http-127.0.0.1-8080", type = labs.newrapaw.dlna.probe.core.ProxyType.HTTP, host = "127.0.0.1", port = 8080),
            labs.newrapaw.dlna.probe.core.parseProxyConfig("http://127.0.0.1:8080"),
        )
        assertEquals(
            labs.newrapaw.dlna.probe.core.ProxyConfig(id = "socks5-10.0.0.2-1080", type = labs.newrapaw.dlna.probe.core.ProxyType.SOCKS5, host = "10.0.0.2", port = 1080),
            labs.newrapaw.dlna.probe.core.parseProxyConfig("socks5://10.0.0.2:1080"),
        )
        assertEquals(
            labs.newrapaw.dlna.probe.core.ProxyConfig(id = "socks5h-proxy.example-1080", type = labs.newrapaw.dlna.probe.core.ProxyType.SOCKS5H, host = "proxy.example", port = 1080),
            labs.newrapaw.dlna.probe.core.parseProxyConfig("socks5h://proxy.example:1080"),
        )
    }

    @Test
    fun parseProxyConfigRejectsUnsupportedOrIncompleteUrls() {
        assertNull(labs.newrapaw.dlna.probe.core.parseProxyConfig(""))
        assertNull(labs.newrapaw.dlna.probe.core.parseProxyConfig("https://proxy.example:443"))
        assertNull(labs.newrapaw.dlna.probe.core.parseProxyConfig("http://proxy.example"))
        assertNull(labs.newrapaw.dlna.probe.core.parseProxyConfig("socks4://proxy.example:1080"))
    }

    @Test
    fun toJavaProxyUsesExpectedProxyTypesAndDnsMode() {
        val http = labs.newrapaw.dlna.probe.core.ProxyConfig("a", labs.newrapaw.dlna.probe.core.ProxyType.HTTP, "127.0.0.1", 8080).toJavaProxy()
        val socks5 = labs.newrapaw.dlna.probe.core.ProxyConfig("b", labs.newrapaw.dlna.probe.core.ProxyType.SOCKS5, "127.0.0.1", 1080).toJavaProxy()
        val socks5h = labs.newrapaw.dlna.probe.core.ProxyConfig("c", labs.newrapaw.dlna.probe.core.ProxyType.SOCKS5H, "proxy.example", 1080).toJavaProxy()

        assertEquals(Proxy.Type.HTTP, http.type())
        assertEquals(Proxy.Type.SOCKS, socks5.type())
        assertEquals(Proxy.Type.SOCKS, socks5h.type())
        assertFalse((socks5.address() as InetSocketAddress).isUnresolved)
        assertTrue((socks5h.address() as InetSocketAddress).isUnresolved)
    }

    @Test
    fun proxyStateCanAddSelectAndRemoveConfigs() {
        val http = labs.newrapaw.dlna.probe.core.ProxyConfig("a", labs.newrapaw.dlna.probe.core.ProxyType.HTTP, "127.0.0.1", 8080)
        val socks = labs.newrapaw.dlna.probe.core.ProxyConfig("b", labs.newrapaw.dlna.probe.core.ProxyType.SOCKS5H, "proxy.example", 1080)
        val state = labs.newrapaw.dlna.probe.core.ProxySettingsState()
            .add(http)
            .add(socks)
            .select("b")
            .withUpstreamMode(labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY)

        assertEquals(socks, state.selectedProxy())
        assertEquals("b", state.selectedProxyId)
        assertEquals(labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY, state.upstreamMode)

        val removed = state.remove("b")
        assertNull(removed.selectedProxy())
        assertEquals("direct", removed.selectedProxyId)
        assertEquals(labs.newrapaw.dlna.probe.core.UpstreamMode.PROXY_ONLY, removed.upstreamMode)
        assertEquals(listOf(http), removed.proxies)
    }

    @Test
    fun proxyStateIgnoresRaceModeWhenDirectIsSelected() {
        val state = labs.newrapaw.dlna.probe.core.ProxySettingsState().withUpstreamMode(labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY)

        assertEquals("direct", state.selectedProxyId)
        assertEquals(labs.newrapaw.dlna.probe.core.UpstreamMode.PROXY_ONLY, state.upstreamMode)
    }

    @Test
    fun proxySettingsClampPrefetchConcurrencyIntoAllowedRange() {
        assertEquals(1, labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = -5).normalized().prefetchConcurrency)
        assertEquals(6, labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 999).normalized().prefetchConcurrency)
        assertEquals(3, labs.newrapaw.dlna.probe.core.ProxySettingsState(prefetchConcurrency = 3).normalized().prefetchConcurrency)
    }

    @Test
    fun proxyStateNormalizationPreservesExistingProxySelectionAndMode() {
        val state = labs.newrapaw.dlna.probe.core.ProxySettingsState(
            proxies = listOf(labs.newrapaw.dlna.probe.core.ProxyConfig("proxy-1", labs.newrapaw.dlna.probe.core.ProxyType.HTTP, "127.0.0.1", 8080)),
            selectedProxyId = "proxy-1",
            upstreamMode = labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY,
            prefetchConcurrency = 42,
        ).normalized()

        assertEquals("proxy-1", state.selectedProxyId)
        assertEquals(labs.newrapaw.dlna.probe.core.UpstreamMode.RACE_DIRECT_AND_PROXY, state.upstreamMode)
        assertEquals(6, state.prefetchConcurrency)
    }

}
