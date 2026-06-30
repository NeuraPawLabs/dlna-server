package labs.newrapaw.dlna.probe.admin

internal fun buildAdminDiagnosticsStyles(): String = """
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
""".trimIndent()
