package labs.newrapaw.dlna.probe.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

enum class ProxyType(val scheme: String) {
    HTTP("http"),
    SOCKS5("socks5"),
    SOCKS5H("socks5h"),
}

enum class UpstreamMode {
    PROXY_ONLY,
    RACE_DIRECT_AND_PROXY,
}

data class ProxyConfig(
    val id: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
) {
    fun displayUrl(): String = "${type.scheme}://$host:$port"

    fun toJavaProxy(): Proxy {
        val address = when (type) {
            ProxyType.SOCKS5H -> InetSocketAddress.createUnresolved(host, port)
            ProxyType.HTTP, ProxyType.SOCKS5 -> InetSocketAddress(host, port)
        }
        val proxyType = if (type == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
        return Proxy(proxyType, address)
    }
}

data class ProxySettingsState(
    val proxies: List<ProxyConfig> = emptyList(),
    val selectedProxyId: String = DIRECT_PROXY_ID,
    val upstreamMode: UpstreamMode = UpstreamMode.PROXY_ONLY,
    val prefetchConcurrency: Int = DEFAULT_PREFETCH_CONCURRENCY,
) {
    fun normalized(): ProxySettingsState =
        copy(prefetchConcurrency = prefetchConcurrency.coerceIn(MIN_PREFETCH_CONCURRENCY, MAX_PREFETCH_CONCURRENCY))

    fun selectedProxy(): ProxyConfig? =
        proxies.firstOrNull { it.id == selectedProxyId }

    fun add(config: ProxyConfig): ProxySettingsState =
        copy(proxies = (proxies.filterNot { it.id == config.id } + config))

    fun select(proxyId: String): ProxySettingsState =
        if (proxyId == DIRECT_PROXY_ID || proxies.any { it.id == proxyId }) {
            copy(
                selectedProxyId = proxyId,
                upstreamMode = if (proxyId == DIRECT_PROXY_ID) UpstreamMode.PROXY_ONLY else upstreamMode,
            )
        } else {
            this
        }

    fun withUpstreamMode(mode: UpstreamMode): ProxySettingsState =
        copy(upstreamMode = if (selectedProxy() == null) UpstreamMode.PROXY_ONLY else mode)

    fun remove(proxyId: String): ProxySettingsState {
        val nextProxies = proxies.filterNot { it.id == proxyId }
        val nextSelected = if (selectedProxyId == proxyId) DIRECT_PROXY_ID else selectedProxyId
        return copy(
            proxies = nextProxies,
            selectedProxyId = nextSelected,
            upstreamMode = if (nextSelected == DIRECT_PROXY_ID) UpstreamMode.PROXY_ONLY else upstreamMode,
        )
    }

    companion object {
        const val DIRECT_PROXY_ID = "direct"
        const val DEFAULT_PREFETCH_CONCURRENCY = 3
        const val MIN_PREFETCH_CONCURRENCY = 1
        const val MAX_PREFETCH_CONCURRENCY = 6
    }
}

interface ProxySettingsStore {
    fun load(): ProxySettingsState
    fun save(state: ProxySettingsState)
}

class InMemoryProxySettingsStore(
    initialState: ProxySettingsState = ProxySettingsState(),
) : ProxySettingsStore {
    private var state = initialState

    override fun load(): ProxySettingsState = state

    override fun save(state: ProxySettingsState) {
        this.state = state
    }
}

fun parseProxyConfig(value: String): ProxyConfig? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val type = proxyTypeFromScheme(uri.scheme.orEmpty().lowercase()) ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val port = uri.port.takeIf { it in 1..65535 } ?: return null
    return ProxyConfig(
        id = proxyId(type, host, port),
        type = type,
        host = host,
        port = port,
    )
}

private fun proxyTypeFromScheme(scheme: String): ProxyType? =
    ProxyType.entries.firstOrNull { it.scheme == scheme }

private fun proxyId(type: ProxyType, host: String, port: Int): String =
    "${type.scheme}-${host.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")}-$port"
