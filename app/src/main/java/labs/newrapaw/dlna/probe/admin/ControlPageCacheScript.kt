package labs.newrapaw.dlna.probe.admin

fun buildCachePageScript(): String = """
    let diagnosticsRefreshPaused = false;
    let diagnosticsRefreshFailureCount = 0;
    let diagnosticsRefreshConsecutiveFailures = 0;

    function formatRefreshTime(date) {
      return date.toLocaleTimeString('zh-CN', { hour12: false });
    }

    function refreshFailureSummary(totalFailures, consecutiveFailures) {
      return '累计失败 ' + totalFailures + ' 次，连续失败 ' + consecutiveFailures + ' 次';
    }

    function updateRefreshStatus(elementId, label, state, detail) {
      const element = document.getElementById(elementId);
      if (!element) {
        return;
      }
      element.textContent = label + '：' + state + (detail ? '（' + detail + '）' : '');
    }

    function setRefreshButtonState(buttonId, paused, runningText, pausedText) {
      const button = document.getElementById(buttonId);
      if (!button) {
        return;
      }
      button.textContent = paused ? runningText : pausedText;
    }

    function markDiagnosticsRefreshFailure() {
      diagnosticsRefreshFailureCount += 1;
      diagnosticsRefreshConsecutiveFailures += 1;
      updateRefreshStatus(
        'diagnostics-refresh-status',
        '排障刷新',
        '最近失败',
        formatRefreshTime(new Date()) + '，' + refreshFailureSummary(diagnosticsRefreshFailureCount, diagnosticsRefreshConsecutiveFailures),
      );
    }

    async function refreshCachePage() {
      if (diagnosticsRefreshPaused) {
        updateRefreshStatus(
          'diagnostics-refresh-status',
          '排障刷新',
          '排障刷新已暂停',
          refreshFailureSummary(diagnosticsRefreshFailureCount, diagnosticsRefreshConsecutiveFailures),
        );
        return;
      }
      const response = await fetch('/diagnostics/panel', { cache: 'no-store' });
      if (!response.ok) {
        throw new Error('刷新缓存页失败');
      }
      const diagnosticsPanel = document.getElementById('diagnostics-panel');
      if (diagnosticsPanel) {
        diagnosticsPanel.innerHTML = await response.text();
      }
      diagnosticsRefreshConsecutiveFailures = 0;
      updateRefreshStatus(
        'diagnostics-refresh-status',
        '排障刷新',
        '最近成功',
        formatRefreshTime(new Date()) + '，' + refreshFailureSummary(diagnosticsRefreshFailureCount, diagnosticsRefreshConsecutiveFailures),
      );
    }

    const copyDiagnosticsButton = document.getElementById('copy-diagnostics');
    if (copyDiagnosticsButton) {
      copyDiagnosticsButton.addEventListener('click', async () => {
        try {
          const response = await fetch('/diagnostics', { cache: 'no-store' });
          if (!response.ok) {
            throw new Error('获取诊断 JSON 失败');
          }
          const text = await response.text();
          await copyText(text);
          showActionFeedback('诊断 JSON 已复制', false);
        } catch (error) {
          showActionFeedback(error.message || '复制失败', true);
        }
      });
    }

    const toggleDiagnosticsRefresh = document.getElementById('toggle-diagnostics-refresh');
    if (toggleDiagnosticsRefresh) {
      toggleDiagnosticsRefresh.addEventListener('click', () => {
        diagnosticsRefreshPaused = !diagnosticsRefreshPaused;
        setRefreshButtonState('toggle-diagnostics-refresh', diagnosticsRefreshPaused, '恢复排障刷新', '暂停排障刷新');
        updateRefreshStatus(
          'diagnostics-refresh-status',
          '排障刷新',
          diagnosticsRefreshPaused ? '排障刷新已暂停' : '等待刷新',
          refreshFailureSummary(diagnosticsRefreshFailureCount, diagnosticsRefreshConsecutiveFailures),
        );
        if (!diagnosticsRefreshPaused) {
          refreshCachePage().catch(() => {
            markDiagnosticsRefreshFailure();
          });
        }
      });
    }

    setRefreshButtonState('toggle-diagnostics-refresh', diagnosticsRefreshPaused, '恢复排障刷新', '暂停排障刷新');
    refreshCachePage().catch(() => {
      markDiagnosticsRefreshFailure();
    });
    setInterval(async () => {
      try {
        await refreshCachePage();
      } catch (_) {
        markDiagnosticsRefreshFailure();
      }
    }, 1000);
""".trimIndent()
