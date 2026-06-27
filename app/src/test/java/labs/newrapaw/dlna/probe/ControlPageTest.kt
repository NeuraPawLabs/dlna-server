package labs.newrapaw.dlna.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class ControlPageTest {
    @Test
    fun buildControlPageContainsPlayAndStopForms() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(),
            cacheStats = HlsSegmentCacheStats(entries = 0, sizeBytes = 0, hits = 0, misses = 0, inFlight = 0),
            logs = emptyList(),
        )

        assertTrue(html.contains("Honor Screen"))
        assertTrue(html.contains("Ready"))
        assertTrue(html.contains("nav"))
        assertTrue(html.contains("href=\"#play\""))
        assertTrue(html.contains("href=\"#proxy\""))
        assertTrue(html.contains("href=\"#logs\""))
        assertTrue(html.contains("href=\"#update\""))
        assertTrue(html.contains("href=\"#cache\""))
        assertTrue(html.contains("textarea"))
        assertTrue(html.contains("name=\"url\""))
        assertTrue(html.contains("action=\"/control/play\""))
        assertTrue(html.contains("action=\"/control/stop\""))
        assertTrue(html.contains("name=\"apkUrl\""))
        assertTrue(html.contains("action=\"/control/update\""))
    }

    @Test
    fun buildControlPageContainsAutoRefreshingLogs() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(
                proxies = listOf(ProxyConfig("p1", ProxyType.SOCKS5H, "proxy.example", 1080)),
                selectedProxyId = "p1",
            ),
            cacheStats = HlsSegmentCacheStats(entries = 2, sizeBytes = 4096, hits = 3, misses = 4, inFlight = 1),
            logs = listOf("Remote play request", "Player error: demo"),
        )

        assertTrue(html.contains("id=\"logs\""))
        assertTrue(html.contains("/logs"))
        assertTrue(html.contains("Remote play request"))
        assertTrue(html.contains("Player error: demo"))
    }

    @Test
    fun buildControlPageContainsProxyManagementForms() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(
                proxies = listOf(ProxyConfig("proxy-1", ProxyType.HTTP, "192.168.1.2", 7890)),
                selectedProxyId = "proxy-1",
                upstreamMode = UpstreamMode.RACE_DIRECT_AND_PROXY,
            ),
            cacheStats = HlsSegmentCacheStats(entries = 0, sizeBytes = 0, hits = 0, misses = 0, inFlight = 0),
            logs = emptyList(),
        )

        assertTrue(html.contains("action=\"/control/proxy/add\""))
        assertTrue(html.contains("name=\"proxyUrl\""))
        assertTrue(html.contains("action=\"/control/proxy/select\""))
        assertTrue(html.contains("action=\"/control/proxy/delete\""))
        assertTrue(html.contains("value=\"direct\""))
        assertTrue(html.contains("name=\"upstreamMode\""))
        assertTrue(html.contains("value=\"PROXY_ONLY\""))
        assertTrue(html.contains("value=\"RACE_DIRECT_AND_PROXY\""))
        assertTrue(html.contains("直连 + 代理竞速"))
        assertTrue(html.contains("http://192.168.1.2:7890"))
        assertTrue(html.contains("checked"))
    }

    @Test
    fun buildControlPageContainsCacheStatusAndClearForm() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(),
            cacheStats = HlsSegmentCacheStats(entries = 3, sizeBytes = 1024 * 1024, hits = 7, misses = 2, inFlight = 1),
            logs = emptyList(),
        )

        assertTrue(html.contains("id=\"cache\""))
        assertTrue(html.contains("Entries: 3"))
        assertTrue(html.contains("Size: 1.0 MB"))
        assertTrue(html.contains("Hits: 7"))
        assertTrue(html.contains("Misses: 2"))
        assertTrue(html.contains("In flight: 1"))
        assertTrue(html.contains("action=\"/control/cache/clear\""))
    }

    @Test
    fun buildControlPageContainsPrefetchConcurrencyForm() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(prefetchConcurrency = 4),
            cacheStats = HlsSegmentCacheStats(entries = 0, sizeBytes = 0, hits = 0, misses = 0, inFlight = 0),
            logs = emptyList(),
        )

        assertTrue(html.contains("name=\"prefetchConcurrency\""))
        assertTrue(html.contains("action=\"/control/prefetch/config\""))
        assertTrue(html.contains("value=\"4\""))
    }

    @Test
    fun buildControlPageContainsDetailedDiagnosticsToggle() {
        val html = buildControlPage(
            deviceName = "Honor Screen",
            status = "Ready",
            localPlaybackUrl = "http://127.0.0.1:43000",
            proxySettings = ProxySettingsState(detailedDiagnosticsEnabled = true),
            cacheStats = HlsSegmentCacheStats(entries = 0, sizeBytes = 0, hits = 0, misses = 0, inFlight = 0),
            logs = emptyList(),
        )

        assertTrue(html.contains("action=\"/control/logging/config\""))
        assertTrue(html.contains("name=\"detailedDiagnosticsEnabled\""))
        assertTrue(html.contains("Detailed VOD diagnostics"))
        assertTrue(html.contains("checked"))
    }

    @Test
    fun decodeFormUrlExtractsLongSignedUrl() {
        val original = "https://origin.example/object?filename=video.m3u8&X-Amz-Signature=abc+123"
        val body = "url=${URLEncoder.encode(original, "UTF-8")}"

        assertEquals(original, decodeFormUrl(body))
    }

    @Test
    fun decodeFormValueExtractsApkUrl() {
        val original = "http://192.168.1.10:8080/app-debug.apk"
        val body = "apkUrl=${URLEncoder.encode(original, "UTF-8")}"

        assertEquals(original, decodeFormValue(body, "apkUrl"))
    }

    @Test
    fun decodeFormValueExtractsProxyUrl() {
        val original = "socks5h://proxy.example:1080"
        val body = "proxyUrl=${URLEncoder.encode(original, "UTF-8")}"

        assertEquals(original, decodeFormValue(body, "proxyUrl"))
    }
}
