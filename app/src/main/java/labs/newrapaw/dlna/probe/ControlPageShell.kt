package labs.newrapaw.dlna.probe

enum class AdminPage(
    val path: String,
    val title: String,
) {
    PLAY("/play", "播放"),
    CACHE("/cache", "缓存"),
    LOGS("/logs-page", "日志"),
    SETTINGS("/settings", "设置"),
}

fun buildAdminShell(
    page: AdminPage,
    deviceName: String,
    status: String,
    localPlaybackUrl: String,
    currentNetwork: String,
    bodyHtml: String,
    pageScript: String,
): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$deviceName - ${page.title}</title>
        <style>
          :root {
            color-scheme: light;
            font-family: sans-serif;
            background: #f3f5f7;
            color: #111827;
          }
          * { box-sizing: border-box; }
          body { margin: 0; background: #f3f5f7; color: #111827; line-height: 1.5; }
          a { color: inherit; }
          .shell { min-height: 100vh; display: grid; grid-template-columns: 220px minmax(0, 1fr); }
          .sidebar {
            background: #111827;
            color: #f8fafc;
            padding: 20px 16px;
            display: flex;
            flex-direction: column;
            gap: 18px;
          }
          .brand { display: flex; flex-direction: column; gap: 4px; }
          .brand h1 { margin: 0; font-size: 18px; }
          .brand p { margin: 0; font-size: 12px; color: #94a3b8; }
          .nav-list { display: flex; flex-direction: column; gap: 6px; }
          .nav-link {
            display: block;
            padding: 10px 12px;
            border-radius: 8px;
            color: #cbd5e1;
            text-decoration: none;
            font-size: 14px;
          }
          .nav-link:hover { background: #1f2937; color: #f8fafc; }
          .nav-current { background: #1d4ed8; color: #eff6ff; }
          .content { min-width: 0; padding: 18px 22px 24px; }
          .topbar { display: flex; flex-direction: column; gap: 14px; margin-bottom: 16px; }
          .page-title { margin: 0; font-size: 22px; }
          .status-panel {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 10px;
            padding: 14px;
            border: 1px solid #dbe2ea;
            background: #ffffff;
            border-radius: 8px;
          }
          .status-item { min-width: 0; }
          .status-label { display: block; margin-bottom: 4px; font-size: 12px; color: #64748b; }
          .status-value { display: block; font-size: 14px; word-break: break-all; }
          .feedback {
            display: none;
            margin: 0 0 14px;
            padding: 10px 12px;
            border: 1px solid #bfdbfe;
            background: #eff6ff;
            color: #1d4ed8;
            border-radius: 8px;
          }
          .feedback.error { border-color: #fecaca; background: #fef2f2; color: #b91c1c; }
          .page-stack { display: flex; flex-direction: column; gap: 14px; }
          .page-section {
            border: 1px solid #dbe2ea;
            background: #ffffff;
            border-radius: 8px;
            padding: 14px;
          }
          .section-head {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 12px;
            margin-bottom: 12px;
          }
          .section-head h3 { margin: 0; font-size: 16px; }
          .toolbar { display: flex; flex-wrap: wrap; gap: 8px; }
          .button-link,
          button {
            appearance: none;
            border: 1px solid #cbd5e1;
            background: #f8fafc;
            color: #0f172a;
            border-radius: 8px;
            padding: 8px 12px;
            font-size: 14px;
            line-height: 1.2;
            text-decoration: none;
            cursor: pointer;
          }
          button:hover,
          .button-link:hover { background: #eff6ff; border-color: #93c5fd; }
          .stack-form,
          .inline-form {
            display: flex;
            flex-direction: column;
            gap: 10px;
          }
          .inline-form { align-items: flex-start; }
          label { font-size: 14px; color: #334155; }
          textarea,
          input[type="text"],
          input[type="number"] {
            width: 100%;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            background: #ffffff;
            color: #0f172a;
            padding: 10px 12px;
            font: inherit;
          }
          textarea { min-height: 140px; resize: vertical; }
          .proxy-row {
            display: grid;
            grid-template-columns: auto minmax(0, 1fr) auto;
            align-items: center;
            gap: 10px;
            padding: 10px 0;
            border-bottom: 1px solid #e5e7eb;
          }
          .proxy-row code { min-width: 0; word-break: break-all; }
          .kv-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
            gap: 10px;
          }
          .kv-item {
            border: 1px solid #e5e7eb;
            border-radius: 8px;
            padding: 10px 12px;
            background: #f8fafc;
          }
          .kv-label { display: block; margin-bottom: 6px; font-size: 12px; color: #64748b; }
          .refresh-status { margin: 0 0 10px; font-size: 13px; color: #475569; }
          .log-console {
            min-height: 360px;
            margin: 0;
            padding: 12px;
            overflow: auto;
            border-radius: 8px;
            background: #0f172a;
            color: #e2e8f0;
            white-space: pre-wrap;
            font-family: monospace;
          }
          .diagnostics-summary { margin: 0 0 16px; padding: 12px 14px; border: 1px solid #f59e0b; background: #fffbeb; color: #92400e; border-radius: 8px; }
          .severity-badge { display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 999px; font-size: 13px; }
          .severity-badge.ok { background: #dcfce7; color: #166534; }
          .severity-badge.warn { background: #fef3c7; color: #92400e; }
          .severity-badge.critical { background: #fee2e2; color: #b91c1c; }
          .diagnostics-summary ul { margin: 8px 0 0 18px; padding: 0; }
          .diagnostics-summary li { margin: 4px 0; }
          .diagnostics-group { margin: 12px 0; }
          .diagnostics-group summary { cursor: pointer; font-weight: 600; margin-bottom: 8px; }
          .diagnostics-group-body { padding-top: 8px; }
          .segment-health-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(16px, 16px)); gap: 4px; margin: 10px 0 14px; }
          .segment-health-block { position: relative; width: 16px; height: 16px; border: 1px solid #cbd5e1; }
          .segment-health-block.not-started { background: #ffffff; }
          .segment-health-block.cached { background: #22c55e; border-color: #16a34a; }
          .segment-health-block.playing { background: #2563eb; border-color: #1d4ed8; }
          .segment-health-block.in-flight { background: #f59e0b; border-color: #d97706; }
          .segment-health-block.degraded { background: #facc15; border-color: #ca8a04; }
          .segment-health-block.evicted { background: #9ca3af; border-color: #6b7280; }
          .segment-health-block.failed { background: #ef4444; border-color: #dc2626; }
          .segment-health-block.ready-window { box-shadow: inset 0 0 0 2px rgba(255,255,255,0.85); }
          .segment-health-block.buffer-edge::after {
            content: "";
            position: absolute;
            top: -3px;
            right: -3px;
            bottom: -3px;
            width: 3px;
            background: #111827;
            border-radius: 999px;
          }
          .segment-health-marker { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); }
          .segment-health-detail { margin: 12px 0; padding: 12px 14px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; }
          .source-tag { display: inline-block; min-width: 40px; padding: 2px 8px; border-radius: 999px; font-size: 13px; }
          .source-tag.direct { background: #dbeafe; color: #1d4ed8; }
          .source-tag.proxy { background: #ede9fe; color: #6d28d9; }
          .source-tag.race { background: #e5e7eb; color: #374151; }
          .reason-tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 13px; }
          .reason-tag.ok { background: #dcfce7; color: #166534; }
          .reason-tag.slow { background: #fef3c7; color: #92400e; }
          .reason-tag.timeout { background: #fee2e2; color: #b91c1c; }
          .reason-tag.fallback { background: #ede9fe; color: #6d28d9; }
          .reason-tag.failed { background: #e5e7eb; color: #374151; }
          .result-ok { color: #166534; font-weight: 600; }
          .result-error { color: #b91c1c; font-weight: 600; }
          table { width: 100%; border-collapse: collapse; margin-top: 8px; }
          th, td { padding: 8px 10px; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: top; }
          th { font-size: 13px; color: #4b5563; }
          @media (max-width: 900px) {
            .shell { grid-template-columns: 1fr; }
            .sidebar { gap: 12px; }
            .nav-list { flex-direction: row; flex-wrap: wrap; }
            .content { padding: 16px; }
            .section-head { flex-direction: column; }
          }
        </style>
      </head>
      <body>
        <div class="shell">
          <nav class="sidebar">
            <div class="brand">
              <h1>$deviceName</h1>
              <p>工具后台</p>
            </div>
            ${buildAdminNav(page)}
          </nav>
          <main class="content">
            <div class="topbar">
              <h2 class="page-title">${page.title}</h2>
              <div class="status-panel">
                <div class="status-item"><span class="status-label">状态</span><strong class="status-value">$status</strong></div>
                <div class="status-item"><span class="status-label">本地播放代理</span><strong class="status-value">$localPlaybackUrl</strong></div>
                <div class="status-item"><span class="status-label">当前网络</span><strong class="status-value">${escapeShellHtml(currentNetwork)}</strong></div>
              </div>
            </div>
            <div id="action-feedback" class="feedback" role="status" aria-live="polite"></div>
            <div class="page-stack">
              $bodyHtml
            </div>
          </main>
        </div>
        <script>
          ${buildCommonFormScript()}
          $pageScript
        </script>
      </body>
    </html>
""".trimIndent()

private fun buildAdminNav(page: AdminPage): String =
    """<div class="nav-list">${
        AdminPage.entries.joinToString("") { item ->
            val current = if (item == page) " nav-current" else ""
            """<a class="nav-link$current" href="${item.path}">${item.title}</a>"""
        }
    }</div>"""

fun controlPageCurrentNetwork(settings: ProxySettingsState): String =
    settings.selectedProxy()?.displayUrl()?.let { "${it}（${controlPageUpstreamModeLabel(settings.upstreamMode)}）" } ?: "直连"

private fun controlPageUpstreamModeLabel(mode: UpstreamMode): String =
    when (mode) {
        UpstreamMode.PROXY_ONLY -> "仅代理"
        UpstreamMode.RACE_DIRECT_AND_PROXY -> "直连 + 代理竞速"
    }

private fun escapeShellHtml(value: String): String =
    buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
