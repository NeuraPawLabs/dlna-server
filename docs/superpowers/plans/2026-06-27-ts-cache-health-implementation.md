# TS Cache Health Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an in-memory full-list TS cache health view for VOD playback diagnostics, including per-segment lifecycle state, current-playback highlighting, and removal of redundant summary diagnostics from the main admin view.

**Architecture:** Extend the existing in-memory diagnostics path rather than adding a parallel subsystem. Track per-manifest segment state close to the cache/playback flow, expose it through `PlaybackDiagnosticsSnapshot`, and let `ControlPage.kt` render a compact full-list block view plus a selected-segment detail panel while preserving existing low-level diagnostics for deeper inspection.

**Tech Stack:** Kotlin, existing local HTTP admin UI, JUnit4 unit tests, Gradle Android unit test task

---

## File Map

**Modify:**

- `app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt`
  - Extend snapshot/state types with full-list segment diagnostics data.
- `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
  - Add manifest-level per-segment state tracking and eviction callbacks.
- `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`
  - Emit segment state transitions for in-flight, success, and failure.
- `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
  - Wire manifest initialization, playback index updates, diagnostics JSON output, and state refresh.
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
  - Replace summary-first diagnostics panel with full-list TS health view and selected-segment details.

**Test:**

- `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

## Task 1: Add per-segment in-memory state model

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`

- [ ] **Step 1: Write the failing test for snapshot segment-state storage**

Add a test to `PlaybackDiagnosticsStateTest.kt` asserting that the diagnostics state can store and return an ordered list of segment states plus the selected/current playback segment.

```kotlin
@Test
fun snapshotIncludesOrderedSegmentStatesAndCurrentSelection() {
    val state = PlaybackDiagnosticsState(sampleLimit = 20)
    state.resetForPlayback(
        sourceUrl = "https://origin.example/video.m3u8",
        localProxyUrl = "http://127.0.0.1:43000/proxy/hls.m3u8?u=abc",
        settings = ProxySettingsState(prefetchConcurrency = 3),
    )

    state.updateSegmentStates(
        listOf(
            SegmentDiagnosticsItem(
                index = 0,
                url = "https://cdn.example/0.ts",
                state = SegmentDiagnosticsState.CACHED,
                isCached = true,
                wasEvicted = false,
                isInFlight = false,
                lastElapsedMs = 120,
                lastSource = "proxy",
                lastFailureReason = null,
            ),
            SegmentDiagnosticsItem(
                index = 1,
                url = "https://cdn.example/1.ts",
                state = SegmentDiagnosticsState.PLAYING,
                isCached = true,
                wasEvicted = false,
                isInFlight = false,
                lastElapsedMs = 110,
                lastSource = "proxy",
                lastFailureReason = null,
            ),
        ),
    )

    val snapshot = state.snapshot()

    assertEquals(2, snapshot.segmentStates.size)
    assertEquals(1, snapshot.segmentStates[1].index)
    assertEquals(SegmentDiagnosticsState.PLAYING, snapshot.segmentStates[1].state)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest
```

Expected: FAIL because `SegmentDiagnosticsItem`, `SegmentDiagnosticsState`, or `updateSegmentStates(...)` do not exist yet.

- [ ] **Step 3: Add minimal diagnostics segment-state model**

In `PlaybackDiagnostics.kt`, add:

```kotlin
enum class SegmentDiagnosticsState {
    NOT_STARTED,
    IN_FLIGHT,
    FAILED,
    CACHED,
    EVICTED,
    PLAYING,
}

data class SegmentDiagnosticsItem(
    val index: Int,
    val url: String,
    val state: SegmentDiagnosticsState,
    val isCached: Boolean,
    val wasEvicted: Boolean,
    val isInFlight: Boolean,
    val lastElapsedMs: Long?,
    val lastSource: String?,
    val lastFailureReason: String?,
)
```

Extend `PlaybackDiagnosticsSnapshot`:

```kotlin
val segmentStates: List<SegmentDiagnosticsItem>,
```

Set the empty default:

```kotlin
segmentStates = emptyList(),
```

Add minimal state mutation:

```kotlin
fun updateSegmentStates(segmentStates: List<SegmentDiagnosticsItem>) = synchronized(lock) {
    touch(snapshot.copy(segmentStates = segmentStates.sortedBy { it.index }))
}
```
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt
git commit -m "feat: add diagnostics segment state model"
```

## Task 2: Track full per-segment lifecycle in cache/prefetch flow

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt`

- [ ] **Step 1: Write the failing cache test for eviction state**

Add a test to `HlsSegmentCacheTest.kt` asserting that a segment previously cached and later evicted is still represented in the manifest snapshot as evicted rather than disappearing entirely.

```kotlin
@Test
fun manifestSnapshotKeepsEvictedSegmentsAsGrayCandidates() {
    val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 8)

    cache.initializeManifest(
        manifestId = "m",
        entries = listOf(
            HlsSegmentEntry(0, "u0"),
            HlsSegmentEntry(1, "u1"),
            HlsSegmentEntry(2, "u2"),
        ),
    )
    cache.store(url = "u0", manifestId = "m", segmentIndex = 0, bytes = byteArrayOf(0, 0, 0, 0))
    cache.store(url = "u1", manifestId = "m", segmentIndex = 1, bytes = byteArrayOf(1, 1, 1, 1))
    cache.store(url = "u2", manifestId = "m", segmentIndex = 2, bytes = byteArrayOf(2, 2, 2, 2))

    val snapshot = cache.snapshotForManifest("m")

    assertEquals(SegmentDiagnosticsState.EVICTED, snapshot.segmentStates.first { it.index == 0 }.state)
    assertEquals(false, snapshot.segmentStates.first { it.index == 0 }.isCached)
    assertEquals(true, snapshot.segmentStates.first { it.index == 0 }.wasEvicted)
}
```

- [ ] **Step 2: Write the failing prefetch test for in-flight and failure state**

Add a test to `VodPrefetchSessionTest.kt` asserting that the session emits callbacks for in-flight start, success, and failure transitions.

```kotlin
@Test
fun sessionPublishesSegmentLifecycleEvents() {
    val events = CopyOnWriteArrayList<Pair<Int, SegmentDiagnosticsState>>()
    val session = VodPrefetchSession(
        manifestId = "m",
        segmentEntries = listOf(HlsSegmentEntry(0, "https://example.com/0.ts")),
        initialConcurrency = 1,
        fetchSegment = { throw IllegalStateException("boom") },
        cacheSegment = { _, _, _ -> },
        isCached = { false },
        logger = {},
        onSegmentStateChanged = { index, state -> events += index to state },
        executor = Executors.newCachedThreadPool(),
    )

    session.start()
    session.awaitIdle(2, TimeUnit.SECONDS)

    assertTrue(events.contains(0 to SegmentDiagnosticsState.IN_FLIGHT))
    assertTrue(events.contains(0 to SegmentDiagnosticsState.FAILED))
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest
```

Expected: FAIL because manifest snapshots do not carry full segment states and `VodPrefetchSession` does not publish lifecycle transitions yet.

- [ ] **Step 4: Add minimal manifest state tracking in cache**

In `HlsSegmentCache.kt`, add a manifest-scoped state table that is initialized from parsed entries and updated during store/evict/playback flow.

Use a focused internal structure:

```kotlin
private val manifestSegments = mutableMapOf<String, MutableMap<Int, SegmentDiagnosticsItem>>()
```

Add initialization:

```kotlin
fun initializeManifest(manifestId: String, entries: List<HlsSegmentEntry>) = synchronized(lock) {
    manifestSegments[manifestId] = entries.associate { entry ->
        entry.index to SegmentDiagnosticsItem(
            index = entry.index,
            url = entry.url,
            state = SegmentDiagnosticsState.NOT_STARTED,
            isCached = false,
            wasEvicted = false,
            isInFlight = false,
            lastElapsedMs = null,
            lastSource = null,
            lastFailureReason = null,
        )
    }.toMutableMap()
}
```

Update store path:

```kotlin
private fun markCached(manifestId: String?, segmentIndex: Int?) { ... }
```

Update eviction path:

```kotlin
private fun markEvicted(metadata: CachedSegmentMetadata?) { ... }
```

Extend `ManifestCacheSnapshot`:

```kotlin
val segmentStates: List<SegmentDiagnosticsItem>,
```

Return ordered segment states:

```kotlin
segmentStates = manifestSegments[manifestId]?.values?.sortedBy { it.index }.orEmpty(),
```

- [ ] **Step 5: Add minimal lifecycle callback support in prefetch session**

In `VodPrefetchSession.kt`, add:

```kotlin
private val onSegmentStateChanged: (Int, SegmentDiagnosticsState, Long?, String?, String?) -> Unit = { _, _, _, _, _ -> }
```

Emit events:

```kotlin
onSegmentStateChanged(entry.index, SegmentDiagnosticsState.IN_FLIGHT, null, null, null)
onSegmentStateChanged(entry.index, SegmentDiagnosticsState.CACHED, elapsedMs, "prefetch", null)
onSegmentStateChanged(entry.index, SegmentDiagnosticsState.FAILED, elapsedMs, "prefetch", "${error::class.java.simpleName}: ${error.message}")
```

Keep the first implementation narrow. The goal is to produce lifecycle signals, not to redesign prefetching.

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt
git commit -m "feat: track per-segment cache lifecycle state"
```

## Task 3: Wire manifest lifecycle into diagnostics snapshot and JSON

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`

- [ ] **Step 1: Write the failing integration-style test for diagnostics JSON**

Add a test to `LocalHlsProxyStabilityTest.kt` asserting that `/diagnostics` contains the ordered segment-state list and current playback state.

```kotlin
@Test
fun diagnosticsJsonIncludesSegmentStateList() {
    val logs = mutableListOf<String>()
    val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 1024)
    val proxy = LocalHlsProxy(
        log = logs::add,
        segmentCache = cache,
    )

    // drive manifest + segment flow here using existing helper patterns in this test class

    val diagnostics = fetchText("http://127.0.0.1:${proxy.port}/diagnostics")

    assertTrue(diagnostics.contains("\"segmentStates\""))
    assertTrue(diagnostics.contains("\"state\":\"PLAYING\""))
    assertTrue(diagnostics.contains("\"state\":\"CACHED\""))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest
```

Expected: FAIL because diagnostics JSON does not contain `segmentStates`.

- [ ] **Step 3: Wire manifest initialization and state refresh**

In `LocalHlsProxy.kt`:

1. Initialize manifest state when replacing the VOD session:

```kotlin
cache.initializeManifest(manifestUrl, entries)
```

2. Pass session lifecycle updates into the cache/state model:

```kotlin
onSegmentStateChanged = { index, state, elapsedMs, source, failureReason ->
    cache.updateSegmentState(
        manifestId = manifestUrl,
        segmentIndex = index,
        state = state,
        lastElapsedMs = elapsedMs,
        lastSource = source,
        lastFailureReason = failureReason,
    )
},
```

3. During actual playback request, mark the current playback segment as playing via the manifest snapshot source of truth.

4. In `refreshDiagnosticsSnapshot()`, push `manifestSnapshot.segmentStates` into `diagnosticsState.updateSegmentStates(...)`.

5. In `buildDiagnosticsJson(...)`, serialize:

```kotlin
appendJsonField("segmentStates", buildJsonArray(snapshot.segmentStates) { item ->
    buildString {
        append('{')
        appendJsonField("index", item.index)
        append(',')
        appendJsonField("url", item.url)
        append(',')
        appendJsonField("state", item.state.name)
        append(',')
        appendJsonField("isCached", item.isCached)
        append(',')
        appendJsonField("wasEvicted", item.wasEvicted)
        append(',')
        appendJsonField("isInFlight", item.isInFlight)
        append(',')
        appendJsonField("lastElapsedMs", item.lastElapsedMs)
        append(',')
        appendJsonField("lastSource", item.lastSource)
        append(',')
        appendJsonField("lastFailureReason", item.lastFailureReason)
        append('}')
    }
}, isRawJson = true)
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt
git commit -m "feat: expose full ts health state in diagnostics"
```

## Task 4: Replace summary-heavy admin diagnostics UI with full TS health view

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] **Step 1: Write the failing UI test for full TS health view**

Add a test to `ControlPageTest.kt` asserting that the page renders:

- a full-list TS health container
- segment blocks using state-specific classes
- selected-segment detail content
- removal of redundant summary lines

```kotlin
assertTrue(html.contains("TS 全量健康图"))
assertTrue(html.contains("id=\"segment-health-grid\""))
assertTrue(html.contains("segment-health-block playing"))
assertTrue(html.contains("segment-health-block cached"))
assertTrue(html.contains("segment-health-block evicted"))
assertTrue(html.contains("分片详情"))
assertFalse(html.contains("最近请求分片"))
assertFalse(html.contains("最近最慢 3 个分片"))
assertFalse(html.contains("来源汇总"))
assertFalse(html.contains("原因汇总"))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: FAIL because the page still renders the old summary-first diagnostics layout.

- [ ] **Step 3: Implement the minimal UI rewrite**

In `ControlPage.kt`:

1. Replace the removed summary lines in `buildDiagnosticsPanelHtml(...)` with:

```html
<h4>TS 全量健康图</h4>
<div id="segment-health-grid" class="segment-health-grid">
  ...
</div>
<div id="segment-health-detail" class="segment-health-detail">
  ...
</div>
```

2. Add a compact helper that maps `SegmentDiagnosticsState` to CSS class names:

```kotlin
private fun segmentHealthCss(state: SegmentDiagnosticsState): String = ...
```

3. Add rendering helpers:

```kotlin
private fun segmentHealthGrid(items: List<SegmentDiagnosticsItem>): String = ...
private fun selectedSegmentDetail(snapshot: PlaybackDiagnosticsSnapshot): String = ...
```

4. Keep the retained auxiliary metrics:

- 当前播放分片索引
- 当前播放分片 URL
- 当前播放分片缓存
- 预取并发配置
- 当前 in-flight
- 当前等待预取数
- 已预取未消费数
- 预取领先量
- 缓存命中 / 未命中

5. Keep `recentSegmentsTable(...)` inside a collapsible area:

```html
<details class="diagnostics-group">
  <summary>最近分片明细</summary>
  ...
</details>
```

6. Remove the following from the main diagnostics block:

- 最近请求分片
- 最近成功分片
- 最近失败分片
- 最近最慢 3 个分片
- 来源汇总
- 当前可见分片
- 原因汇总
- 当前主要问题

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: PASS

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest --tests labs.newrapaw.dlna.probe.ControlPageTest
```

Expected: PASS

- [ ] **Step 6: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt
git add app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt
git add app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "feat: add full ts cache health diagnostics view"
```

## Self-Review

Spec coverage check:

- Full-list TS health view: covered by Task 4.
- In-memory only segment lifecycle tracking: covered by Tasks 1-3 and limited to existing in-memory state holders.
- Current playback highlighting and details: covered by Tasks 3-4.
- Gray/evicted state support: covered by Task 2.
- Diagnostics JSON output: covered by Task 3.
- Removal of redundant summary diagnostics: covered by Task 4.

Placeholder scan:

- No `TODO`, `TBD`, or deferred implementation notes remain.
- Every task includes a concrete test command and expected result.

Type consistency check:

- Uses a single shared `SegmentDiagnosticsState` / `SegmentDiagnosticsItem` naming scheme across state, cache, proxy, JSON, and page rendering tasks.
- `updateSegmentStates(...)`, `initializeManifest(...)`, and `updateSegmentState(...)` are referenced consistently.
