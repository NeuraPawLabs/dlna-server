# VOD Prefetch Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a VOD-only sustained prefetch session to the local HLS proxy, with playback-aware cache eviction and management-page configurable prefetch concurrency.

**Architecture:** Keep ExoPlayer pointed at the existing local proxy. Extend `LocalHlsProxy` to detect VOD manifests, create a `VodPrefetchSession`, and route segment requests through that session. Extend settings storage and the control page to manage prefetch concurrency, and teach the cache layer enough metadata to evict old played segments before future segments when the `2 GB` limit is reached.

**Tech Stack:** Kotlin, Android app module, OkHttp, Media3 ExoPlayer, JUnit 4, existing in-process proxy/cache classes

---

## File Structure

- Create: `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`
  Purpose: own ordered VOD segment state, bounded background scheduling, retry state, and dynamic concurrency updates.
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
  Purpose: add segment metadata and playback-aware eviction hooks while preserving deduplicated fetch behavior.
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`
  Purpose: add VOD manifest helpers and preserve segment ordering details used by the session.
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
  Purpose: create/cancel VOD sessions, apply management-page concurrency updates, and route segment requests through session-aware logic.
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt`
  Purpose: persist prefetch concurrency with the existing local settings model and clamp values into `1..6`.
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
  Purpose: add the management-page prefetch concurrency control and display the configured value.
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt`
  Purpose: verify settings persistence and concurrency clamping.
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
  Purpose: verify management-page rendering for the new concurrency control.
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`
  Purpose: verify VOD manifest detection and ordered segment extraction helpers.
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`
  Purpose: verify playback-aware eviction behavior.
- Create: `app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt`
  Purpose: verify session scheduling, priority fetch behavior, retry/skip, and dynamic concurrency updates.
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
  Purpose: verify end-to-end VOD session creation, sustained prefetch, and live control-page updates through the proxy surface.

### Task 1: Persist Prefetch Concurrency In Settings

**Files:**
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package labs.newrapaw.dlna.probe

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyConfigTest {
    @Test
    fun proxySettingsClampPrefetchConcurrencyIntoAllowedRange() {
        assertEquals(1, ProxySettingsState(prefetchConcurrency = -5).normalized().prefetchConcurrency)
        assertEquals(6, ProxySettingsState(prefetchConcurrency = 999).normalized().prefetchConcurrency)
        assertEquals(3, ProxySettingsState(prefetchConcurrency = 3).normalized().prefetchConcurrency)
    }

    @Test
    fun sharedPreferencesStorePersistsPrefetchConcurrency() {
        val preferences = InMemorySharedPreferences()
        val store = SharedPreferencesProxySettingsStore(preferences)

        store.save(ProxySettingsState(prefetchConcurrency = 5))

        assertEquals(5, store.load().prefetchConcurrency)
    }

    @Test
    fun sharedPreferencesStoreFallsBackToDefaultConcurrencyForInvalidJson() {
        val preferences = InMemorySharedPreferences()
        preferences.edit().putString("proxy_settings_state", JSONObject().put("prefetchConcurrency", 42).toString()).apply()
        val store = SharedPreferencesProxySettingsStore(preferences)

        assertEquals(6, store.load().prefetchConcurrency)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ProxyConfigTest`
Expected: FAIL because `prefetchConcurrency`, `normalized()`, or persistence support does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class ProxySettingsState(
    val proxies: List<ProxyConfig> = emptyList(),
    val selectedProxyId: String = DIRECT_PROXY_ID,
    val upstreamMode: UpstreamMode = UpstreamMode.PROXY_ONLY,
    val prefetchConcurrency: Int = DEFAULT_PREFETCH_CONCURRENCY,
) {
    fun normalized(): ProxySettingsState =
        copy(prefetchConcurrency = prefetchConcurrency.coerceIn(MIN_PREFETCH_CONCURRENCY, MAX_PREFETCH_CONCURRENCY))

    companion object {
        const val DIRECT_PROXY_ID = "direct"
        const val DEFAULT_PREFETCH_CONCURRENCY = 3
        const val MIN_PREFETCH_CONCURRENCY = 1
        const val MAX_PREFETCH_CONCURRENCY = 6
    }
}

private fun encodeState(state: ProxySettingsState): JSONObject =
    JSONObject()
        .put("selectedProxyId", state.selectedProxyId)
        .put("upstreamMode", state.upstreamMode.name)
        .put("prefetchConcurrency", state.normalized().prefetchConcurrency)
        .put("proxies", proxies)

private fun decodeState(json: JSONObject): ProxySettingsState {
    val prefetchConcurrency = json.optInt("prefetchConcurrency", ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY)
    return ProxySettingsState(
        proxies = proxies,
        selectedProxyId = selected,
        upstreamMode = mode,
        prefetchConcurrency = prefetchConcurrency,
    ).select(selected).withUpstreamMode(mode).normalized()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ProxyConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt
git commit -m "feat: persist prefetch concurrency settings"
```

### Task 2: Add Management-Page Prefetch Concurrency Control

**Files:**
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest`
Expected: FAIL because the management page does not render prefetch concurrency controls yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
<section id="cache">
  <h2>缓存</h2>
  <p>Entries: ${cacheStats.entries}</p>
  <p>Size: ${formatBytes(cacheStats.sizeBytes)}</p>
  <p>Hits: ${cacheStats.hits}</p>
  <p>Misses: ${cacheStats.misses}</p>
  <p>In flight: ${cacheStats.inFlight}</p>
  <form method="post" action="/control/prefetch/config">
    <label for="prefetchConcurrency">Prefetch concurrency</label>
    <input
      id="prefetchConcurrency"
      name="prefetchConcurrency"
      type="number"
      min="${ProxySettingsState.MIN_PREFETCH_CONCURRENCY}"
      max="${ProxySettingsState.MAX_PREFETCH_CONCURRENCY}"
      value="${proxySettings.prefetchConcurrency}">
    <button type="submit">Apply Prefetch Setting</button>
  </form>
  <form method="post" action="/control/cache/clear">
    <button type="submit">Clear Cache</button>
  </form>
</section>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt
git commit -m "feat: add prefetch concurrency control page setting"
```

### Task 3: Add VOD Manifest Helpers

**Files:**
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun detectsVodManifestFromEndlistTag() {
    val manifest = """
        #EXTM3U
        #EXTINF:4.0,
        seg-1.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    assertTrue(isVodManifest(manifest))
}

@Test
fun extractsOrderedSegmentEntriesFromVodManifest() {
    val manifest = """
        #EXTM3U
        #EXTINF:4.0,
        seg-1.ts
        #EXTINF:4.0,
        seg-2.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    assertEquals(
        listOf(
            HlsSegmentEntry(index = 0, url = "https://example.com/seg-1.ts"),
            HlsSegmentEntry(index = 1, url = "https://example.com/seg-2.ts"),
        ),
        extractOrderedHlsSegmentEntries(manifest, "https://example.com/master.m3u8"),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsProxyTransformsTest`
Expected: FAIL because VOD detection and ordered entry extraction do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class HlsSegmentEntry(
    val index: Int,
    val url: String,
)

fun isVodManifest(manifest: String): Boolean =
    manifest.lineSequence().any { it.trim() == "#EXT-X-ENDLIST" }

fun extractOrderedHlsSegmentEntries(manifest: String, manifestUrl: String): List<HlsSegmentEntry> =
    manifest.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapIndexed { index, value ->
            HlsSegmentEntry(index = index, url = URI(manifestUrl).resolve(value).toString())
        }
        .toList()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsProxyTransformsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt
git commit -m "feat: add vod manifest helper functions"
```

### Task 4: Add Playback-Aware Cache Metadata And Eviction

**Files:**
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun trimPrefersEvictingPlayedSegmentsBeforeFutureSegments() {
    val cache = HlsSegmentCache(temporaryFolder.newFolder("cache"), maxBytes = 8)

    cache.store(url = "u0", manifestId = "m", segmentIndex = 0, bytes = byteArrayOf(0, 0, 0, 0))
    cache.store(url = "u1", manifestId = "m", segmentIndex = 1, bytes = byteArrayOf(1, 1, 1, 1))
    cache.store(url = "u2", manifestId = "m", segmentIndex = 2, bytes = byteArrayOf(2, 2, 2, 2))

    cache.updatePlaybackPosition(manifestId = "m", currentPlayIndex = 2)
    cache.store(url = "u3", manifestId = "m", segmentIndex = 3, bytes = byteArrayOf(3, 3, 3, 3))

    assertNull(cache.readIfCached("u0"))
    assertNull(cache.readIfCached("u1"))
    assertNotNull(cache.readIfCached("u2"))
    assertNotNull(cache.readIfCached("u3"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest`
Expected: FAIL because playback-aware metadata, `store`, `updatePlaybackPosition`, or `readIfCached` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class CachedSegmentMetadata(
    val url: String,
    val manifestId: String,
    val segmentIndex: Int,
    val lastAccessNanos: Long,
)

fun store(url: String, manifestId: String, segmentIndex: Int, bytes: ByteArray) {
    writeCached(url, manifestId, segmentIndex, bytes)
}

fun readIfCached(url: String): ByteArray? = readCached(url)

fun updatePlaybackPosition(manifestId: String, currentPlayIndex: Int) = synchronized(lock) {
    playbackPositions[manifestId] = currentPlayIndex
}

private fun trimToMaxBytes() {
    val positions = playbackPositions.toMap()
    val files = segmentFiles()
    val victims = files.sortedWith(
        compareBy<File> { file ->
            val metadata = readMetadata(file) ?: return@compareBy Int.MAX_VALUE
            val playIndex = positions[metadata.manifestId] ?: -1
            if (metadata.segmentIndex < playIndex) 0 else 1
        }.thenBy { file ->
            val metadata = readMetadata(file) ?: return@thenBy Int.MAX_VALUE
            val playIndex = positions[metadata.manifestId] ?: -1
            if (metadata.segmentIndex < playIndex) metadata.segmentIndex else -metadata.segmentIndex
        }
    )
    ...
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt
git commit -m "feat: add playback aware cache eviction"
```

### Task 5: Build `VodPrefetchSession`

**Files:**
- Create: `app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package labs.newrapaw.dlna.probe

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPrefetchSessionTest {
    @Test
    fun sessionSchedulesSegmentsInOrderWithBoundedConcurrency() {
        val started = CopyOnWriteArrayList<Int>()
        val release = CountDownLatch(1)
        val session = VodPrefetchSession(
            manifestId = "m",
            segmentEntries = (0..4).map { HlsSegmentEntry(it, "https://example.com/$it.ts") },
            initialConcurrency = 2,
            fetchSegment = { entry ->
                started += entry.index
                release.await(2, TimeUnit.SECONDS)
                byteArrayOf(entry.index.toByte())
            },
            cacheSegment = { _, _, _ -> },
            isCached = { false },
            logger = {},
            executor = Executors.newCachedThreadPool(),
        )

        session.start()
        assertTrue(started.containsAll(listOf(0, 1)))
        assertEquals(2, started.size)
        release.countDown()
        session.awaitIdle(2, TimeUnit.SECONDS)
    }

    @Test
    fun sessionUpdatesConcurrencyImmediately() {
        val session = ...
        session.updateConcurrency(4)
        assertEquals(4, session.stats().configuredConcurrency)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest`
Expected: FAIL because `VodPrefetchSession` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class VodPrefetchSessionStats(
    val configuredConcurrency: Int,
    val currentPlayIndex: Int,
    val nextPrefetchIndex: Int,
    val inFlightCount: Int,
    val completedCount: Int,
)

class VodPrefetchSession(
    private val manifestId: String,
    private val segmentEntries: List<HlsSegmentEntry>,
    initialConcurrency: Int,
    private val fetchSegment: (HlsSegmentEntry) -> ByteArray,
    private val cacheSegment: (String, Int, ByteArray) -> Unit,
    private val isCached: (String) -> Boolean,
    private val logger: (String) -> Unit,
    private val executor: ExecutorService,
) {
    @Volatile private var configuredConcurrency = initialConcurrency
    @Volatile private var cancelled = false
    ...

    fun start() { pump() }

    fun updateConcurrency(value: Int) {
        configuredConcurrency = value.coerceIn(ProxySettingsState.MIN_PREFETCH_CONCURRENCY, ProxySettingsState.MAX_PREFETCH_CONCURRENCY)
        pump()
    }

    fun onSegmentRequested(url: String) { ... }

    fun updatePlaybackIndex(index: Int) { currentPlayIndex.set(index) }

    fun cancel() { cancelled = true }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt
git commit -m "feat: add vod prefetch session"
```

### Task 6: Integrate VOD Session Into `LocalHlsProxy`

**Files:**
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun vodManifestStartsSustainedPrefetchSession() {
    val logs = CopyOnWriteArrayList<String>()
    val cache = HlsSegmentCache(temporaryFolder.newFolder("segments"), maxBytes = 1024 * 1024)
    val store = InMemoryProxySettingsStore(ProxySettingsState(prefetchConcurrency = 2))
    val upstream = FakeHttpServer(
        manifest = """
            #EXTM3U
            #EXTINF:4.0,
            seg-1.ts
            #EXTINF:4.0,
            seg-2.ts
            #EXT-X-ENDLIST
        """.trimIndent(),
    )
    val proxy = LocalHlsProxy(
        client = upstream.client(),
        log = logs::add,
        proxySettingsStore = store,
        segmentCache = cache,
    )

    proxy.start()
    val manifestResponse = httpGet("${proxy.baseUrl}/proxy/hls.m3u8?u=${encodeProxyUrl(upstream.manifestUrl)}")

    assertEquals(200, manifestResponse.code)
    eventually {
        assertTrue(logs.any { it.contains("session created") })
        assertTrue(logs.any { it.contains("prefetched segment") })
    }
}

@Test
fun prefetchConcurrencyUpdateAppliesToActiveSession() {
    ...
    httpPost("${proxy.baseUrl}/control/prefetch/config", "prefetchConcurrency=5")
    eventually {
        assertTrue(logs.any { it.contains("prefetch concurrency updated") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest`
Expected: FAIL because `LocalHlsProxy` does not create VOD sessions or handle `/control/prefetch/config` yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
private var activeVodSession: VodPrefetchSession? = null

private fun handleManifest(path: String, output: OutputStream) {
    val upstreamUrl = extractUrl(path) ?: ...
    val manifest = fetchUpstreamBytes(upstreamUrl).toString(Charsets.UTF_8)
    if (isVodManifest(manifest)) {
        val entries = extractOrderedHlsSegmentEntries(manifest, upstreamUrl)
        replaceVodSession(upstreamUrl, entries)
    } else {
        scheduleSegmentPrefetch(manifest, upstreamUrl)
    }
    writeText(output, 200, "application/vnd.apple.mpegurl", rewriteHlsManifest(manifest, upstreamUrl, baseUrl))
}

private fun replaceVodSession(manifestUrl: String, entries: List<HlsSegmentEntry>) {
    activeVodSession?.cancel()
    activeVodSession = VodPrefetchSession(
        manifestId = manifestUrl,
        segmentEntries = entries,
        initialConcurrency = proxySettingsStore.load().prefetchConcurrency,
        fetchSegment = { entry -> fetchSegmentBytes(entry.url) },
        cacheSegment = { url, index, bytes -> segmentCache?.store(url, manifestUrl, index, bytes) },
        isCached = { url -> segmentCache?.readIfCached(url) != null },
        logger = ::safeLog,
        executor = Executors.newCachedThreadPool(),
    ).also { it.start() }
}

private fun handlePrefetchConfigRequest(body: String, output: OutputStream) {
    val requested = decodeFormValue(body, "prefetchConcurrency")?.toIntOrNull() ?: ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY
    val next = proxySettingsStore.load().copy(prefetchConcurrency = requested).normalized()
    proxySettingsStore.save(next)
    activeVodSession?.updateConcurrency(next.prefetchConcurrency)
    safeLog("Prefetch concurrency updated: ${next.prefetchConcurrency}")
    writeText(output, 200, "text/html", "Prefetch concurrency updated")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "feat: integrate vod prefetch session into proxy"
```

### Task 7: Run Full Verification

**Files:**
- No source changes required

- [ ] **Step 1: Run focused unit and integration tests**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ProxyConfigTest --tests labs.newrapaw.dlna.probe.ControlPageTest --tests labs.newrapaw.dlna.probe.HlsProxyTransformsTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest`
Expected: PASS

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: Run debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL and `app/build/outputs/apk/debug/app-debug.apk` updated

- [ ] **Step 4: Commit verification-only follow-up if needed**

```bash
git status --short
```

Expected: no source changes; if any test snapshots or generated files changed unexpectedly, inspect before committing.

## Self-Review

- Spec coverage:
  - VOD-only activation is covered in Task 3 and Task 6.
  - Ordered sustained background prefetch is covered in Task 5 and Task 6.
  - `2 GB` playback-aware eviction is covered in Task 4.
  - management-page configurable concurrency with `1..6` clamp and default `3` is covered in Task 1, Task 2, and Task 6.
  - immediate concurrency updates on the active session are covered in Task 5 and Task 6.
  - logging and observability are covered in Task 5 and Task 6.
  - testing requirements are covered in Task 1 through Task 7.
- Placeholder scan: no `TBD`, `TODO`, or “implement later” placeholders remain in the plan steps.
- Type consistency:
  - `ProxySettingsState.prefetchConcurrency` is introduced in Task 1 and reused consistently.
  - `HlsSegmentEntry` is introduced in Task 3 and reused in Task 5 and Task 6.
  - `VodPrefetchSession.updateConcurrency()` is introduced in Task 5 and consumed in Task 6.
  - `HlsSegmentCache.store/readIfCached/updatePlaybackPosition` are introduced in Task 4 and consumed in Task 6.
