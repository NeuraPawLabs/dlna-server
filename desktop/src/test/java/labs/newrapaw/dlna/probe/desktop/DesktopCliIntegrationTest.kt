package labs.newrapaw.dlna.probe.desktop

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCliIntegrationTest {
    @Test
    fun printsLocalPlaybackUrlAfterOpeningSession() {
        val output = StringBuilder()
        val app = DesktopCliApp(
            proxyFactory = {
                FakeDesktopProxy(
                    localManifestUrl = "http://127.0.0.1:41000/session/session-1/manifest.m3u8",
                )
            },
            playerLauncher = DesktopPlayerLauncher(commandExists = { false }, spawn = { it }),
            printLine = { output.appendLine(it) },
            waitForShutdown = {},
        )

        app.run(DesktopCliArgs(url = "https://example.com/video.m3u8", playerMode = PlayerMode.NONE))

        assertTrue(output.toString().contains("http://127.0.0.1:41000/session/session-1/manifest.m3u8"))
    }

    @Test
    fun keepsProxyAliveForManualPlaybackWhenNoPlayerIsLaunched() {
        val proxy = FakeDesktopProxy(
            localManifestUrl = "http://127.0.0.1:41000/session/session-1/manifest.m3u8",
        )
        var waitedForShutdown = false
        val app = DesktopCliApp(
            proxyFactory = { proxy },
            playerLauncher = DesktopPlayerLauncher(commandExists = { false }, spawn = { it }),
            waitForShutdown = { waitedForShutdown = true },
        )

        app.run(DesktopCliArgs(url = "https://example.com/video.m3u8", playerMode = PlayerMode.NONE))
        assertTrue(waitedForShutdown)
        assertTrue(proxy.closed)
    }
}

private class FakeDesktopProxy(
    private val localManifestUrl: String,
) : DesktopProxy {
    var closed: Boolean = false

    override fun start() = Unit
    override fun close() {
        closed = true
    }

    override fun openSession(sourceUrl: String): ActiveSessionInfo =
        ActiveSessionInfo(
            sessionId = "session-1",
            status = PlaybackSessionStatus.READY,
            sourceUrl = sourceUrl,
            localManifestUrl = localManifestUrl,
            slotCount = 1,
            assetCount = 1,
            prepared = true,
            pendingPrefetchAssetIds = emptyList(),
        )
}
