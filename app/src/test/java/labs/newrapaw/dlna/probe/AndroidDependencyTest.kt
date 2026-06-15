package labs.newrapaw.dlna.probe

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDependencyTest {
    @Test
    fun includesMedia3HlsPlaybackModule() {
        val buildFile = Paths.get("build.gradle.kts")
        val buildScript = String(Files.readAllBytes(buildFile), Charsets.UTF_8)

        assertTrue(buildScript.contains("androidx.media3:media3-exoplayer-hls"))
    }
}
