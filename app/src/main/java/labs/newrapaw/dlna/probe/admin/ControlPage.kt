package labs.newrapaw.dlna.probe.admin

import java.net.URLDecoder
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import labs.newrapaw.dlna.probe.core.UpstreamMode

fun decodeFormUrl(body: String): String? =
    decodeFormValue(body, "url")

fun decodeFormValue(body: String, key: String): String? {
    val params = body.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size != 2) {
            return@mapNotNull null
        }
        runCatching {
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }.getOrNull()
    }.toMap()
    return params[key]?.trim()?.takeIf { it.isNotEmpty() }
}

fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

fun proxyStatus(settings: ProxySettingsState): String =
    settings.selectedProxy()?.displayUrl() ?: "直连"

fun proxyOptionsHtml(settings: ProxySettingsState): String {
    val directChecked = if (settings.selectedProxyId == ProxySettingsState.DIRECT_PROXY_ID) " checked" else ""
    val direct = """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="direct"$directChecked> 直连</label>
        </div>
    """.trimIndent()

    val proxies = settings.proxies.joinToString("\n") { proxy ->
        val checked = if (settings.selectedProxyId == proxy.id) " checked" else ""
        """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="${escapeHtml(proxy.id)}"$checked></label>
          <code>${escapeHtml(proxy.displayUrl())}</code>
          <button type="submit" formaction="/control/proxy/delete" formmethod="post" name="proxyId" value="${escapeHtml(proxy.id)}">删除</button>
        </div>
        """.trimIndent()
    }

    return listOf(direct, proxies).filter { it.isNotBlank() }.joinToString("\n")
}

fun upstreamModeHtml(settings: ProxySettingsState): String {
    val proxyOnlyChecked = if (settings.upstreamMode == UpstreamMode.PROXY_ONLY) " checked" else ""
    val raceChecked = if (settings.upstreamMode == UpstreamMode.RACE_DIRECT_AND_PROXY) " checked" else ""
    val disabled = if (settings.selectedProxy() == null) " disabled" else ""
    return """
        <div>
          <label><input type="radio" name="upstreamMode" value="PROXY_ONLY"$proxyOnlyChecked$disabled> 仅代理</label>
          <label><input type="radio" name="upstreamMode" value="RACE_DIRECT_AND_PROXY"$raceChecked$disabled> 直连 + 代理竞速</label>
        </div>
    """.trimIndent()
}
