package labs.newrapaw.dlna.probe

import java.net.URLDecoder

fun buildControlPage(
    deviceName: String,
    status: String,
    localPlaybackUrl: String,
    proxySettings: ProxySettingsState,
    cacheStats: HlsSegmentCacheStats,
    logs: List<String>,
): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$deviceName</title>
        <style>
          body { font-family: sans-serif; margin: 0; line-height: 1.4; color: #1f2933; background: #f6f7f9; }
          .shell { display: flex; min-height: 100vh; }
          nav { width: 180px; padding: 24px 18px; background: #111827; color: white; box-sizing: border-box; }
          nav h1 { font-size: 18px; margin: 0 0 24px; line-height: 1.2; }
          nav a { display: block; color: #d1d5db; text-decoration: none; padding: 10px 0; }
          nav a:hover { color: white; }
          main { flex: 1; padding: 24px; box-sizing: border-box; }
          section { margin-bottom: 28px; background: white; border: 1px solid #e5e7eb; padding: 18px; }
          textarea { width: 100%; height: 180px; font-family: monospace; }
          input[type="text"] { width: 100%; box-sizing: border-box; font-size: 16px; padding: 10px; }
          button { font-size: 16px; padding: 9px 14px; margin-top: 10px; }
          .status { margin: 0 0 18px; padding: 10px; background: #eef2ff; }
          .proxy-row { display: flex; gap: 10px; align-items: center; padding: 8px 0; border-bottom: 1px solid #eee; }
          .proxy-row code { flex: 1; word-break: break-all; }
          #logs { min-height: 220px; padding: 10px; overflow: auto; background: #111; color: #eee; white-space: pre-wrap; }
          @media (max-width: 720px) {
            .shell { display: block; }
            nav { width: auto; }
            nav a { display: inline-block; margin-right: 14px; }
          }
        </style>
      </head>
      <body>
        <div class="shell">
          <nav>
            <h1>$deviceName</h1>
            <a href="#play">播放</a>
            <a href="#proxy">代理</a>
            <a href="#cache">缓存</a>
            <a href="#logs">日志</a>
            <a href="#update">更新</a>
          </nav>
          <main>
            <div class="status">Status: $status<br>Local playback proxy: $localPlaybackUrl<br>Current network: ${escapeHtml(proxyStatus(proxySettings))}</div>
            <section id="play">
              <h2>播放</h2>
              <form method="post" action="/control/play">
                <label for="url">Paste m3u8 URL</label>
                <textarea id="url" name="url" autofocus></textarea>
                <button type="submit">Play</button>
              </form>
              <form method="post" action="/control/stop">
                <button type="submit">Stop</button>
              </form>
            </section>
            <section id="proxy">
              <h2>代理</h2>
              <form method="post" action="/control/proxy/add">
                <label for="proxyUrl">Proxy URL</label>
                <input id="proxyUrl" name="proxyUrl" type="text" placeholder="http://127.0.0.1:7890 或 socks5h://proxy.example:1080">
                <button type="submit">Add and Use</button>
              </form>
              <form method="post" action="/control/proxy/select">
                ${proxyOptionsHtml(proxySettings)}
                ${upstreamModeHtml(proxySettings)}
                <button type="submit">Use Selected Proxy</button>
              </form>
            </section>
            <section id="cache">
              <h2>缓存</h2>
              <p>Entries: ${cacheStats.entries}</p>
              <p>Size: ${formatBytes(cacheStats.sizeBytes)}</p>
              <p>Hits: ${cacheStats.hits}</p>
              <p>Misses: ${cacheStats.misses}</p>
              <p>In flight: ${cacheStats.inFlight}</p>
              <form method="post" action="/control/prefetch/config">
                <label for="prefetchConcurrency">Prefetch concurrency</label>
                <input
                  id="prefetchConcurrency"
                  name="prefetchConcurrency"
                  type="number"
                  min="${ProxySettingsState.MIN_PREFETCH_CONCURRENCY}"
                  max="${ProxySettingsState.MAX_PREFETCH_CONCURRENCY}"
                  value="${proxySettings.prefetchConcurrency}">
                <button type="submit">Apply Prefetch Setting</button>
              </form>
              <form method="post" action="/control/logging/config">
                <label>
                  <input
                    type="checkbox"
                    name="detailedDiagnosticsEnabled"
                    value="true"${if (proxySettings.detailedDiagnosticsEnabled) " checked" else ""}>
                  Detailed VOD diagnostics
                </label>
                <button type="submit">Apply Logging Setting</button>
              </form>
              <form method="post" action="/control/cache/clear">
                <button type="submit">Clear Cache</button>
              </form>
            </section>
            <section id="logs">
              <h2>日志</h2>
              <pre id="log-content">${escapeHtml(logs.joinToString("\n"))}</pre>
            </section>
            <section id="update">
              <h2>更新</h2>
              <form method="post" action="/control/update">
                <label for="apkUrl">Paste APK URL</label>
                <textarea id="apkUrl" name="apkUrl"></textarea>
                <button type="submit">Install Update</button>
              </form>
            </section>
          </main>
        </div>
        <script>
          async function refreshLogs() {
            const response = await fetch('/logs', { cache: 'no-store' });
            document.getElementById('log-content').textContent = await response.text();
          }
          setInterval(refreshLogs, 1000);
          refreshLogs();
        </script>
      </body>
    </html>
""".trimIndent()

fun decodeFormUrl(body: String): String? {
    return decodeFormValue(body, "url")
}

fun decodeFormValue(body: String, key: String): String? {
    val params = body.split("&").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
    }.toMap()

    return params[key]?.trim()?.takeIf { it.isNotEmpty() }
}

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun proxyStatus(settings: ProxySettingsState): String =
    settings.selectedProxy()?.displayUrl() ?: "Direct"

private fun proxyOptionsHtml(settings: ProxySettingsState): String {
    val directChecked = if (settings.selectedProxyId == ProxySettingsState.DIRECT_PROXY_ID) " checked" else ""
    val direct = """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="direct"$directChecked> Direct</label>
        </div>
    """.trimIndent()

    val proxies = settings.proxies.joinToString("\n") { proxy ->
        val checked = if (settings.selectedProxyId == proxy.id) " checked" else ""
        """
        <div class="proxy-row">
          <label><input type="radio" name="proxyId" value="${escapeHtml(proxy.id)}"$checked></label>
          <code>${escapeHtml(proxy.displayUrl())}</code>
          <button type="submit" formaction="/control/proxy/delete" formmethod="post" name="proxyId" value="${escapeHtml(proxy.id)}">Delete</button>
        </div>
        """.trimIndent()
    }

    return listOf(direct, proxies).filter { it.isNotBlank() }.joinToString("\n")
}

private fun upstreamModeHtml(settings: ProxySettingsState): String {
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

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / 1024.0 / 1024.0)
        bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0)
        else -> "$bytes B"
    }
