package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.resolveRendererInstallationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RendererInstallationIdTest {
    @Test
    fun resolveRendererInstallationIdReusesExistingValueWithoutPersisting() {
        var persistedValue: String? = null

        val installationId = resolveRendererInstallationId(
            currentValue = "existing-installation-id",
            persist = { persistedValue = it },
            generate = { "generated-installation-id" },
        )

        assertEquals("existing-installation-id", installationId)
        assertNull(persistedValue)
    }

    @Test
    fun resolveRendererInstallationIdGeneratesAndPersistsWhenMissing() {
        var persistedValue: String? = null

        val installationId = resolveRendererInstallationId(
            currentValue = "   ",
            persist = { persistedValue = it },
            generate = { "generated-installation-id" },
        )

        assertEquals("generated-installation-id", installationId)
        assertEquals("generated-installation-id", persistedValue)
    }
}
