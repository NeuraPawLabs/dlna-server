package labs.newrapaw.dlna.probe.dlna

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpAdvertiserTest {
    @Test
    fun formatSsdpDateUsesRfc1123Utc() {
        assertEquals(
            "Tue, 30 Jun 2026 12:34:56 GMT",
            formatSsdpDate(Instant.parse("2026-06-30T12:34:56Z")),
        )
    }

    @Test
    fun closeSendsByebyeBeforeStoppingAdvertiserLoop() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/dlna/SsdpAdvertiser.kt")),
            Charsets.UTF_8,
        )
        val closeBlock = source.substringAfter("override fun close() {").substringBefore("private fun receiveLoop()")

        assertTrue(closeBlock.indexOf("notifyByebye()") < closeBlock.indexOf("running.set(false)"))
    }

    @Test
    fun startFailureCleansUpMulticastResources() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/dlna/SsdpAdvertiser.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("cleanupStartFailure()"))
        assertTrue(source.contains("runCatching { socket?.close() }"))
        assertTrue(source.contains("runCatching { multicastLock?.release() }"))
        assertTrue(source.contains("multicastLock = null"))
        assertTrue(source.contains("socket = null"))
    }

    @Test
    fun schedulesAliveNotificationsWithFixedDelayInsteadOfFixedRate() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/dlna/SsdpAdvertiser.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("scheduleWithFixedDelay"))
    }

    @Test
    fun looksUpWifiManagerFromApplicationContext() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/labs/newrapaw/dlna/probe/dlna/SsdpAdvertiser.kt")),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("appContext.applicationContext.getSystemService"))
    }
}
