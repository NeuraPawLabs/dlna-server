# Honor Smart Screen Playback Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android probe APK at the repository root that verifies Honor Smart Screen installation and ExoPlayer playback through a local HLS proxy that strips PNG wrappers from HLS segments.

**Architecture:** The existing Linux prototype remains at the repository root. The Android probe is an independent Kotlin Android app at the repository root with a simple TV-friendly Activity, AndroidX Media3 ExoPlayer, and a lightweight localhost HLS proxy. Pure Kotlin unit tests cover URL encoding, manifest rewrite, and segment wrapper stripping; device testing verifies sideload and playback.

**Tech Stack:** Kotlin, Android Gradle Plugin, AndroidX Media3 ExoPlayer, JUnit, OkHttp, built-in `HttpServer`-style socket handling through a small embedded Kotlin server.

---

## File Structure

- `settings.gradle.kts`: Android project settings.
- `build.gradle.kts`: root Android Gradle plugin configuration.
- `gradle.properties`: AndroidX and Kotlin settings.
- `app/build.gradle.kts`: app module dependencies and SDK versions.
- `app/src/main/AndroidManifest.xml`: permissions, Activity declaration, and network security config reference.
- `app/src/main/res/xml/network_security_config.xml`: cleartext allowlist for localhost only.
- `app/src/main/res/values/strings.xml`: app name and UI labels.
- `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`: probe UI, ExoPlayer lifecycle, logs, and playback actions.
- `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`: localhost manifest and segment proxy.
- `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`: pure URL encoding, manifest rewriting, and PNG wrapper stripping.
- `app/src/main/java/labs/newrapaw/dlna/probe/ProbeConfig.kt`: test URL configuration and defaults.
- `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`: JVM unit tests for pure proxy transforms.
- `docs/android-honor-probe.md`: build, sideload, and device verification instructions.

### Task 1: Android Toolchain Check And Ignore Rules

**Files:**
- Modify: `.gitignore`
- Create: `docs/android-honor-probe.md`

- [ ] **Step 1: Add Android build outputs to `.gitignore`**

Append these lines to `.gitignore`:

```gitignore
.gradle/
build/
app/build/
local.properties
*.apk
```

- [ ] **Step 2: Write device verification document**

Create `docs/android-honor-probe.md` with:

```markdown
# Honor Smart Screen Probe

This probe validates APK installation and playback on Honor Smart Screen before the DLNA receiver is ported.

## Local Build Requirements

- JDK 17
- Android SDK with platform 35
- Android build-tools

Check the local toolchain:

```bash
java -version
echo "$ANDROID_HOME"
ls "$ANDROID_HOME/platforms/android-35"
```

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Device Verification

1. Install the APK on Honor Smart Screen by U disk or the system-supported sideload flow.
2. Launch `PawCast`.
3. Enter a fresh m3u8 URL if the built-in field is empty.
4. Press `Play Test Stream`.
5. Confirm the status changes from `Idle` to `Buffering` and then `Ready`.
6. Confirm video and audio play.
7. Press `Stop` and then `Play Test Stream` again.

If playback fails, photograph the visible log panel and copy any logcat output if available.
```

- [ ] **Step 3: Run toolchain check**

Run:

```bash
java -version
echo "$ANDROID_HOME"
```

Expected: JDK 17 and an Android SDK path are available. If either is missing, record the blocker in the final report and continue with source generation only.

- [ ] **Step 4: Commit**

Run:

```bash
git add .gitignore docs/android-honor-probe.md
git commit -m "docs: add honor smart screen probe instructions"
```

### Task 2: Android Gradle Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Write Android project settings**

Create `settings.gradle.kts`:

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
```

- [ ] **Step 2: Write root Gradle config**

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

- [ ] **Step 3: Write Gradle properties**

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Write app module config**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "labs.newrapaw.dlna.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "labs.newrapaw.dlna.probe"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 5: Write manifest and resources**

Create `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

Create `app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PawCast</string>
</resources>
```

Create `app/src/main/res/values/styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:windowFullscreen">true</item>
    </style>
</resources>
```

- [ ] **Step 6: Run Gradle tasks if toolchain exists**

Run:

```bash
./gradlew :app:tasks
```

Expected: Gradle lists app tasks. If no wrapper exists yet, run system `gradle wrapper --gradle-version 8.10.2` from the repository root first when `gradle` is available.

- [ ] **Step 7: Commit**

Run:

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "chore: scaffold android playback probe"
```

### Task 3: HLS Proxy Pure Transforms

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`
- Create: `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`

- [ ] **Step 1: Write failing transform tests**

Create `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`:

```kotlin
package labs.newrapaw.dlna.probe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsProxyTransformsTest {
    @Test
    fun encodeDecodeRoundTripDoesNotLeakExtension() {
        val original = "https://cdn.example/path/seg0.png?token=abc"
        val encoded = encodeProxyUrl(original)

        assertFalse(encoded.contains(".png"))
        assertEquals(original, decodeProxyUrl(encoded))
    }

    @Test
    fun rewriteManifestRoutesAbsoluteAndRelativeSegmentsThroughProxy() {
        val manifest = """
            #EXTM3U
            #EXTINF:6.0,
            https://cdn.example/path/seg0.png
            #EXTINF:6.0,
            relative/seg1.png
            #EXT-X-ENDLIST
        """.trimIndent()

        val rewritten = rewriteHlsManifest(
            manifest = manifest,
            manifestUrl = "https://origin.example/video/index.m3u8?token=abc",
            proxyBaseUrl = "http://127.0.0.1:49152",
        )

        assertTrue(rewritten.contains("http://127.0.0.1:49152/proxy/segment.ts?u="))
        assertFalse(rewritten.contains("cdn.example"))
        assertFalse(rewritten.lines().filter { !it.startsWith("#") }.any { it.contains(".png") })
    }

    @Test
    fun stripPngWrapperReturnsBytesStartingAtTsSync() {
        val wrapper = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x00,
        )
        val ts = ByteArray(188 * 3) { 0xff.toByte() }
        ts[0] = 0x47
        ts[188] = 0x47
        ts[376] = 0x47

        assertArrayEquals(ts, stripPngWrapperFromSegment(wrapper + ts))
    }

    @Test
    fun stripPngWrapperPassesThroughNormalSegments() {
        val ts = ByteArray(188 * 3) { 0xff.toByte() }
        ts[0] = 0x47
        ts[188] = 0x47
        ts[376] = 0x47

        assertArrayEquals(ts, stripPngWrapperFromSegment(ts))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsProxyTransformsTest
```

Expected: tests fail because transform functions do not exist.

- [ ] **Step 3: Implement pure transforms**

Create `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`:

```kotlin
package labs.newrapaw.dlna.probe

import android.util.Base64
import java.net.URI

fun encodeProxyUrl(url: String): String =
    Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

fun decodeProxyUrl(encoded: String): String =
    String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)

fun isLikelyHlsManifest(url: String): Boolean =
    Regex("""\.m3u8(?:$|[/?#&=;%])""", RegexOption.IGNORE_CASE).containsMatchIn(url)

fun resolvePlayableUri(uri: String, proxyBaseUrl: String): String =
    if (isLikelyHlsManifest(uri)) "$proxyBaseUrl/proxy/hls.m3u8?u=${encodeProxyUrl(uri)}" else uri

fun rewriteHlsManifest(manifest: String, manifestUrl: String, proxyBaseUrl: String): String =
    manifest.lineSequence()
        .map { line -> rewriteManifestLine(line, manifestUrl, proxyBaseUrl) }
        .joinToString("\n")

fun stripPngWrapperFromSegment(segment: ByteArray): ByteArray {
    val offset = findMpegTsOffset(segment)
    return if (offset > 0) segment.copyOfRange(offset, segment.size) else segment
}

private fun rewriteManifestLine(line: String, manifestUrl: String, proxyBaseUrl: String): String {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return line

    val segmentUrl = URI(manifestUrl).resolve(trimmed).toString()
    return "$proxyBaseUrl/proxy/segment.ts?u=${encodeProxyUrl(segmentUrl)}"
}

private fun findMpegTsOffset(segment: ByteArray): Int {
    var offset = 0
    while (offset < segment.size - 376) {
        if (
            segment[offset] == 0x47.toByte() &&
            segment[offset + 188] == 0x47.toByte() &&
            segment[offset + 376] == 0x47.toByte()
        ) {
            return offset
        }
        offset += 1
    }
    return 0
}
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsProxyTransformsTest
```

Expected: tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt
git commit -m "feat: add android hls proxy transforms"
```

### Task 4: Local HLS Proxy Runtime

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`

- [ ] **Step 1: Implement local proxy server**

Create `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`:

```kotlin
package labs.newrapaw.dlna.probe

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalHlsProxy(
    private val client: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun start() {
        if (running.get()) return
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        running.set(true)
        executor.execute {
            log("Proxy listening at $baseUrl")
            while (running.get()) {
                val socket = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                executor.execute { handle(socket) }
            }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            while (reader.readLine().orEmpty().isNotEmpty()) {
                // Consume headers.
            }

            when {
                path.startsWith("/proxy/hls.m3u8") -> handleManifest(path, it.getOutputStream())
                path.startsWith("/proxy/segment.ts") -> handleSegment(path, it.getOutputStream())
                else -> writeText(it.getOutputStream(), 404, "text/plain", "Not Found")
            }
        }
    }

    private fun handleManifest(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        val response = client.newCall(Request.Builder().url(upstreamUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                writeText(output, it.code, "text/plain", "Upstream manifest failed: ${it.code}")
                return
            }
            val manifest = it.body?.string().orEmpty()
            writeText(output, 200, "application/vnd.apple.mpegurl", rewriteHlsManifest(manifest, upstreamUrl, baseUrl))
        }
    }

    private fun handleSegment(path: String, output: OutputStream) {
        val upstreamUrl = extractUrl(path)
        if (upstreamUrl == null) {
            writeText(output, 400, "text/plain", "Missing url")
            return
        }

        val response = client.newCall(Request.Builder().url(upstreamUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                writeText(output, it.code, "text/plain", "Upstream segment failed: ${it.code}")
                return
            }
            val bytes = it.body?.bytes() ?: ByteArray(0)
            writeBytes(output, 200, "video/mp2t", stripPngWrapperFromSegment(bytes))
        }
    }

    private fun extractUrl(path: String): String? {
        val query = path.substringAfter("?", "")
        val params = query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
        return params["u"]?.let(::decodeProxyUrl) ?: params["url"]
    }

    private fun writeText(output: OutputStream, status: Int, contentType: String, body: String) {
        writeBytes(output, status, "$contentType; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(output: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val reason = if (status in 200..299) "OK" else "Error"
        output.write("HTTP/1.1 $status $reason\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        output.write("Content-Length: ${body.size}\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }
}
```

- [ ] **Step 2: Build compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation succeeds.

- [ ] **Step 3: Commit**

Run:

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt
git commit -m "feat: add android local hls proxy"
```

### Task 5: Probe Activity And ExoPlayer Playback

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/ProbeConfig.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`

- [ ] **Step 1: Write probe config**

Create `app/src/main/java/labs/newrapaw/dlna/probe/ProbeConfig.kt`:

```kotlin
package labs.newrapaw.dlna.probe

object ProbeConfig {
    const val DefaultTestUrl: String = ""
}
```

- [ ] **Step 2: Write Activity UI and playback lifecycle**

Create `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`:

```kotlin
package labs.newrapaw.dlna.probe

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var proxy: LocalHlsProxy
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var urlInput: EditText
    private val logs = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()
        proxy = LocalHlsProxy(OkHttpClient(), ::appendLog)
        proxy.start()

        setContentView(buildContentView())
        appendLog("IP: ${localIpAddress()}")
        appendLog("Proxy: ${proxy.baseUrl}")
        setStatus("Idle")
    }

    override fun onDestroy() {
        player.release()
        proxy.close()
        super.onDestroy()
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(TextView(this).apply {
            text = "PawCast"
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        statusView = TextView(this).apply { textSize = 18f }
        root.addView(statusView)

        urlInput = EditText(this).apply {
            hint = "m3u8 test URL"
            setSingleLine(false)
            minLines = 2
            setText(ProbeConfig.DefaultTestUrl)
        }
        root.addView(urlInput)

        val playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(playerView)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttonRow.addView(Button(this).apply {
            text = "Play Test Stream"
            setOnClickListener { playTestStream() }
        })
        buttonRow.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopPlayback() }
        })
        root.addView(buttonRow)

        logView = TextView(this).apply {
            textSize = 14f
            movementMethod = ScrollingMovementMethod()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                220,
            )
        }
        root.addView(logView)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val label = when (playbackState) {
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_BUFFERING -> "Buffering"
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
                setStatus(label)
                appendLog("Player: $label")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                setStatus("Error")
                appendLog("Player error: ${error.errorCodeName}: ${error.message}")
            }
        })

        return root
    }

    private fun playTestStream() {
        val source = urlInput.text.toString().trim()
        if (source.isEmpty()) {
            appendLog("Enter a test m3u8 URL first")
            return
        }

        val playable = resolvePlayableUri(source, proxy.baseUrl)
        appendLog("Play: $playable")
        player.setMediaItem(MediaItem.fromUri(playable))
        player.prepare()
        player.play()
    }

    private fun stopPlayback() {
        player.stop()
        setStatus("Stopped")
        appendLog("Stopped")
    }

    private fun setStatus(status: String) {
        statusView.text = "Status: $status    Local IP: ${localIpAddress()}    Proxy: ${proxy.baseUrl}"
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            logs.addLast(message)
            while (logs.size > 50) logs.removeFirst()
            logView.text = logs.joinToString("\n")
        }
    }

    private fun localIpAddress(): String =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress
            ?: "unknown"
}
```

- [ ] **Step 3: Run compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation succeeds.

- [ ] **Step 4: Commit**

Run:

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ProbeConfig.kt app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt
git commit -m "feat: add android playback probe activity"
```

### Task 6: APK Build And Verification Notes

**Files:**
- Modify: `README.md`
- Modify: `docs/android-honor-probe.md`

- [ ] **Step 1: Add Android probe section to root README**

Append to `README.md`:

```markdown
## Android Honor Smart Screen Probe

The Android probe lives at the repository root. It validates APK installation and ExoPlayer playback through the same HLS normalization approach proven by the Linux prototype.

Build when JDK 17 and Android SDK 35 are available:

```bash
./gradlew :app:assembleDebug
```

See `docs/android-honor-probe.md` for sideload and TV verification steps.
```

- [ ] **Step 2: Run unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: unit tests pass.

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Commit**

Run:

```bash
git add README.md docs/android-honor-probe.md android
git commit -m "docs: add android probe build notes"
```

## Self-Review Checklist

- Spec coverage: installation, ExoPlayer, local proxy, PNG wrapper stripping, localhost cleartext, and visible logs are covered.
- Out of scope: DLNA discovery, SOAP, and background service remain out of this implementation plan.
- Type consistency: `encodeProxyUrl`, `decodeProxyUrl`, `rewriteHlsManifest`, `stripPngWrapperFromSegment`, `resolvePlayableUri`, `LocalHlsProxy`, and `ProbeConfig.DefaultTestUrl` are used consistently across tasks.
- Known environment constraint: the current local machine may not have JDK, Gradle, or Android SDK installed. If unavailable, create source files and report build verification as blocked by missing toolchain.
