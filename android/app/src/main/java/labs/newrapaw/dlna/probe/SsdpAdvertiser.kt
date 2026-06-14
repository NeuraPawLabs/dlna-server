package labs.newrapaw.dlna.probe

import android.content.Context
import android.net.wifi.WifiManager
import java.io.Closeable
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.nio.charset.Charset
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SsdpAdvertiser(
    context: Context,
    private val configProvider: () -> DlnaDeviceConfig?,
    private val log: (String) -> Unit,
) : Closeable {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var socket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (running.getAndSet(true)) return

        runCatching {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("newrapaw-dlna-ssdp")?.apply {
                setReferenceCounted(false)
                acquire()
            }

            val group = InetAddress.getByName(SSDP_ADDRESS)
            socket = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(SSDP_PORT))
                timeToLive = 4
                joinGroup(group)
            }

            executor.execute { receiveLoop() }
            executor.scheduleAtFixedRate({ notifyAlive() }, 0, 30, TimeUnit.SECONDS)
            log("[SSDP] Started")
        }.onFailure {
            running.set(false)
            log("[SSDP] Start failed: ${it.message}")
        }
    }

    override fun close() {
        running.set(false)
        runCatching { notifyByebye() }
        runCatching { socket?.close() }
        socket = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        executor.shutdownNow()
    }

    private fun receiveLoop() {
        val buffer = ByteArray(8192)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            runCatching {
                socket?.receive(packet)
                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                handleSearch(message, packet.address, packet.port)
            }.onFailure {
                if (running.get()) log("[SSDP] Receive failed: ${it.message}")
            }
        }
    }

    private fun handleSearch(message: String, address: InetAddress, port: Int) {
        if (!message.startsWith("M-SEARCH", ignoreCase = true)) return
        val searchTarget = message.lineSequence()
            .firstOrNull { it.uppercase(Locale.US).startsWith("ST:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
        if (searchTarget != "ssdp:all" && searchTarget !in SEARCH_TARGETS) return

        val targets = if (searchTarget == "ssdp:all") SEARCH_TARGETS else listOf(searchTarget)
        targets.forEach { send(searchResponse(it), address, port) }
    }

    private fun notifyAlive() {
        SEARCH_TARGETS.forEach { send(notifyMessage(it, "ssdp:alive"), InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT) }
    }

    private fun notifyByebye() {
        SEARCH_TARGETS.forEach { send(notifyMessage(it, "ssdp:byebye"), InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT) }
    }

    private fun searchResponse(searchTarget: String): String {
        val config = configProvider() ?: return ""
        return listOf(
            "HTTP/1.1 200 OK",
            "CACHE-CONTROL: max-age=1800",
            "DATE: ${Date().toString()}",
            "EXT:",
            "LOCATION: ${config.baseUrl}/description.xml",
            "SERVER: Android UPnP/1.0 NewraPawDLNA/0.1",
            "ST: $searchTarget",
            "USN: ${usn(config.uuid, searchTarget)}",
            "",
            "",
        ).joinToString("\r\n")
    }

    private fun notifyMessage(searchTarget: String, nts: String): String {
        val config = configProvider() ?: return ""
        return listOf(
            "NOTIFY * HTTP/1.1",
            "HOST: $SSDP_ADDRESS:$SSDP_PORT",
            "CACHE-CONTROL: max-age=1800",
            "LOCATION: ${config.baseUrl}/description.xml",
            "SERVER: Android UPnP/1.0 NewraPawDLNA/0.1",
            "NT: $searchTarget",
            "NTS: $nts",
            "USN: ${usn(config.uuid, searchTarget)}",
            "",
            "",
        ).joinToString("\r\n")
    }

    private fun send(message: String, address: InetAddress, port: Int) {
        if (message.isBlank()) return
        val bytes = message.toByteArray(Charset.forName("UTF-8"))
        val packet = DatagramPacket(bytes, bytes.size, address, port)
        runCatching { socket?.send(packet) }
            .onFailure { if (running.get()) log("[SSDP] Send failed: ${it.message}") }
    }

    private fun usn(uuid: String, searchTarget: String): String =
        if (searchTarget == "upnp:rootdevice") {
            "uuid:$uuid::upnp:rootdevice"
        } else {
            "uuid:$uuid::$searchTarget"
        }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val SEARCH_TARGETS = listOf(
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1",
        )
    }
}
