package labs.newrapaw.dlna.probe.desktop

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import okhttp3.OkHttpClient
import java.io.File

interface DesktopProxy : AutoCloseable {
    fun start()
    fun openSession(sourceUrl: String): ActiveSessionInfo
}

class DesktopCliApp(
    private val proxyFactory: (DesktopCliArgs) -> DesktopProxy = { args ->
        object : DesktopProxy {
            private val proxy = CoreLocalHlsProxy(
                client = OkHttpClient(),
                log = ::println,
                sessionAssetRootDir = File(System.getProperty("java.io.tmpdir")).resolve("pawcast-desktop-session-assets"),
            )

            override fun start() = proxy.start()
            override fun close() = proxy.close()
            override fun openSession(sourceUrl: String) = proxy.openSession(sourceUrl)
        }
    },
    private val playerLauncher: DesktopPlayerLauncher = DesktopPlayerLauncher(),
    private val printLine: (String) -> Unit = ::println,
    private val waitForShutdown: () -> Unit = {
        val lock = Object()
        Runtime.getRuntime().addShutdownHook(
            Thread {
                synchronized(lock) {
                    lock.notifyAll()
                }
            },
        )
        synchronized(lock) {
            lock.wait()
        }
    },
) {
    fun run(args: DesktopCliArgs) {
        val proxy = proxyFactory(args)
        try {
            proxy.start()
            val session = proxy.openSession(args.url)
            printLine("Source: ${session.sourceUrl}")
            printLine("Local playback URL: ${session.localManifestUrl}")
            playerLauncher.launch(args.playerMode, session.localManifestUrl)
            waitForShutdown()
        } finally {
            proxy.close()
        }
    }
}

fun main(args: Array<String>) {
    DesktopCliApp().run(DesktopCliArgs.parse(args.toList()))
}
