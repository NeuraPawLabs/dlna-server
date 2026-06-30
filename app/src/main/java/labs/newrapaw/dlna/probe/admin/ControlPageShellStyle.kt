package labs.newrapaw.dlna.probe.admin

internal fun buildAdminShellStyles(): String = """
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
""".trimIndent()
