# Admin Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the admin backend from a single anchor-based page into a true four-page tool-style console with scoped auto-refresh, cleaner information architecture, and file splits that keep page-specific UI and scripts isolated.

**Architecture:** Replace the current monolithic control-page renderer with a shared shell plus page-specific content builders and script builders. Route requests to `/play`, `/cache`, `/logs`, and `/settings`, render only the current page body, and inject auto-refresh logic only on `缓存` and `日志` pages while moving configuration forms into `设置`.

**Tech Stack:** Kotlin, existing local HTTP admin UI, JUnit4 unit tests, Gradle Android unit test task

---

## File Map

**Create:**

- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
  - Shared page shell, nav, status banner, feedback area, shared style helpers.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPagePlay.kt`
  - `播放` page content.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt`
  - `缓存` page content, TS health view, details, diagnostics metrics.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt`
  - `日志` page content.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt`
  - `设置` page content and grouped forms.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageScripts.kt`
  - Shared form submission script plus page-scoped refresh scripts.

**Modify:**

- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
  - Reduce to thin entry points or remove old monolithic builder content in favor of delegated page-specific builders.
- `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
  - Add paged routes and make `/` resolve to the default page.
- `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
  - Update HTML expectations for paged rendering and tool-layout structure.
- `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
  - Add route coverage for `/play`, `/cache`, `/logs`, `/settings`.

## Task 1: Introduce page model and true paged routing

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

- [ ] **Step 1: Write the failing route test**

Add a test to `LocalHlsProxyStabilityTest.kt` asserting that each admin page route responds with 200 and page-specific content.

```kotlin
@Test
fun adminPagesRenderAsSeparateRoutes() {
    val proxy = LocalHlsProxy(log = {})

    proxy.start()
    try {
        val play = rawHttpRequest(proxy.port, "GET /play HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
        val cache = rawHttpRequest(proxy.port, "GET /cache HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
        val logs = rawHttpRequest(proxy.port, "GET /logs-page HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
        val settings = rawHttpRequest(proxy.port, "GET /settings HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

        assertTrue(play.startsWith("HTTP/1.1 200"))
        assertTrue(cache.startsWith("HTTP/1.1 200"))
        assertTrue(logs.startsWith("HTTP/1.1 200"))
        assertTrue(settings.startsWith("HTTP/1.1 200"))
        assertTrue(play.contains(">播放<"))
        assertTrue(cache.contains(">缓存<"))
        assertTrue(logs.contains(">日志<"))
        assertTrue(settings.contains(">设置<"))
    } finally {
        proxy.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest
```

Expected: FAIL because only `/` currently renders the admin page.

- [ ] **Step 3: Add minimal page model and route switching**

In `ControlPageShell.kt`, define a narrow page enum and nav model:

```kotlin
enum class AdminPage(
    val path: String,
    val title: String,
) {
    PLAY("/play", "播放"),
    CACHE("/cache", "缓存"),
    LOGS("/logs-page", "日志"),
    SETTINGS("/settings", "设置"),
}
```

Add a shared shell entry:

```kotlin
fun buildAdminShell(
    page: AdminPage,
    deviceName: String,
    status: String,
    localPlaybackUrl: String,
    currentNetwork: String,
    bodyHtml: String,
    pageScript: String,
): String = ...
```

In `LocalHlsProxy.kt`, update request handling:

```kotlin
method == "GET" && path == "/" -> handlePage(AdminPage.PLAY, output)
method == "GET" && path == AdminPage.PLAY.path -> handlePage(AdminPage.PLAY, output)
method == "GET" && path == AdminPage.CACHE.path -> handlePage(AdminPage.CACHE, output)
method == "GET" && path == AdminPage.LOGS.path -> handlePage(AdminPage.LOGS, output)
method == "GET" && path == AdminPage.SETTINGS.path -> handlePage(AdminPage.SETTINGS, output)
```

Keep the first implementation simple:

```kotlin
private fun handlePage(page: AdminPage, output: OutputStream) { ... }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "feat: add paged admin routes"
```

## Task 2: Split page bodies into focused files

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPagePlay.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] **Step 1: Write the failing page-content test**

Add a test to `ControlPageTest.kt` asserting that only the current page body is rendered.

```kotlin
@Test
fun cachePageRendersOnlyCacheContent() {
    val html = buildAdminShell(
        page = AdminPage.CACHE,
        deviceName = "Honor Screen",
        status = "Ready",
        localPlaybackUrl = "http://127.0.0.1:43000",
        currentNetwork = "直连",
        bodyHtml = buildCachePageContent(
            proxySettings = ProxySettingsState(),
            cacheStats = HlsSegmentCacheStats(0, 0, 0, 0, 0),
            playbackDiagnostics = PlaybackDiagnosticsSnapshot.empty(),
        ),
        pageScript = "",
    )

    assertTrue(html.contains(">缓存<"))
    assertTrue(html.contains("TS 全量健康图"))
    assertFalse(html.contains("输入 m3u8 地址"))
    assertFalse(html.contains("代理地址"))
    assertFalse(html.contains("输入 APK 地址"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: FAIL because the control page still includes multiple page bodies together.

- [ ] **Step 3: Move page bodies into focused files**

Create page builders:

```kotlin
fun buildPlayPageContent(): String = ...
fun buildCachePageContent(
    proxySettings: ProxySettingsState,
    cacheStats: HlsSegmentCacheStats,
    playbackDiagnostics: PlaybackDiagnosticsSnapshot,
): String = ...
fun buildLogsPageContent(logs: List<String>): String = ...
fun buildSettingsPageContent(proxySettings: ProxySettingsState): String = ...
```

In `ControlPage.kt`, keep only shared low-level helpers still used across pages:

- escaping helpers
- byte formatting
- TS diagnostics rendering helpers that belong to cache page

If helper ownership becomes clear, move cache-page-specific helpers into `ControlPageCache.kt`.

- [ ] **Step 4: Recompose current routes through shell + body**

In `LocalHlsProxy.kt`, render each page with only its body:

```kotlin
val bodyHtml = when (page) {
    AdminPage.PLAY -> buildPlayPageContent()
    AdminPage.CACHE -> buildCachePageContent(...)
    AdminPage.LOGS -> buildLogsPageContent(getLogs())
    AdminPage.SETTINGS -> buildSettingsPageContent(proxySettingsStore.load())
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPagePlay.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt
git commit -m "feat: split admin page bodies by route"
```

## Task 3: Scope auto-refresh scripts to cache and logs pages only

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageScripts.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] **Step 1: Write the failing script-scope test**

Add tests asserting:

- `缓存` page includes diagnostics refresh script but not logs refresh controls
- `日志` page includes logs refresh script but not diagnostics refresh controls
- `播放` / `设置` pages include neither auto-refresh block

```kotlin
assertTrue(cacheHtml.contains("refreshCachePage"))
assertFalse(cacheHtml.contains("refreshLogs()"))
assertTrue(logsHtml.contains("refreshLogs()"))
assertFalse(logsHtml.contains("refreshCachePage"))
assertFalse(settingsHtml.contains("diagnostics-refresh-status"))
assertFalse(settingsHtml.contains("logs-refresh-status"))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: FAIL because scripts are still injected globally.

- [ ] **Step 3: Split scripts by responsibility**

In `ControlPageScripts.kt`, add:

```kotlin
fun buildCommonFormScript(): String = ...
fun buildCachePageScript(): String = ...
fun buildLogsPageScript(): String = ...
```

Keep the cache-page script limited to:

- diagnostics panel refresh
- cache page refresh status text

Keep the logs-page script limited to:

- logs refresh
- monitor log controls

In `buildAdminShell(...)`, inject:

```kotlin
<script>
${buildCommonFormScript()}
$pageScript
</script>
```

Where `pageScript` is chosen by route and empty for `播放` / `设置`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPageScripts.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt
git commit -m "feat: scope admin auto-refresh by page"
```

## Task 4: Apply tool-console layout and move configuration into settings

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

- [ ] **Step 1: Write the failing layout test**

Add tests asserting:

- nav labels are `播放 / 缓存 / 日志 / 设置`
- current page nav item is highlighted
- settings page contains proxy/cache/update controls
- cache page no longer contains settings forms

```kotlin
assertTrue(html.contains(">播放</a>"))
assertTrue(html.contains(">缓存</a>"))
assertTrue(html.contains(">日志</a>"))
assertTrue(html.contains(">设置</a>"))
assertTrue(settingsHtml.contains("代理地址"))
assertTrue(settingsHtml.contains("输入 APK 地址"))
assertFalse(cacheHtml.contains("代理地址"))
assertFalse(cacheHtml.contains("输入 APK 地址"))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest
```

Expected: FAIL because nav labels and page ownership still reflect the old structure.

- [ ] **Step 3: Apply compact tool-console layout**

In `ControlPageShell.kt`, implement the shared layout with:

- fixed left nav
- compact page title area
- status summary banner
- restrained borders and spacing
- no oversized cards or decorative elements

Use a current-page CSS class:

```kotlin
val currentCss = if (item == page) "nav-current" else ""
```

In `ControlPageSettings.kt`, move:

- proxy add/select/delete forms
- prefetch concurrency
- detailed diagnostics toggle
- clear cache
- APK update form

Out of cache page and into settings page.

In `ControlPageCache.kt`, keep only:

- TS health view
- segment detail
- current playback info
- cache/prefetch metrics
- upstream diagnostics
- recent segments table

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: PASS

- [ ] **Step 5: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageCache.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageLogs.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageSettings.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageScripts.kt app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "feat: convert admin ui into paged tool console"
```

## Self-Review

Spec coverage check:

- True four-page admin layout: covered by Tasks 1 and 2.
- Tool-console visual structure: covered by Task 4.
- Cache/logs refresh only: covered by Task 3.
- Settings page consolidation: covered by Task 4.
- File splitting instead of growing `ControlPage.kt`: covered by Tasks 1-3.

Placeholder scan:

- No `TODO`, `TBD`, or implied future work remain.
- Each task includes concrete tests and exact commands.

Type consistency check:

- Uses one shared `AdminPage` enum across shell, routing, tests, and page builders.
- Keeps the route name for logs explicit as `/logs-page` to avoid collision with the existing raw `/logs` endpoint.
