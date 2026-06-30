package labs.newrapaw.dlna.probe.admin

import labs.newrapaw.dlna.probe.core.ProxySettingsState
import labs.newrapaw.dlna.probe.core.UpstreamMode

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
          ${buildAdminShellStyles()}
          ${buildAdminDiagnosticsStyles()}
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
