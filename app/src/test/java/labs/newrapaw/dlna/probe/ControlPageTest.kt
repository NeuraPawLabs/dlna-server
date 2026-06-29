package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.core.session.SessionAssetKind
import labs.newrapaw.dlna.probe.core.session.SessionAssetState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPageTest {
    @Test
    fun diagnosticsPanelRendersSlotHealthGraph() {
        val html = buildDiagnosticsPanelHtml(
            PlaybackDiagnosticsSnapshot.empty().copy(
                playbackStatus = PlaybackDiagnosticsStatus.PLAYING,
                sessionStatus = "DEGRADED",
                startupGatePhase = "启动预热",
                startupGateReady = true,
                startupGateDetail = "启动资源已就绪",
                currentStallReason = "当前槽位 12 缺少硬依赖：音频、密钥",
                slotStates = listOf(
                    SlotDiagnosticsItem(
                        slotIndex = 10,
                        startMs = 40_000L,
                        endMs = 44_000L,
                        state = SlotDiagnosticsState.DEGRADED,
                        videoReady = true,
                        audioReady = true,
                        subtitleReady = false,
                        blockedAssetKinds = emptyList(),
                        degradedAssetKinds = listOf(SessionAssetKind.SUBTITLE_SEGMENT),
                        videoAssetIdRef = "video-10",
                        audioAssetIdRefs = listOf("audio-main-10"),
                        subtitleAssetIdRefs = listOf("subtitle-zh-10"),
                        prerequisiteAssetIdRefs = listOf("init-video-0", "key-video-0"),
                    ),
                    SlotDiagnosticsItem(
                        slotIndex = 11,
                        startMs = 44_000L,
                        endMs = 48_000L,
                        state = SlotDiagnosticsState.PLAYING,
                        videoReady = true,
                        audioReady = true,
                        subtitleReady = true,
                        blockedAssetKinds = emptyList(),
                        degradedAssetKinds = emptyList(),
                        videoAssetIdRef = "video-11",
                        audioAssetIdRefs = listOf("audio-main-11"),
                        subtitleAssetIdRefs = listOf("subtitle-zh-11"),
                        prerequisiteAssetIdRefs = listOf("init-video-0", "key-video-0"),
                    ),
                    SlotDiagnosticsItem(
                        slotIndex = 12,
                        startMs = 48_000L,
                        endMs = 52_000L,
                        state = SlotDiagnosticsState.BLOCKED,
                        videoReady = false,
                        audioReady = true,
                        subtitleReady = true,
                        blockedAssetKinds = listOf(SessionAssetKind.VIDEO_SEGMENT),
                        degradedAssetKinds = emptyList(),
                        videoAssetIdRef = "video-12",
                        audioAssetIdRefs = listOf("audio-main-12"),
                        subtitleAssetIdRefs = listOf("subtitle-zh-12"),
                        prerequisiteAssetIdRefs = listOf("init-video-0", "key-video-0"),
                    ),
                ),
                assetDiagnostics = listOf(
                    AssetDiagnosticsItem(
                        assetId = "init-video-0",
                        kind = SessionAssetKind.INIT_SEGMENT,
                        trackId = "video-main",
                        state = SessionAssetState.READY,
                        localReady = true,
                        sizeBytes = 2048,
                        lastElapsedMs = 40,
                        lastSource = "session-local",
                        retryCount = 1,
                        failureReason = null,
                    ),
                    AssetDiagnosticsItem(
                        assetId = "video-11",
                        kind = SessionAssetKind.VIDEO_SEGMENT,
                        trackId = "video-main",
                        state = SessionAssetState.READY,
                        localReady = true,
                        sizeBytes = 1_048_576,
                        lastElapsedMs = 180,
                        lastSource = "session-local",
                        retryCount = 1,
                        failureReason = null,
                    ),
                ),
                currentPlaybackSlotIndex = 11,
                bufferedSlotIndex = 12,
                currentPlaybackSlotReady = true,
                continuousReadySlotCount = 2,
                continuousReadySlotDurationMs = 8_000L,
            ),
        )

        assertTrue(html.contains("槽位全量健康图"))
        assertTrue(html.contains("id=\"segment-health-grid\""))
        assertTrue(html.contains("segment-health-block degraded"))
        assertTrue(html.contains("segment-health-block playing"))
        assertTrue(html.contains("segment-health-block failed"))
        assertTrue(html.contains("槽位详情"))
        assertTrue(html.contains("阻塞依赖"))
        assertTrue(html.contains("降级依赖"))
        assertTrue(html.contains("连续可播：2 槽"))
        assertTrue(html.contains("会话状态：降级可播"))
        assertTrue(html.contains("当前卡顿原因：当前槽位 12 缺少硬依赖：音频、密钥"))
        assertTrue(html.contains("关联资源"))
        assertTrue(html.contains("video-11"))
        assertFalse(html.contains("TS 全量健康图"))
        assertFalse(html.contains("分片详情"))
    }

    @Test
    fun cachePageRendersSessionOnlyContent() {
        val html = buildAdminShell(
            page = AdminPage.CACHE,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildCachePageContent(
                proxySettings = ProxySettingsState(),
                playbackDiagnostics = PlaybackDiagnosticsSnapshot.empty().copy(
                    sessionReadyAssetCount = 3,
                    sessionTotalAssetCount = 8,
                    sessionReadyBytes = 4096,
                    inFlightCount = 1,
                    pendingPrefetchCount = 2,
                ),
                activeSession = null,
            ),
            pageScript = buildCachePageScript(),
        )

        assertTrue(html.contains(">缓存<"))
        assertTrue(html.contains("当前会话"))
        assertTrue(html.contains("会话资源"))
        assertTrue(html.contains("本地已落盘"))
        assertFalse(html.contains("缓存条目"))
        assertFalse(html.contains("命中次数"))
        assertFalse(html.contains("未命中次数"))
    }

    @Test
    fun pageShellInjectsRefreshScriptsOnlyForCacheAndLogs() {
        val cacheHtml = buildAdminShell(
            page = AdminPage.CACHE,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildCachePageContent(
                proxySettings = ProxySettingsState(),
                playbackDiagnostics = PlaybackDiagnosticsSnapshot.empty(),
                activeSession = null,
            ),
            pageScript = buildCachePageScript(),
        )
        val logsHtml = buildAdminShell(
            page = AdminPage.LOGS,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildLogsPageContent(emptyList()),
            pageScript = buildLogsPageScript(),
        )
        val settingsHtml = buildAdminShell(
            page = AdminPage.SETTINGS,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildSettingsPageContent(ProxySettingsState()),
            pageScript = "",
        )

        assertTrue(cacheHtml.contains("refreshCachePage"))
        assertFalse(cacheHtml.contains("refreshLogs()"))
        assertTrue(logsHtml.contains("refreshLogs()"))
        assertFalse(logsHtml.contains("refreshCachePage"))
        assertFalse(settingsHtml.contains("diagnostics-refresh-status"))
        assertFalse(settingsHtml.contains("logs-refresh-status"))
    }

    @Test
    fun pageShellUsesPagedNavAndSettingsOwnsConfigurationForms() {
        val cacheHtml = buildAdminShell(
            page = AdminPage.CACHE,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildCachePageContent(
                proxySettings = ProxySettingsState(),
                playbackDiagnostics = PlaybackDiagnosticsSnapshot.empty(),
                activeSession = null,
            ),
            pageScript = buildCachePageScript(),
        )
        val settingsHtml = buildAdminShell(
            page = AdminPage.SETTINGS,
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            currentNetwork = "直连",
            bodyHtml = buildSettingsPageContent(ProxySettingsState()),
            pageScript = "",
        )

        assertTrue(cacheHtml.contains("""class="nav-link nav-current" href="/cache""""))
        assertTrue(settingsHtml.contains("""class="nav-link nav-current" href="/settings""""))
        assertTrue(settingsHtml.contains("代理地址"))
        assertTrue(settingsHtml.contains("输入 APK 地址"))
        assertTrue(settingsHtml.contains("预取并发数"))
        assertFalse(settingsHtml.contains("详细点播诊断日志"))
        assertFalse(settingsHtml.contains("/control/logging/config"))
        assertFalse(cacheHtml.contains("name=\"proxyUrl\""))
        assertFalse(cacheHtml.contains("name=\"apkUrl\""))
        assertFalse(cacheHtml.contains("name=\"prefetchConcurrency\""))
    }
}
