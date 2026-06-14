package labs.newrapaw.dlna.probe

import java.net.URLDecoder

fun buildControlPage(
    deviceName: String,
    status: String,
    localPlaybackUrl: String,
    logs: List<String>,
): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$deviceName</title>
        <style>
          body { font-family: sans-serif; margin: 24px; line-height: 1.4; }
          textarea { width: 100%; height: 180px; font-family: monospace; }
          button { font-size: 18px; padding: 10px 16px; margin-top: 10px; }
          .status { margin: 12px 0; padding: 10px; background: #f2f2f2; }
          #logs { min-height: 220px; padding: 10px; overflow: auto; background: #111; color: #eee; white-space: pre-wrap; }
        </style>
      </head>
      <body>
        <h1>$deviceName</h1>
        <div class="status">Status: $status<br>Local playback proxy: $localPlaybackUrl</div>
        <form method="post" action="/control/play">
          <label for="url">Paste m3u8 URL</label>
          <textarea id="url" name="url" autofocus></textarea>
          <button type="submit">Play</button>
        </form>
        <form method="post" action="/control/stop">
          <button type="submit">Stop</button>
        </form>
        <hr>
        <form method="post" action="/control/update">
          <label for="apkUrl">Paste APK URL</label>
          <textarea id="apkUrl" name="apkUrl"></textarea>
          <button type="submit">Install Update</button>
        </form>
        <hr>
        <h2>Logs</h2>
        <pre id="logs">${escapeHtml(logs.joinToString("\n"))}</pre>
        <script>
          async function refreshLogs() {
            const response = await fetch('/logs', { cache: 'no-store' });
            document.getElementById('logs').textContent = await response.text();
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
