package labs.newrapaw.dlna.probe

import android.content.SharedPreferences
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

enum class ProxyType(val scheme: String) {
    HTTP("http"),
    SOCKS5("socks5"),
    SOCKS5H("socks5h"),
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
) {
    fun selectedProxy(): ProxyConfig? =
        proxies.firstOrNull { it.id == selectedProxyId }

    fun add(config: ProxyConfig): ProxySettingsState =
        copy(proxies = (proxies.filterNot { it.id == config.id } + config))

    fun select(proxyId: String): ProxySettingsState =
        if (proxyId == DIRECT_PROXY_ID || proxies.any { it.id == proxyId }) {
            copy(selectedProxyId = proxyId)
        } else {
            this
        }

    fun remove(proxyId: String): ProxySettingsState {
        val nextProxies = proxies.filterNot { it.id == proxyId }
        val nextSelected = if (selectedProxyId == proxyId) DIRECT_PROXY_ID else selectedProxyId
        return copy(proxies = nextProxies, selectedProxyId = nextSelected)
    }

    companion object {
        const val DIRECT_PROXY_ID = "direct"
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

class SharedPreferencesProxySettingsStore(
    private val preferences: SharedPreferences,
) : ProxySettingsStore {
    override fun load(): ProxySettingsState {
        val raw = preferences.getString(KEY_STATE, null) ?: return ProxySettingsState()
        return runCatching { decodeState(JSONObject(raw)) }.getOrDefault(ProxySettingsState())
    }

    override fun save(state: ProxySettingsState) {
        preferences.edit().putString(KEY_STATE, encodeState(state).toString()).apply()
    }

    private fun encodeState(state: ProxySettingsState): JSONObject {
        val proxies = JSONArray()
        state.proxies.forEach { proxy ->
            proxies.put(
                JSONObject()
                    .put("id", proxy.id)
                    .put("type", proxy.type.scheme)
                    .put("host", proxy.host)
                    .put("port", proxy.port),
            )
        }
        return JSONObject()
            .put("selectedProxyId", state.selectedProxyId)
            .put("proxies", proxies)
    }

    private fun decodeState(json: JSONObject): ProxySettingsState {
        val proxiesJson = json.optJSONArray("proxies") ?: JSONArray()
        val proxies = buildList {
            for (index in 0 until proxiesJson.length()) {
                val item = proxiesJson.optJSONObject(index) ?: continue
                val type = proxyTypeFromScheme(item.optString("type")) ?: continue
                val host = item.optString("host").takeIf { it.isNotBlank() } ?: continue
                val port = item.optInt("port").takeIf { it in 1..65535 } ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: proxyId(type, host, port)
                add(ProxyConfig(id, type, host, port))
            }
        }
        val selected = json.optString("selectedProxyId", ProxySettingsState.DIRECT_PROXY_ID)
        return ProxySettingsState(proxies = proxies, selectedProxyId = selected).select(selected)
    }

    private companion object {
        const val KEY_STATE = "proxy_settings_state"
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
