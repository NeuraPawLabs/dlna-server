package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNetworkConfigTest {
    @Test
    fun permitsCleartextForLanApkUpdates() {
        val configPath = Paths.get("src/main/res/xml/network_security_config.xml")
        val config = String(Files.readAllBytes(configPath), Charsets.UTF_8)

        assertTrue(config.contains("""<base-config cleartextTrafficPermitted="true""""))
    }
}
