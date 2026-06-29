package labs.newrapaw.dlna.probe

import android.content.SharedPreferences
import labs.newrapaw.dlna.probe.core.ProxyConfig
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxyType
import labs.newrapaw.dlna.probe.core.UpstreamMode
import labs.newrapaw.dlna.probe.core.parseProxyConfig
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesProxySettingsStore(
    private val preferences: SharedPreferences,
) : ProxySettingsStore {
    @Volatile
    private var state: ProxySettingsState = loadFromPreferences()

    override fun load(): ProxySettingsState = state

    override fun save(state: ProxySettingsState) {
        this.state = state
        preferences.edit().putString(KEY_STATE, encodeState(state).toString()).apply()
    }

    private fun loadFromPreferences(): ProxySettingsState {
        val raw = preferences.getString(KEY_STATE, null) ?: return ProxySettingsState()
        return runCatching { decodeState(JSONObject(raw)) }.getOrDefault(ProxySettingsState())
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
            .put("upstreamMode", state.upstreamMode.name)
            .put("prefetchConcurrency", state.normalized().prefetchConcurrency)
            .put("proxies", proxies)
    }

    private fun decodeState(json: JSONObject): ProxySettingsState {
        val proxiesJson = json.optJSONArray("proxies") ?: JSONArray()
        val proxies = buildList {
            for (index in 0 until proxiesJson.length()) {
                val item = proxiesJson.optJSONObject(index) ?: continue
                val type = ProxyType.entries.firstOrNull { it.scheme == item.optString("type") } ?: continue
                val host = item.optString("host").takeIf { it.isNotBlank() } ?: continue
                val port = item.optInt("port").takeIf { it in 1..65535 } ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: "${type.scheme}-${host.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")}-$port"
                add(ProxyConfig(id, type, host, port))
            }
        }
        val selected = json.optString("selectedProxyId", ProxySettingsState.DIRECT_PROXY_ID)
        val mode = runCatching {
            UpstreamMode.valueOf(json.optString("upstreamMode", UpstreamMode.PROXY_ONLY.name))
        }.getOrDefault(UpstreamMode.PROXY_ONLY)
        val prefetchConcurrency = json.optInt("prefetchConcurrency", ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY)
        return ProxySettingsState(
            proxies = proxies,
            selectedProxyId = selected,
            upstreamMode = mode,
            prefetchConcurrency = prefetchConcurrency,
        ).select(selected).withUpstreamMode(mode).normalized()
    }

    private companion object {
        const val KEY_STATE = "proxy_settings_state"
    }
}

typealias ProxyConfig = labs.newrapaw.dlna.probe.core.ProxyConfig
typealias ProxyType = labs.newrapaw.dlna.probe.core.ProxyType
typealias UpstreamMode = labs.newrapaw.dlna.probe.core.UpstreamMode
typealias ProxySettingsState = labs.newrapaw.dlna.probe.core.ProxySettingsState
typealias ProxySettingsStore = labs.newrapaw.dlna.probe.core.ProxySettingsStore
typealias InMemoryProxySettingsStore = labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore

fun parseProxyConfig(value: String): ProxyConfig? =
    labs.newrapaw.dlna.probe.core.parseProxyConfig(value)
