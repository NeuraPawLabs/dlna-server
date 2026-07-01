package labs.newrapaw.dlna.probe.admin

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.ProxySettingsState

fun buildPlayPageContent(): String = """
    <section class="page-section" id="play">
      <div class="section-head">
        <h3>播放控制</h3>
      </div>
      <form class="stack-form" method="post" action="/control/play" data-control-form>
        <label for="url">输入 m3u8 地址</label>
        <textarea id="url" name="url" autofocus></textarea>
        <button type="submit">开始播放</button>
      </form>
      <form class="inline-form" method="post" action="/control/stop" data-control-form>
        <button type="submit">停止播放</button>
      </form>
    </section>
""".trimIndent()

fun buildLogsPageContent(logs: List<String>): String = """
    <section class="page-section" id="logs">
      <div class="section-head">
        <h3>实时日志</h3>
        <div class="toolbar">
          <button id="copy-logs" type="button">复制日志</button>
          <button id="toggle-logs-refresh" type="button">暂停日志刷新</button>
          <button id="toggle-monitor-logs" type="button">折叠前端监控事件</button>
          <button id="clear-monitor-logs" type="button">清空监控事件</button>
        </div>
      </div>
      <p id="logs-refresh-status" class="refresh-status">日志刷新：等待首次刷新</p>
      <pre id="log-content" class="log-console">${escapeHtml(logs.joinToString("\n"))}</pre>
    </section>
""".trimIndent()

internal fun buildCachePageContent(
    proxySettings: ProxySettingsState,
    playbackDiagnostics: AdminPlaybackDiagnosticsSnapshot,
    activeSession: ActiveSessionInfo?,
): String = """
    <section class="page-section" id="cache-overview">
      <div class="section-head">
        <h3>缓存概览</h3>
      </div>
      <div class="kv-grid">
        <div class="kv-item"><span class="kv-label">当前会话</span><strong>${escapeHtml(activeSession?.sessionId ?: "-")}</strong></div>
        <div class="kv-item"><span class="kv-label">会话状态</span><strong>${escapeHtml(activeSession?.status?.name ?: "-")}</strong></div>
        <div class="kv-item"><span class="kv-label">会话资源</span><strong>${playbackDiagnostics.sessionReadyAssetCount} / ${playbackDiagnostics.sessionTotalAssetCount}</strong></div>
        <div class="kv-item"><span class="kv-label">本地已落盘</span><strong>${formatBytes(playbackDiagnostics.sessionReadyBytes)}</strong></div>
        <div class="kv-item"><span class="kv-label">当前下载中</span><strong>${playbackDiagnostics.inFlightCount}</strong></div>
        <div class="kv-item"><span class="kv-label">当前待预取</span><strong>${playbackDiagnostics.pendingPrefetchCount}</strong></div>
        <div class="kv-item"><span class="kv-label">当前线路</span><strong>${escapeHtml(controlPageCurrentNetwork(proxySettings))}</strong></div>
      </div>
    </section>
    <section class="page-section" id="cache-diagnostics">
      <div class="section-head">
        <h3>播放诊断</h3>
        <div class="toolbar">
          <button id="copy-diagnostics" type="button">复制诊断 JSON</button>
          <a class="button-link" href="/diagnostics" target="_blank" rel="noopener">打开诊断 JSON</a>
          <button id="toggle-diagnostics-refresh" type="button">暂停排障刷新</button>
        </div>
      </div>
      <p id="diagnostics-refresh-status" class="refresh-status">排障刷新：等待首次刷新</p>
      <div id="diagnostics-panel">
      ${buildDiagnosticsPanelHtml(playbackDiagnostics)}
      </div>
    </section>
""".trimIndent()

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
