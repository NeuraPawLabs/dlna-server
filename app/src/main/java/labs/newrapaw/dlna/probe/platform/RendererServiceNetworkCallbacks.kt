package labs.newrapaw.dlna.probe.platform

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class RendererServiceNetworkCallbacks(
    private val connectivityManager: ConnectivityManager,
    private val closed: AtomicBoolean,
    private val onAvailable: () -> Unit,
    private val onLost: () -> Unit,
    private val onLinkPropertiesChanged: () -> Unit,
) : Closeable {
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (closed.get()) return
            onAvailable()
        }

        override fun onLost(network: Network) {
            if (closed.get()) return
            onLost()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (closed.get()) return
            onLinkPropertiesChanged()
        }
    }

    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        }
    }

    override fun close() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
