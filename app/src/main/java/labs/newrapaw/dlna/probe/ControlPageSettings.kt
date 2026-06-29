package labs.newrapaw.dlna.probe

fun buildSettingsPageContent(proxySettings: ProxySettingsState): String = """
    <section class="page-section" id="settings-proxy">
      <h3>代理配置</h3>
      <form class="stack-form" method="post" action="/control/proxy/add" data-control-form>
        <label for="proxyUrl">代理地址</label>
        <input id="proxyUrl" name="proxyUrl" type="text" placeholder="http://127.0.0.1:7890 或 socks5h://proxy.example:1080">
        <button type="submit">添加并启用</button>
      </form>
      <form class="stack-form" method="post" action="/control/proxy/select" data-control-form>
        ${proxyOptionsHtml(proxySettings)}
        ${upstreamModeHtml(proxySettings)}
        <button type="submit">启用选中代理</button>
      </form>
    </section>
    <section class="page-section" id="settings-cache">
      <h3>缓存配置</h3>
      <form class="stack-form" method="post" action="/control/prefetch/config" data-control-form>
        <label for="prefetchConcurrency">预取并发数</label>
        <input
          id="prefetchConcurrency"
          name="prefetchConcurrency"
          type="number"
          min="${ProxySettingsState.MIN_PREFETCH_CONCURRENCY}"
          max="${ProxySettingsState.MAX_PREFETCH_CONCURRENCY}"
          value="${proxySettings.prefetchConcurrency}">
        <button type="submit">应用预取设置</button>
      </form>
      <form class="inline-form" method="post" action="/control/cache/clear" data-control-form>
        <button type="submit">清空缓存</button>
      </form>
    </section>
    <section class="page-section" id="settings-update">
      <h3>更新</h3>
      <form class="stack-form" method="post" action="/control/update" data-control-form>
        <label for="apkUrl">输入 APK 地址</label>
        <textarea id="apkUrl" name="apkUrl"></textarea>
        <button type="submit">安装更新</button>
      </form>
    </section>
""".trimIndent()
