package labs.newrapaw.dlna.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxySettingsTest {
    @Test
    fun inMemoryStoreRoundTripsSelectedProxy() {
        val proxy = ProxyConfig(
            id = "http-localhost-8080",
            type = ProxyType.HTTP,
            host = "127.0.0.1",
            port = 8080,
        )
        val store = InMemoryProxySettingsStore()
        store.save(
            ProxySettingsState()
                .add(proxy)
                .select(proxy.id)
                .withUpstreamMode(UpstreamMode.PROXY_ONLY),
        )

        val loaded = store.load()

        assertEquals(proxy.id, loaded.selectedProxyId)
        assertEquals(proxy, loaded.selectedProxy())
    }

    @Test
    fun directSelectionDoesNotRequireAndroidStorage() {
        val loaded = InMemoryProxySettingsStore().load()

        assertEquals(ProxySettingsState.DIRECT_PROXY_ID, loaded.selectedProxyId)
        assertNull(loaded.selectedProxy())
    }
}
