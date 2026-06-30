package labs.newrapaw.dlna.probe.admin

fun buildLogsPageScript(): String = """
    let logsRefreshPaused = false;
    let logsRefreshFailureCount = 0;
    let logsRefreshConsecutiveFailures = 0;
    let monitorLogEvents = [];
    let monitorLogsCollapsed = false;
    let serverLogsText = (document.getElementById('log-content')?.textContent) || '';

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

    function setMonitorLogToggleButtonState() {
      const button = document.getElementById('toggle-monitor-logs');
      if (!button) {
        return;
      }
      button.textContent = monitorLogsCollapsed ? '展开前端监控事件' : '折叠前端监控事件';
    }

    function renderLogContent() {
      const monitorText = monitorLogEvents.map((entry) => '[monitor] ' + entry).join('\n');
      const serverLogCount = serverLogsText.split('\n').filter((line) => line.trim().length > 0).length;
      const sections = [];
      if (!monitorLogsCollapsed && monitorText) {
        sections.push('=== 前端监控事件（' + monitorLogEvents.length + ' 条）===');
        sections.push(monitorText);
      }
      if (serverLogsText) {
        sections.push('=== 服务端原始日志（' + serverLogCount + ' 条）===');
        sections.push(serverLogsText);
      }
      const logContent = document.getElementById('log-content');
      if (logContent) {
        logContent.textContent = sections.join('\n');
      }
    }

    function appendMonitorLogEvent(message) {
      const timestamp = formatRefreshTime(new Date());
      monitorLogEvents.unshift(timestamp + ' ' + message);
      if (monitorLogEvents.length > 100) {
        monitorLogEvents = monitorLogEvents.slice(0, 100);
      }
      renderLogContent();
    }

    function markLogsRefreshFailure() {
      logsRefreshFailureCount += 1;
      logsRefreshConsecutiveFailures += 1;
      appendMonitorLogEvent('日志面板刷新失败');
      updateRefreshStatus(
        'logs-refresh-status',
        '日志刷新',
        '最近失败',
        formatRefreshTime(new Date()) + '，' + refreshFailureSummary(logsRefreshFailureCount, logsRefreshConsecutiveFailures),
      );
    }

    async function refreshLogs() {
      if (logsRefreshPaused) {
        updateRefreshStatus(
          'logs-refresh-status',
          '日志刷新',
          '日志刷新已暂停',
          refreshFailureSummary(logsRefreshFailureCount, logsRefreshConsecutiveFailures),
        );
        return;
      }
      const response = await fetch('/logs', { cache: 'no-store' });
      serverLogsText = await response.text();
      renderLogContent();
      logsRefreshConsecutiveFailures = 0;
      updateRefreshStatus(
        'logs-refresh-status',
        '日志刷新',
        '最近成功',
        formatRefreshTime(new Date()) + '，' + refreshFailureSummary(logsRefreshFailureCount, logsRefreshConsecutiveFailures),
      );
    }

    const copyLogsButton = document.getElementById('copy-logs');
    if (copyLogsButton) {
      copyLogsButton.addEventListener('click', async () => {
        const text = (document.getElementById('log-content')?.textContent) || '';
        try {
          await copyText(text);
          showActionFeedback('日志已复制', false);
        } catch (error) {
          showActionFeedback(error.message || '复制失败', true);
        }
      });
    }

    const clearMonitorLogsButton = document.getElementById('clear-monitor-logs');
    if (clearMonitorLogsButton) {
      clearMonitorLogsButton.addEventListener('click', () => {
        monitorLogEvents = [];
        renderLogContent();
        showActionFeedback('监控事件已清空', false);
      });
    }

    const toggleMonitorLogsButton = document.getElementById('toggle-monitor-logs');
    if (toggleMonitorLogsButton) {
      toggleMonitorLogsButton.addEventListener('click', () => {
        monitorLogsCollapsed = !monitorLogsCollapsed;
        setMonitorLogToggleButtonState();
        renderLogContent();
      });
    }

    const toggleLogsRefreshButton = document.getElementById('toggle-logs-refresh');
    if (toggleLogsRefreshButton) {
      toggleLogsRefreshButton.addEventListener('click', () => {
        logsRefreshPaused = !logsRefreshPaused;
        appendMonitorLogEvent(logsRefreshPaused ? '暂停日志刷新' : '恢复日志刷新');
        setRefreshButtonState('toggle-logs-refresh', logsRefreshPaused, '恢复日志刷新', '暂停日志刷新');
        updateRefreshStatus(
          'logs-refresh-status',
          '日志刷新',
          logsRefreshPaused ? '日志刷新已暂停' : '等待刷新',
          refreshFailureSummary(logsRefreshFailureCount, logsRefreshConsecutiveFailures),
        );
        if (!logsRefreshPaused) {
          refreshLogs().catch(() => {
            markLogsRefreshFailure();
          });
        }
      });
    }

    setRefreshButtonState('toggle-logs-refresh', logsRefreshPaused, '恢复日志刷新', '暂停日志刷新');
    setMonitorLogToggleButtonState();
    renderLogContent();
    refreshLogs().catch(() => {
      markLogsRefreshFailure();
    });
    setInterval(async () => {
      try {
        await refreshLogs();
      } catch (_) {
        markLogsRefreshFailure();
      }
    }, 1000);
""".trimIndent()
