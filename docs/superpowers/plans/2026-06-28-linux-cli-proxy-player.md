# Linux CLI Proxy Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Linux command-line entrypoint that can open an `m3u8` URL through the same sessionized proxy/cache pipeline currently used by Android.

**Architecture:** Extract Android-free proxy/session logic into a shared JVM module, keep Android as a thin shell around that core, and add a `:desktop` CLI shell that starts the core proxy and optionally launches `mpv`/`vlc`.

**Tech Stack:** Gradle multi-module Kotlin, JVM 17, OkHttp, JUnit 4, Android app module, desktop application plugin.

---

## File Structure

### New modules

- Create: `core/build.gradle.kts`
- Create: `desktop/build.gradle.kts`

### Gradle wiring

- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

### Shared core source files

- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/ProxySettings.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/UpstreamHttp.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/HlsProxyTransforms.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/ManifestPlanner.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackSession.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackSessionManager.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/PlaybackTelemetryBridge.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/SessionAssetStore.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/SessionDownloader.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/SessionLocalServer.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/SessionTimeline.kt`

### Desktop shell source files

- Create: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt`
- Create: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt`
- Create: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncher.kt`

### Android shell updates

- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`
- Modify: any Android file importing moved shared classes, as discovered during compilation

### Tests

- Create: `core/src/test/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyTest.kt`
- Create: `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgsTest.kt`
- Create: `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncherTest.kt`
- Modify: existing tests under `app/src/test/java/labs/newrapaw/dlna/probe/**`

---

### Task 1: Add Gradle modules for shared core and desktop CLI

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `core/build.gradle.kts`
- Create: `desktop/build.gradle.kts`
- Test: `./gradlew tasks`

- [ ] **Step 1: Write the failing integration expectation**

Add this test file:

`desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgsTest.kt`

```kotlin
package labs.newrapaw.dlna.probe.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCliArgsTest {
    @Test
    fun parsesPlayCommandWithUrl() {
        val args = DesktopCliArgs.parse(listOf("play", "https://example.com/video.m3u8"))

        assertEquals("https://example.com/video.m3u8", args.url)
        assertEquals(PlayerMode.AUTO, args.playerMode)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopCliArgsTest.parsesPlayCommandWithUrl'
```

Expected: FAIL because `:desktop` module does not exist yet.

- [ ] **Step 3: Add new modules and root plugin wiring**

Update `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PawCast"
include(":app")
include(":core")
include(":desktop")
```

Update `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("application") apply false
}
```

Create `core/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
```

Create `desktop/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("labs.newrapaw.dlna.probe.desktop.DesktopCliKt")
}

dependencies {
    implementation(project(":core"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Add minimal parser skeleton to make Gradle compile**

Create `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

enum class PlayerMode {
    AUTO,
    MPV,
    VLC,
    NONE,
}

data class DesktopCliArgs(
    val url: String,
    val playerMode: PlayerMode,
) {
    companion object {
        fun parse(args: List<String>): DesktopCliArgs {
            require(args.size >= 2 && args.first() == "play") { "Usage: play <m3u8-url>" }
            return DesktopCliArgs(
                url = args[1],
                playerMode = PlayerMode.AUTO,
            )
        }
    }
}
```

Create `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

fun main(args: Array<String>) {
    DesktopCliArgs.parse(args.toList())
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopCliArgsTest.parsesPlayCommandWithUrl'
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts core/build.gradle.kts desktop/build.gradle.kts desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgsTest.kt
git commit -m "build: add core and desktop modules"
```

### Task 2: Move Android-free settings and helper classes into shared core

**Files:**
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/ProxySettings.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/UpstreamHttp.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/HlsProxyTransforms.kt`
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/UpstreamHttpTest.kt`
- Test: shared/app unit tests above

- [ ] **Step 1: Write failing shared-core test for proxy settings**

Create `core/src/test/java/labs/newrapaw/dlna/probe/core/ProxySettingsTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxySettingsTest {
    @Test
    fun inMemoryStoreRoundTripsSelectedProxy() {
        val proxy = ProxyConfig(
            id = "http-localhost-8080",
            type = ProxyType.HTTP,
            host = "127.0.0.1",
            port = 8080,
        )
        val store = InMemoryProxySettingsStore()
        store.save(
            ProxySettingsState()
                .add(proxy)
                .select(proxy.id)
                .withUpstreamMode(UpstreamMode.PROXY_ONLY),
        )

        val loaded = store.load()

        assertEquals(proxy.id, loaded.selectedProxyId)
        assertEquals(proxy, loaded.selectedProxy())
    }

    @Test
    fun directSelectionDoesNotRequireAndroidStorage() {
        val loaded = InMemoryProxySettingsStore().load()

        assertEquals(ProxySettingsState.DIRECT_PROXY_ID, loaded.selectedProxyId)
        assertNull(loaded.selectedProxy())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.ProxySettingsTest'
```

Expected: FAIL because shared core settings classes do not exist yet.

- [ ] **Step 3: Create Android-free shared settings and helper files**

Create `core/src/main/java/labs/newrapaw/dlna/probe/core/ProxySettings.kt` with:

```kotlin
package labs.newrapaw.dlna.probe.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

enum class ProxyType(val scheme: String) {
    HTTP("http"),
    SOCKS5("socks5"),
    SOCKS5H("socks5h"),
}

enum class UpstreamMode {
    PROXY_ONLY,
    RACE_DIRECT_AND_PROXY,
}

data class ProxyConfig(
    val id: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
) {
    fun displayUrl(): String = "${type.scheme}://$host:$port"

    fun toJavaProxy(): Proxy {
        val address = when (type) {
            ProxyType.SOCKS5H -> InetSocketAddress.createUnresolved(host, port)
            ProxyType.HTTP, ProxyType.SOCKS5 -> InetSocketAddress(host, port)
        }
        val proxyType = if (type == ProxyType.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
        return Proxy(proxyType, address)
    }
}

data class ProxySettingsState(
    val proxies: List<ProxyConfig> = emptyList(),
    val selectedProxyId: String = DIRECT_PROXY_ID,
    val upstreamMode: UpstreamMode = UpstreamMode.PROXY_ONLY,
    val prefetchConcurrency: Int = DEFAULT_PREFETCH_CONCURRENCY,
    val detailedDiagnosticsEnabled: Boolean = false,
) {
    fun normalized(): ProxySettingsState =
        copy(prefetchConcurrency = prefetchConcurrency.coerceIn(MIN_PREFETCH_CONCURRENCY, MAX_PREFETCH_CONCURRENCY))

    fun selectedProxy(): ProxyConfig? =
        proxies.firstOrNull { it.id == selectedProxyId }

    fun add(config: ProxyConfig): ProxySettingsState =
        copy(proxies = (proxies.filterNot { it.id == config.id } + config))

    fun select(proxyId: String): ProxySettingsState =
        if (proxyId == DIRECT_PROXY_ID || proxies.any { it.id == proxyId }) {
            copy(
                selectedProxyId = proxyId,
                upstreamMode = if (proxyId == DIRECT_PROXY_ID) UpstreamMode.PROXY_ONLY else upstreamMode,
            )
        } else {
            this
        }

    fun withUpstreamMode(mode: UpstreamMode): ProxySettingsState =
        copy(upstreamMode = if (selectedProxy() == null) UpstreamMode.PROXY_ONLY else mode)

    fun remove(proxyId: String): ProxySettingsState {
        val nextProxies = proxies.filterNot { it.id == proxyId }
        val nextSelected = if (selectedProxyId == proxyId) DIRECT_PROXY_ID else selectedProxyId
        return copy(
            proxies = nextProxies,
            selectedProxyId = nextSelected,
            upstreamMode = if (nextSelected == DIRECT_PROXY_ID) UpstreamMode.PROXY_ONLY else upstreamMode,
        )
    }

    companion object {
        const val DIRECT_PROXY_ID = "direct"
        const val DEFAULT_PREFETCH_CONCURRENCY = 3
        const val MIN_PREFETCH_CONCURRENCY = 1
        const val MAX_PREFETCH_CONCURRENCY = 6
    }
}

interface ProxySettingsStore {
    fun load(): ProxySettingsState
    fun save(state: ProxySettingsState)
}

class InMemoryProxySettingsStore(
    initialState: ProxySettingsState = ProxySettingsState(),
) : ProxySettingsStore {
    private var state = initialState

    override fun load(): ProxySettingsState = state

    override fun save(state: ProxySettingsState) {
        this.state = state
    }
}

fun parseProxyConfig(value: String): ProxyConfig? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val type = proxyTypeFromScheme(uri.scheme.orEmpty().lowercase()) ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val port = uri.port.takeIf { it in 1..65535 } ?: return null
    return ProxyConfig(
        id = proxyId(type, host, port),
        type = type,
        host = host,
        port = port,
    )
}

private fun proxyTypeFromScheme(scheme: String): ProxyType? =
    ProxyType.entries.firstOrNull { it.scheme == scheme }

private fun proxyId(type: ProxyType, host: String, port: Int): String =
    "${type.scheme}-${host.replace(Regex(\"\"\"[^A-Za-z0-9_.-]\"\"\"), "_")}-$port"
```

Copy current Android-free implementations into:

- `core/src/main/java/labs/newrapaw/dlna/probe/core/UpstreamHttp.kt`
- `core/src/main/java/labs/newrapaw/dlna/probe/core/HlsProxyTransforms.kt`
- `core/src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt`

Only change package names during the move.

- [ ] **Step 4: Turn Android app `ProxyConfig.kt` into Android-only adapter**

Replace `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt` with:

```kotlin
package labs.newrapaw.dlna.probe

import android.content.SharedPreferences
import labs.newrapaw.dlna.probe.core.InMemoryProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxyConfig
import labs.newrapaw.dlna.probe.core.ProxySettingsState
import labs.newrapaw.dlna.probe.core.ProxySettingsStore
import labs.newrapaw.dlna.probe.core.ProxyType
import labs.newrapaw.dlna.probe.core.UpstreamMode
import labs.newrapaw.dlna.probe.core.parseProxyConfig
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesProxySettingsStore(
    private val preferences: SharedPreferences,
) : ProxySettingsStore {
    override fun load(): ProxySettingsState {
        val raw = preferences.getString(KEY_STATE, null) ?: return ProxySettingsState()
        return runCatching { decodeState(JSONObject(raw)) }.getOrDefault(ProxySettingsState())
    }

    override fun save(state: ProxySettingsState) {
        preferences.edit().putString(KEY_STATE, encodeState(state).toString()).apply()
    }

    private fun encodeState(state: ProxySettingsState): JSONObject {
        val proxies = JSONArray()
        state.proxies.forEach { proxy ->
            proxies.put(
                JSONObject()
                    .put("id", proxy.id)
                    .put("type", proxy.type.scheme)
                    .put("host", proxy.host)
                    .put("port", proxy.port),
            )
        }
        return JSONObject()
            .put("selectedProxyId", state.selectedProxyId)
            .put("upstreamMode", state.upstreamMode.name)
            .put("prefetchConcurrency", state.normalized().prefetchConcurrency)
            .put("detailedDiagnosticsEnabled", state.detailedDiagnosticsEnabled)
            .put("proxies", proxies)
    }

    private fun decodeState(json: JSONObject): ProxySettingsState {
        val proxiesJson = json.optJSONArray("proxies") ?: JSONArray()
        val proxies = buildList {
            for (index in 0 until proxiesJson.length()) {
                val item = proxiesJson.optJSONObject(index) ?: continue
                val type = ProxyType.entries.firstOrNull { it.scheme == item.optString("type") } ?: continue
                val host = item.optString("host").takeIf { it.isNotBlank() } ?: continue
                val port = item.optInt("port").takeIf { it in 1..65535 } ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: "${type.scheme}-${host}-$port"
                add(ProxyConfig(id, type, host, port))
            }
        }
        val selected = json.optString("selectedProxyId", ProxySettingsState.DIRECT_PROXY_ID)
        val mode = runCatching {
            UpstreamMode.valueOf(json.optString("upstreamMode", UpstreamMode.PROXY_ONLY.name))
        }.getOrDefault(UpstreamMode.PROXY_ONLY)
        val prefetchConcurrency = json.optInt("prefetchConcurrency", ProxySettingsState.DEFAULT_PREFETCH_CONCURRENCY)
        val detailedDiagnosticsEnabled = json.optBoolean("detailedDiagnosticsEnabled", false)
        return ProxySettingsState(
            proxies = proxies,
            selectedProxyId = selected,
            upstreamMode = mode,
            prefetchConcurrency = prefetchConcurrency,
            detailedDiagnosticsEnabled = detailedDiagnosticsEnabled,
        ).select(selected).withUpstreamMode(mode).normalized()
    }

    private companion object {
        const val KEY_STATE = "proxy_settings_state"
    }
}

typealias AndroidInMemoryProxySettingsStore = InMemoryProxySettingsStore
```

- [ ] **Step 5: Update imports in app tests and source**

Adjust imports in:

- `app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/UpstreamHttpTest.kt`

Use `labs.newrapaw.dlna.probe.core.*` for moved types and helpers.

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.ProxySettingsTest'
./gradlew :app:testDebugUnitTest --tests 'labs.newrapaw.dlna.probe.ProxyConfigTest'
./gradlew :app:testDebugUnitTest --tests 'labs.newrapaw.dlna.probe.HlsProxyTransformsTest'
./gradlew :app:testDebugUnitTest --tests 'labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest'
./gradlew :app:testDebugUnitTest --tests 'labs.newrapaw.dlna.probe.UpstreamHttpTest'
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/labs/newrapaw/dlna/probe/core/ProxySettings.kt core/src/main/java/labs/newrapaw/dlna/probe/core/UpstreamHttp.kt core/src/main/java/labs/newrapaw/dlna/probe/core/HlsProxyTransforms.kt core/src/main/java/labs/newrapaw/dlna/probe/core/PlaybackDiagnostics.kt core/src/test/java/labs/newrapaw/dlna/probe/core/ProxySettingsTest.kt app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt app/src/test/java/labs/newrapaw/dlna/probe/UpstreamHttpTest.kt
git commit -m "refactor: move proxy helpers into shared core"
```

### Task 3: Move session model and local serving classes into shared core

**Files:**
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/session/*.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/session/*.kt`
- Test: moved session tests in `:core`

- [ ] **Step 1: Write failing shared-core session test**

Create `core/src/test/java/labs/newrapaw/dlna/probe/core/session/SessionLocalServerTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe.core.session

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLocalServerTest {
    @Test
    fun localManifestUsesSessionAssetRoutes() {
        val server = SessionLocalServer()
        val manifest = server.buildManifest(
            sessionId = "session-1",
            slots = listOf(
                TimelineSlot(
                    slotIndex = 0,
                    startMs = 0,
                    endMs = 4000,
                    videoAssetId = "video-0",
                    prerequisiteAssetIds = listOf("init-0"),
                ),
            ),
        )

        assertTrue(manifest.contains("/session/session-1/asset/video-0"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.SessionLocalServerTest.localManifestUsesSessionAssetRoutes'
```

Expected: FAIL because shared session classes do not exist yet.

- [ ] **Step 3: Copy Android-free session classes into `:core`**

Copy these files into `core/src/main/java/labs/newrapaw/dlna/probe/core/session/` and change only package/imports:

- `ManifestPlanner.kt`
- `PlaybackSession.kt`
- `PlaybackSessionManager.kt`
- `PlaybackTelemetryBridge.kt`
- `SessionAssetStore.kt`
- `SessionDownloader.kt`
- `SessionLocalServer.kt`
- `SessionTimeline.kt`

Keep class names and behavior identical.

- [ ] **Step 4: Move session unit tests to `:core`**

Copy these tests into `core/src/test/java/labs/newrapaw/dlna/probe/core/session/` and update imports/packages:

- `SessionLocalServerTest.kt`
- `PlaybackTelemetryBridgeTest.kt`
- `SessionAssetStoreTest.kt`
- `ManifestPlannerTest.kt`
- `SessionDownloaderTest.kt`
- `PlaybackSessionManagerTest.kt`

- [ ] **Step 5: Run tests to verify they pass in `:core`**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.SessionLocalServerTest'
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.ManifestPlannerTest'
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.SessionDownloaderTest'
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.PlaybackTelemetryBridgeTest'
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.SessionAssetStoreTest'
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.session.PlaybackSessionManagerTest'
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/labs/newrapaw/dlna/probe/core/session core/src/test/java/labs/newrapaw/dlna/probe/core/session
git commit -m "refactor: move session classes into shared core"
```

### Task 4: Extract Android-free proxy core from `LocalHlsProxy`

**Files:**
- Create: `core/src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt`
- Create: `core/src/test/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

- [ ] **Step 1: Write failing shared-core proxy test**

Create `core/src/test/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe.core

import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocalHlsProxyTest {
    @Test
    fun proxyStartsAndExposesBaseUrl() {
        val proxy = CoreLocalHlsProxy(
            client = OkHttpClient(),
            log = {},
        )

        proxy.start()
        try {
            assertTrue(proxy.baseUrl.startsWith("http://127.0.0.1:"))
        } finally {
            proxy.close()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.CoreLocalHlsProxyTest.proxyStartsAndExposesBaseUrl'
```

Expected: FAIL because `CoreLocalHlsProxy` does not exist yet.

- [ ] **Step 3: Create `CoreLocalHlsProxy` by extracting Android-free logic**

Create `core/src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt` by moving the Android-free parts of `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`:

Keep:

- server socket lifecycle
- session preparation
- local `/session/...` serving
- prefetching
- diagnostics state
- upstream fetching
- asset request/response logging

Remove from the shared core:

- DLNA renderer controller
- Android control pages
- APK update hooks
- Android-only control endpoints

Expose minimal API:

```kotlin
class CoreLocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
    private val proxySettingsStore: ProxySettingsStore = InMemoryProxySettingsStore(),
    private val sessionAssetRootDir: File = File(requireNotNull(System.getProperty("java.io.tmpdir"))).resolve("pawcast-session-assets"),
) : Closeable {
    val port: Int
    val baseUrl: String

    fun start()
    fun openSession(sourceUrl: String): ActiveSessionInfo
    fun activeSessionInfo(): ActiveSessionInfo?
    fun diagnosticsSnapshot(): PlaybackDiagnosticsSnapshot
}
```

- [ ] **Step 4: Turn Android `LocalHlsProxy` into a shell around `CoreLocalHlsProxy`**

Refactor `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt` so it:

- owns Android-specific routes and callbacks
- delegates sessionized proxy mechanics to `CoreLocalHlsProxy`
- preserves existing Android behaviors and tests

The Android shell may temporarily wrap `CoreLocalHlsProxy` compositionally rather than via inheritance.

- [ ] **Step 5: Run targeted tests**

Run:

```bash
./gradlew :core:test --tests 'labs.newrapaw.dlna.probe.core.CoreLocalHlsProxyTest'
./gradlew :app:testDebugUnitTest --tests 'labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest'
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxy.kt core/src/test/java/labs/newrapaw/dlna/probe/core/CoreLocalHlsProxyTest.kt app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "refactor: extract shared local hls proxy core"
```

### Task 5: Add desktop player launcher and argument parsing

**Files:**
- Create: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncher.kt`
- Modify: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt`
- Create: `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncherTest.kt`

- [ ] **Step 1: Write failing player launcher tests**

Create `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncherTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPlayerLauncherTest {
    @Test
    fun choosesMpvFirstInAutoMode() {
        val launcher = DesktopPlayerLauncher(
            commandExists = { it == "mpv" || it == "vlc" },
            spawn = { command -> command },
        )

        val result = launcher.launch(PlayerMode.AUTO, "http://127.0.0.1:43000/session/1/manifest.m3u8")

        assertEquals(listOf("mpv", "http://127.0.0.1:43000/session/1/manifest.m3u8"), result)
    }

    @Test
    fun fallsBackToVlcWhenMpvMissing() {
        val launcher = DesktopPlayerLauncher(
            commandExists = { it == "vlc" },
            spawn = { command -> command },
        )

        val result = launcher.launch(PlayerMode.AUTO, "http://127.0.0.1:43000/session/1/manifest.m3u8")

        assertEquals(listOf("vlc", "http://127.0.0.1:43000/session/1/manifest.m3u8"), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopPlayerLauncherTest'
```

Expected: FAIL because launcher does not exist yet.

- [ ] **Step 3: Expand CLI args and add launcher**

Update `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

data class DesktopCliArgs(
    val url: String,
    val playerMode: PlayerMode,
) {
    companion object {
        fun parse(args: List<String>): DesktopCliArgs {
            require(args.isNotEmpty() && args.first() == "play") { "Usage: play <m3u8-url> [--player=auto|mpv|vlc|none]" }
            val url = args.getOrNull(1) ?: error("Missing m3u8 url")
            val playerMode = args
                .drop(2)
                .firstOrNull { it.startsWith("--player=") }
                ?.substringAfter("=")
                ?.let(::parsePlayerMode)
                ?: PlayerMode.AUTO
            return DesktopCliArgs(url = url, playerMode = playerMode)
        }

        private fun parsePlayerMode(value: String): PlayerMode =
            when (value.lowercase()) {
                "auto" -> PlayerMode.AUTO
                "mpv" -> PlayerMode.MPV
                "vlc" -> PlayerMode.VLC
                "none" -> PlayerMode.NONE
                else -> error("Unknown player mode: $value")
            }
    }
}
```

Create `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncher.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

class DesktopPlayerLauncher(
    private val commandExists: (String) -> Boolean = { command ->
        runCatching {
            ProcessBuilder("sh", "-lc", "command -v $command >/dev/null 2>&1").start().waitFor() == 0
        }.getOrDefault(false)
    },
    private val spawn: (List<String>) -> List<String> = { command ->
        ProcessBuilder(command).inheritIO().start()
        command
    },
) {
    fun launch(mode: PlayerMode, url: String): List<String>? =
        when (mode) {
            PlayerMode.NONE -> null
            PlayerMode.MPV -> launchIfAvailable("mpv", url)
            PlayerMode.VLC -> launchIfAvailable("vlc", url)
            PlayerMode.AUTO -> launchIfAvailable("mpv", url) ?: launchIfAvailable("vlc", url)
        }

    private fun launchIfAvailable(command: String, url: String): List<String>? {
        if (!commandExists(command)) return null
        return spawn(listOf(command, url))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopPlayerLauncherTest'
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopCliArgsTest'
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncher.kt desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopPlayerLauncherTest.kt desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgsTest.kt
git commit -m "feat: add desktop cli parsing and player launcher"
```

### Task 6: Wire desktop CLI to shared proxy core

**Files:**
- Modify: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt`
- Modify: `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCliArgs.kt`
- Modify: `desktop/build.gradle.kts`
- Test: `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliIntegrationTest.kt`

- [ ] **Step 1: Write failing CLI integration test**

Create `desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliIntegrationTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

import labs.newrapaw.dlna.probe.core.ActiveSessionInfo
import labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCliIntegrationTest {
    @Test
    fun printsLocalPlaybackUrlAfterOpeningSession() {
        val output = StringBuilder()
        val app = DesktopCliApp(
            proxyFactory = {
                FakeDesktopProxy(
                    localManifestUrl = "http://127.0.0.1:41000/session/session-1/manifest.m3u8",
                )
            },
            playerLauncher = DesktopPlayerLauncher(commandExists = { false }, spawn = { it }),
            printLine = { output.appendLine(it) },
        )

        app.run(DesktopCliArgs(url = "https://example.com/video.m3u8", playerMode = PlayerMode.NONE))

        assertTrue(output.toString().contains("http://127.0.0.1:41000/session/session-1/manifest.m3u8"))
    }
}

private class FakeDesktopProxy(
    private val localManifestUrl: String,
) : DesktopProxy {
    override fun start() = Unit
    override fun close() = Unit
    override fun openSession(sourceUrl: String): ActiveSessionInfo =
        ActiveSessionInfo(
            sessionId = "session-1",
            status = labs.newrapaw.dlna.probe.core.session.PlaybackSessionStatus.READY,
            sourceUrl = sourceUrl,
            localManifestUrl = localManifestUrl,
            slotCount = 1,
            assetCount = 1,
            prepared = true,
            pendingPrefetchAssetIds = emptyList(),
        )
    override fun diagnosticsSnapshot(): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot.empty()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopCliIntegrationTest.printsLocalPlaybackUrlAfterOpeningSession'
```

Expected: FAIL because CLI app wiring does not exist yet.

- [ ] **Step 3: Add CLI app and desktop proxy abstraction**

Update `desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt`:

```kotlin
package labs.newrapaw.dlna.probe.desktop

import labs.newrapaw.dlna.probe.core.CoreLocalHlsProxy
import okhttp3.OkHttpClient
import java.io.File
import kotlin.concurrent.thread

interface DesktopProxy : AutoCloseable {
    fun start()
    fun openSession(sourceUrl: String): labs.newrapaw.dlna.probe.core.ActiveSessionInfo
    fun diagnosticsSnapshot(): labs.newrapaw.dlna.probe.core.PlaybackDiagnosticsSnapshot
}

class DesktopCliApp(
    private val proxyFactory: () -> DesktopProxy = {
        object : DesktopProxy {
            private val proxy = CoreLocalHlsProxy(
                client = OkHttpClient(),
                log = ::println,
                sessionAssetRootDir = File(System.getProperty("java.io.tmpdir")).resolve("pawcast-desktop-session-assets"),
            )

            override fun start() = proxy.start()
            override fun close() = proxy.close()
            override fun openSession(sourceUrl: String) = proxy.openSession(sourceUrl)
            override fun diagnosticsSnapshot() = proxy.diagnosticsSnapshot()
        }
    },
    private val playerLauncher: DesktopPlayerLauncher = DesktopPlayerLauncher(),
    private val printLine: (String) -> Unit = ::println,
) {
    fun run(args: DesktopCliArgs) {
        proxyFactory().use { proxy ->
            proxy.start()
            val session = proxy.openSession(args.url)
            printLine("Source: ${session.sourceUrl}")
            printLine("Local playback URL: ${session.localManifestUrl}")
            playerLauncher.launch(args.playerMode, session.localManifestUrl)
            while (true) Thread.sleep(1000)
        }
    }
}

fun main(args: Array<String>) {
    DesktopCliApp().run(DesktopCliArgs.parse(args.toList()))
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :desktop:test --tests 'labs.newrapaw.dlna.probe.desktop.DesktopCliIntegrationTest'
```

Expected: PASS

- [ ] **Step 5: Manual smoke run**

Run:

```bash
./gradlew :desktop:run --args="play https://example.com/video.m3u8 --player=none"
```

Expected: process prints `Local playback URL:` and stays alive until interrupted.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/labs/newrapaw/dlna/probe/desktop/DesktopCli.kt desktop/src/test/java/labs/newrapaw/dlna/probe/desktop/DesktopCliIntegrationTest.kt
git commit -m "feat: wire desktop cli to shared proxy core"
```

### Task 7: Reconnect Android app to shared core and run full verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: Android source files impacted by moved imports
- Test: app/core/desktop test suites

- [ ] **Step 1: Add `:core` dependency to Android**

Update `app/build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Update Android imports and shell usage**

Adjust Android source to import moved core classes from:

- `labs.newrapaw.dlna.probe.core.*`
- `labs.newrapaw.dlna.probe.core.session.*`

Ensure Android-only classes still compile:

- `MainActivity.kt`
- `RendererForegroundService.kt`
- `ControlPage*.kt`
- `DlnaRendererController.kt`

- [ ] **Step 3: Run full verification**

Run:

```bash
./gradlew :core:test
./gradlew :desktop:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected: all pass

- [ ] **Step 4: Manual desktop smoke with real URL**

Run:

```bash
./gradlew :desktop:run --args="play <real-m3u8-url> --player=none"
```

Expected:

- prints local playback URL
- logs session asset request/response lines
- stays alive until interrupted

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/labs/newrapaw/dlna/probe core desktop
git commit -m "feat: add linux cli proxy player"
```

---

## Self-Review

- Spec coverage:
  - shared core extraction: Tasks 2, 3, 4
  - Linux CLI entrypoint: Tasks 1, 5, 6
  - external player launch: Task 5
  - Android compatibility after extraction: Task 7
- Placeholder scan:
  - No `TODO`/`TBD`/“similar to above” placeholders remain
- Type consistency:
  - Shared core package is `labs.newrapaw.dlna.probe.core`
  - Session package is `labs.newrapaw.dlna.probe.core.session`
  - Desktop shell depends on `CoreLocalHlsProxy`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-28-linux-cli-proxy-player.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
