package labs.newrapaw.dlna.probe

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
