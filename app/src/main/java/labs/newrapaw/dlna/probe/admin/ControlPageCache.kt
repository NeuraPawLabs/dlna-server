package labs.newrapaw.dlna.probe.admin

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.ProxySettingsState

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
