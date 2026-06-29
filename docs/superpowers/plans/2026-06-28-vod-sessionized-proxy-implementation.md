# VOD Sessionized Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current request-driven VOD proxy/prefetch flow with a sessionized VOD pipeline that plans, downloads, serves, and diagnoses video/audio/subtitle/init/key resources from one coherent playback session.

**Architecture:** Introduce a single active VOD playback session that owns a normalized asset/slot timeline, a session-local cache/store, a prioritized downloader, a local session-serving manifest/asset layer, and ExoPlayer-backed playback telemetry. Migrate diagnostics and the cache health UI to slot-level real playability rather than segment-only inference.

**Tech Stack:** Kotlin, Android, ExoPlayer Media3, existing in-process HTTP server, OkHttp, JUnit4/Gradle unit tests

---

## File Structure

### New files

- `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSession.kt`
  - Session domain models and enums
- `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManager.kt`
  - Single active VOD session lifecycle
- `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionTimeline.kt`
  - Asset and slot timeline models
- `app/src/main/java/labs/newrapaw/dlna/probe/session/ManifestPlanner.kt`
  - Single-bitrate VOD HLS planning for video/audio/subtitle/init/key
- `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionAssetStore.kt`
  - Session-scoped local file store and metadata
- `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionDownloader.kt`
  - Priority-driven session download engine
- `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionLocalServer.kt`
  - Session-local manifest and asset serving helpers
- `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridge.kt`
  - ExoPlayer telemetry adapter into session state
- `app/src/test/java/labs/newrapaw/dlna/probe/session/ManifestPlannerTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManagerTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionAssetStoreTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionDownloaderTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionLocalServerTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridgeTest.kt`

### Existing files to modify

- `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
  - Replace core VOD flow integration
- `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`
  - Route player setup through session-local manifest and telemetry bridge
- `app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt`
  - Upgrade diagnostics model from segment-centric to slot/session-centric
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
  - Slot-level cache/health UI
- `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
  - Supporting style changes for slot graph
- `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`
  - Narrow to reusable HLS parsing helpers
- `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
  - Retire or convert to compatibility shim, depending on migration
- `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`
  - Retire old prefetch logic
- `app/src/main/java/labs/newrapaw/dlna/probe/DlnaRendererController.kt`
  - Ensure DLNA play path creates a real playback session

### Existing tests to update

- `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/HlsProxyTransformsTest.kt`
- `app/src/test/java/labs/newrapaw/dlna/probe/DlnaRendererControlTest.kt`

---

### Task 1: Add Session Domain Models

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSession.kt`
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionTimeline.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManagerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionManagerTest {
    @Test
    fun sessionFactoryCreatesPreparingVodSessionWithSingleActiveIdentity() {
        val session = PlaybackSession.create(
            sessionId = "session-1",
            sourceUrl = "https://example.com/video.m3u8",
            entryManifestUrl = "https://example.com/video.m3u8",
            localRootDir = "/tmp/session-1",
        )

        assertEquals("session-1", session.sessionId)
        assertEquals(PlaybackSessionStatus.PREPARING, session.status)
        assertEquals("https://example.com/video.m3u8", session.sourceUrl)
        assertTrue(session.timeline.slots.isEmpty())
        assertTrue(session.timeline.assets.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackSessionManagerTest.sessionFactoryCreatesPreparingVodSessionWithSingleActiveIdentity`

Expected: FAIL with unresolved `PlaybackSession` / `PlaybackSessionStatus`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

data class SessionTimeline(
    val slots: List<TimelineSlot> = emptyList(),
    val assets: List<SessionAsset> = emptyList(),
)

data class TimelineSlot(
    val slotIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val videoAssetId: String?,
    val audioAssetIds: List<String> = emptyList(),
    val subtitleAssetIds: List<String> = emptyList(),
    val prerequisiteAssetIds: List<String> = emptyList(),
)

enum class SessionAssetKind {
    MANIFEST,
    VIDEO_SEGMENT,
    AUDIO_SEGMENT,
    SUBTITLE_SEGMENT,
    INIT_SEGMENT,
    KEY,
}

enum class SessionAssetState {
    NOT_STARTED,
    QUEUED,
    DOWNLOADING,
    READY,
    FAILED,
}

data class SessionAsset(
    val assetId: String,
    val kind: SessionAssetKind,
    val trackId: String?,
    val url: String,
    val durationMs: Long?,
    val sequence: Int?,
    val blocking: Boolean,
    val requiredForStartup: Boolean,
    val localPath: String?,
    val downloadState: SessionAssetState = SessionAssetState.NOT_STARTED,
)
```

```kotlin
package labs.newrapaw.dlna.probe.session

enum class PlaybackSessionStatus {
    PREPARING,
    PRIMING,
    READY,
    PLAYING,
    DEGRADED,
    STALLED,
    COMPLETED,
    FAILED,
    CLOSED,
}

data class PlaybackSession(
    val sessionId: String,
    val sourceUrl: String,
    val entryManifestUrl: String,
    val localRootDir: String,
    val createdAtMs: Long,
    val status: PlaybackSessionStatus,
    val timeline: SessionTimeline,
) {
    companion object {
        fun create(
            sessionId: String,
            sourceUrl: String,
            entryManifestUrl: String,
            localRootDir: String,
        ): PlaybackSession = PlaybackSession(
            sessionId = sessionId,
            sourceUrl = sourceUrl,
            entryManifestUrl = entryManifestUrl,
            localRootDir = localRootDir,
            createdAtMs = System.currentTimeMillis(),
            status = PlaybackSessionStatus.PREPARING,
            timeline = SessionTimeline(),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackSessionManagerTest.sessionFactoryCreatesPreparingVodSessionWithSingleActiveIdentity`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSession.kt app/src/main/java/labs/newrapaw/dlna/probe/session/SessionTimeline.kt app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManagerTest.kt
git commit -m "feat: add playback session domain models"
```

### Task 2: Add PlaybackSessionManager

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManager.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManagerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun startingNewSessionClosesPreviousSessionAndInvokesCleanup() {
    val cleaned = mutableListOf<String>()
    val manager = PlaybackSessionManager(
        createSessionId = { "session-${cleaned.size + 1}" },
        cleanupSession = { cleaned += it.sessionId },
    )

    val first = manager.startSession(
        sourceUrl = "https://example.com/a.m3u8",
        entryManifestUrl = "https://example.com/a.m3u8",
        localRootDir = "/tmp/a",
    )
    val second = manager.startSession(
        sourceUrl = "https://example.com/b.m3u8",
        entryManifestUrl = "https://example.com/b.m3u8",
        localRootDir = "/tmp/b",
    )

    assertEquals("session-1", first.sessionId)
    assertEquals("session-2", second.sessionId)
    assertEquals(listOf("session-1"), cleaned)
    assertEquals("session-2", manager.activeSession()?.sessionId)
    assertEquals(PlaybackSessionStatus.CLOSED, manager.closedSessions().single().status)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackSessionManagerTest.startingNewSessionClosesPreviousSessionAndInvokesCleanup`

Expected: FAIL with unresolved `PlaybackSessionManager`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

class PlaybackSessionManager(
    private val createSessionId: () -> String,
    private val cleanupSession: (PlaybackSession) -> Unit,
) {
    private var active: PlaybackSession? = null
    private val closed = mutableListOf<PlaybackSession>()

    fun startSession(
        sourceUrl: String,
        entryManifestUrl: String,
        localRootDir: String,
    ): PlaybackSession {
        active?.let { previous ->
            val closedSession = previous.copy(status = PlaybackSessionStatus.CLOSED)
            cleanupSession(closedSession)
            closed += closedSession
        }
        val session = PlaybackSession.create(
            sessionId = createSessionId(),
            sourceUrl = sourceUrl,
            entryManifestUrl = entryManifestUrl,
            localRootDir = localRootDir,
        )
        active = session
        return session
    }

    fun activeSession(): PlaybackSession? = active

    fun closedSessions(): List<PlaybackSession> = closed.toList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackSessionManagerTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManager.kt app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackSessionManagerTest.kt
git commit -m "feat: add playback session manager"
```

### Task 3: Add ManifestPlanner for Single-Bitrate VOD With Audio and Subtitles

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/ManifestPlanner.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/ManifestPlannerTest.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPlannerTest {
    @Test
    fun plannerBuildsSlotsAndAssetsForVideoAudioSubtitleInitAndKey() {
        val manifest = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="enc.key"
            #EXTINF:4.0,
            video-1.ts
            #EXTINF:4.0,
            video-2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val plan = ManifestPlanner().plan(
            manifestUrl = "https://example.com/video/index.m3u8",
            videoManifest = manifest,
            audioTracks = listOf(
                PlannedTrackManifest(
                    trackId = "audio-main",
                    kind = SessionAssetKind.AUDIO_SEGMENT,
                    manifestUrl = "https://example.com/audio/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXTINF:4.0,
                        audio-1.aac
                        #EXTINF:4.0,
                        audio-2.aac
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
            subtitleTracks = listOf(
                PlannedTrackManifest(
                    trackId = "sub-zh",
                    kind = SessionAssetKind.SUBTITLE_SEGMENT,
                    manifestUrl = "https://example.com/subs/index.m3u8",
                    manifestBody = """
                        #EXTM3U
                        #EXTINF:4.0,
                        sub-1.vtt
                        #EXTINF:4.0,
                        sub-2.vtt
                        #EXT-X-ENDLIST
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(2, plan.slots.size)
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.INIT_SEGMENT })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.KEY })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.AUDIO_SEGMENT })
        assertTrue(plan.assets.any { it.kind == SessionAssetKind.SUBTITLE_SEGMENT })
        assertEquals(0L, plan.slots[0].startMs)
        assertEquals(4_000L, plan.slots[0].endMs)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.ManifestPlannerTest.plannerBuildsSlotsAndAssetsForVideoAudioSubtitleInitAndKey`

Expected: FAIL with unresolved `ManifestPlanner` / `PlannedTrackManifest`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

import java.net.URI

data class PlannedTrackManifest(
    val trackId: String,
    val kind: SessionAssetKind,
    val manifestUrl: String,
    val manifestBody: String,
)

data class PlannedSessionTimeline(
    val slots: List<TimelineSlot>,
    val assets: List<SessionAsset>,
)

class ManifestPlanner {
    fun plan(
        manifestUrl: String,
        videoManifest: String,
        audioTracks: List<PlannedTrackManifest>,
        subtitleTracks: List<PlannedTrackManifest>,
    ): PlannedSessionTimeline {
        val assets = mutableListOf<SessionAsset>()
        val videoEntries = parseMediaEntries(videoManifest, manifestUrl)
        val audioEntries = audioTracks.associate { track ->
            track.trackId to parseMediaEntries(track.manifestBody, track.manifestUrl)
        }
        val subtitleEntries = subtitleTracks.associate { track ->
            track.trackId to parseMediaEntries(track.manifestBody, track.manifestUrl)
        }

        parseMapUri(videoManifest, manifestUrl)?.let { initUrl ->
            assets += SessionAsset(
                assetId = "init-0",
                kind = SessionAssetKind.INIT_SEGMENT,
                trackId = null,
                url = initUrl,
                durationMs = null,
                sequence = 0,
                blocking = true,
                requiredForStartup = true,
                localPath = null,
            )
        }
        parseKeyUri(videoManifest, manifestUrl)?.let { keyUrl ->
            assets += SessionAsset(
                assetId = "key-0",
                kind = SessionAssetKind.KEY,
                trackId = null,
                url = keyUrl,
                durationMs = null,
                sequence = 0,
                blocking = true,
                requiredForStartup = true,
                localPath = null,
            )
        }

        val slots = videoEntries.mapIndexed { index, entry ->
            val videoId = "video-$index"
            assets += SessionAsset(
                assetId = videoId,
                kind = SessionAssetKind.VIDEO_SEGMENT,
                trackId = "video-main",
                url = entry.url,
                durationMs = entry.durationMs,
                sequence = index,
                blocking = true,
                requiredForStartup = index < 4,
                localPath = null,
            )

            val audioIds = audioEntries.flatMap { (trackId, entries) ->
                entries.getOrNull(index)?.let { audioEntry ->
                    val audioId = "audio-$trackId-$index"
                    assets += SessionAsset(
                        assetId = audioId,
                        kind = SessionAssetKind.AUDIO_SEGMENT,
                        trackId = trackId,
                        url = audioEntry.url,
                        durationMs = audioEntry.durationMs,
                        sequence = index,
                        blocking = true,
                        requiredForStartup = index < 4,
                        localPath = null,
                    )
                    listOf(audioId)
                } ?: emptyList()
            }

            val subtitleIds = subtitleEntries.flatMap { (trackId, entries) ->
                entries.getOrNull(index)?.let { subtitleEntry ->
                    val subtitleId = "subtitle-$trackId-$index"
                    assets += SessionAsset(
                        assetId = subtitleId,
                        kind = SessionAssetKind.SUBTITLE_SEGMENT,
                        trackId = trackId,
                        url = subtitleEntry.url,
                        durationMs = subtitleEntry.durationMs,
                        sequence = index,
                        blocking = false,
                        requiredForStartup = index < 2,
                        localPath = null,
                    )
                    listOf(subtitleId)
                } ?: emptyList()
            }

            val startMs = videoEntries.take(index).sumOf { it.durationMs ?: 0L }
            val endMs = startMs + (entry.durationMs ?: 0L)
            TimelineSlot(
                slotIndex = index,
                startMs = startMs,
                endMs = endMs,
                videoAssetId = videoId,
                audioAssetIds = audioIds,
                subtitleAssetIds = subtitleIds,
                prerequisiteAssetIds = assets.filter { it.kind == SessionAssetKind.INIT_SEGMENT || it.kind == SessionAssetKind.KEY }.map { it.assetId },
            )
        }

        return PlannedSessionTimeline(slots = slots, assets = assets)
    }
}

private data class MediaEntry(
    val url: String,
    val durationMs: Long?,
)

private fun parseMediaEntries(manifestBody: String, manifestUrl: String): List<MediaEntry> {
    var pendingDurationMs: Long? = null
    return buildList {
        manifestBody.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingDurationMs = (line.substringAfter(":").substringBefore(",").trim().toDoubleOrNull()?.times(1000))?.toLong()
                }
                line.startsWith("#") -> Unit
                else -> {
                    add(MediaEntry(url = URI(manifestUrl).resolve(line).toString(), durationMs = pendingDurationMs))
                    pendingDurationMs = null
                }
            }
        }
    }
}

private fun parseMapUri(manifestBody: String, manifestUrl: String): String? =
    Regex("""#EXT-X-MAP:URI="([^"]+)"""").find(manifestBody)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() }

private fun parseKeyUri(manifestBody: String, manifestUrl: String): String? =
    Regex("""#EXT-X-KEY:[^\\n]*URI="([^"]+)"""").find(manifestBody)?.groupValues?.get(1)?.let { URI(manifestUrl).resolve(it).toString() }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.ManifestPlannerTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/ManifestPlanner.kt app/src/test/java/labs/newrapaw/dlna/probe/session/ManifestPlannerTest.kt app/src/main/java/labs/newrapaw/dlna/probe/HlsProxyTransforms.kt
git commit -m "feat: add session manifest planner"
```

### Task 4: Add SessionAssetStore

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionAssetStore.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionAssetStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionAssetStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun storeWritesSessionAssetAndClearsWholeSessionTree() {
        val root = temporaryFolder.newFolder("session-root")
        val store = SessionAssetStore(root)

        val file = store.writeAsset("session-1", "video-0", byteArrayOf(1, 2, 3))
        assertTrue(file.isFile)
        assertEquals(3, file.length())

        store.clearSession("session-1")

        assertTrue(!file.exists())
        assertTrue(!root.resolve("session-1").exists())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionAssetStoreTest.storeWritesSessionAssetAndClearsWholeSessionTree`

Expected: FAIL with unresolved `SessionAssetStore`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

import java.io.File

class SessionAssetStore(
    private val rootDir: File,
) {
    init {
        rootDir.mkdirs()
    }

    fun writeAsset(sessionId: String, assetId: String, bytes: ByteArray): File {
        val sessionDir = rootDir.resolve(sessionId).also { it.mkdirs() }
        val file = sessionDir.resolve("$assetId.bin")
        file.writeBytes(bytes)
        return file
    }

    fun resolveAsset(sessionId: String, assetId: String): File =
        rootDir.resolve(sessionId).resolve("$assetId.bin")

    fun clearSession(sessionId: String) {
        rootDir.resolve(sessionId).deleteRecursively()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionAssetStoreTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/SessionAssetStore.kt app/src/test/java/labs/newrapaw/dlna/probe/session/SessionAssetStoreTest.kt
git commit -m "feat: add session asset store"
```

### Task 5: Add SessionDownloader Priority Engine

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionDownloader.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionDownloaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDownloaderTest {
    @Test
    fun startupAssetsAreScheduledBeforeFarTailVideoAssets() {
        val assets = listOf(
            SessionAsset("video-8", SessionAssetKind.VIDEO_SEGMENT, "video", "u8", 4_000, 8, true, false, null),
            SessionAsset("init-0", SessionAssetKind.INIT_SEGMENT, null, "init", null, 0, true, true, null),
            SessionAsset("key-0", SessionAssetKind.KEY, null, "key", null, 0, true, true, null),
            SessionAsset("audio-0", SessionAssetKind.AUDIO_SEGMENT, "audio", "a0", 4_000, 0, true, true, null),
            SessionAsset("subtitle-0", SessionAssetKind.SUBTITLE_SEGMENT, "sub", "s0", 4_000, 0, false, true, null),
            SessionAsset("video-0", SessionAssetKind.VIDEO_SEGMENT, "video", "u0", 4_000, 0, true, true, null),
        )

        val scheduled = SessionDownloader.planStartupQueue(assets).map { it.assetId }

        assertEquals(listOf("init-0", "key-0", "audio-0", "subtitle-0", "video-0", "video-8"), scheduled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionDownloaderTest.startupAssetsAreScheduledBeforeFarTailVideoAssets`

Expected: FAIL with unresolved `SessionDownloader`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

class SessionDownloader {
    companion object {
        fun planStartupQueue(assets: List<SessionAsset>): List<SessionAsset> =
            assets.sortedWith(
                compareBy<SessionAsset> { startupPriority(it) }
                    .thenBy { it.sequence ?: Int.MAX_VALUE },
            )
    }
}

private fun startupPriority(asset: SessionAsset): Int =
    when {
        asset.kind == SessionAssetKind.INIT_SEGMENT -> 0
        asset.kind == SessionAssetKind.KEY -> 1
        asset.kind == SessionAssetKind.AUDIO_SEGMENT && asset.requiredForStartup -> 2
        asset.kind == SessionAssetKind.SUBTITLE_SEGMENT && asset.requiredForStartup -> 3
        asset.kind == SessionAssetKind.VIDEO_SEGMENT && asset.requiredForStartup -> 4
        else -> 5
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionDownloaderTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/SessionDownloader.kt app/src/test/java/labs/newrapaw/dlna/probe/session/SessionDownloaderTest.kt
git commit -m "feat: add session downloader priorities"
```

### Task 6: Add SessionLocalServer Manifest Rewriting

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/SessionLocalServer.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/SessionLocalServerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

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
                    endMs = 4_000,
                    videoAssetId = "video-0",
                    audioAssetIds = listOf("audio-0"),
                    subtitleAssetIds = listOf("subtitle-0"),
                    prerequisiteAssetIds = listOf("init-0", "key-0"),
                ),
            ),
        )

        assertTrue(manifest.contains("/session/session-1/asset/video-0"))
        assertTrue(manifest.contains("/session/session-1/asset/audio-0"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionLocalServerTest.localManifestUsesSessionAssetRoutes`

Expected: FAIL with unresolved `SessionLocalServer`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

class SessionLocalServer {
    fun buildManifest(
        sessionId: String,
        slots: List<TimelineSlot>,
    ): String = buildString {
        append("#EXTM3U\n")
        slots.forEach { slot ->
            append("#EXTINF:${(slot.endMs - slot.startMs) / 1000.0},\n")
            append("/session/$sessionId/asset/${slot.videoAssetId}\n")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.SessionLocalServerTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/SessionLocalServer.kt app/src/test/java/labs/newrapaw/dlna/probe/session/SessionLocalServerTest.kt
git commit -m "feat: add session local server manifest routes"
```

### Task 7: Add PlaybackTelemetryBridge

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridge.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridgeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package labs.newrapaw.dlna.probe.session

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTelemetryBridgeTest {
    @Test
    fun telemetryMapsPlayerPositionToPlayAndBufferSlots() {
        val bridge = PlaybackTelemetryBridge(
            slots = listOf(
                TimelineSlot(0, 0, 4_000, "video-0"),
                TimelineSlot(1, 4_000, 8_000, "video-1"),
                TimelineSlot(2, 8_000, 12_000, "video-2"),
            ),
        )

        val snapshot = bridge.snapshot(
            currentPositionMs = 4_100L,
            bufferedPositionMs = 9_500L,
            isLoading = true,
        )

        assertEquals(1, snapshot.playHeadSlotIndex)
        assertEquals(2, snapshot.bufferHeadSlotIndex)
        assertEquals(true, snapshot.isLoading)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackTelemetryBridgeTest.telemetryMapsPlayerPositionToPlayAndBufferSlots`

Expected: FAIL with unresolved `PlaybackTelemetryBridge`

- [ ] **Step 3: Write minimal implementation**

```kotlin
package labs.newrapaw.dlna.probe.session

data class PlaybackTelemetrySnapshot(
    val playHeadSlotIndex: Int?,
    val bufferHeadSlotIndex: Int?,
    val isLoading: Boolean,
)

class PlaybackTelemetryBridge(
    private val slots: List<TimelineSlot>,
) {
    fun snapshot(
        currentPositionMs: Long,
        bufferedPositionMs: Long,
        isLoading: Boolean,
    ): PlaybackTelemetrySnapshot = PlaybackTelemetrySnapshot(
        playHeadSlotIndex = slots.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }?.slotIndex
            ?: slots.lastOrNull()?.slotIndex,
        bufferHeadSlotIndex = slots.firstOrNull { bufferedPositionMs >= it.startMs && bufferedPositionMs < it.endMs }?.slotIndex
            ?: slots.lastOrNull()?.slotIndex,
        isLoading = isLoading,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.PlaybackTelemetryBridgeTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridge.kt app/src/test/java/labs/newrapaw/dlna/probe/session/PlaybackTelemetryBridgeTest.kt
git commit -m "feat: add playback telemetry bridge"
```

### Task 8: Integrate Session Manager Into LocalHlsProxy Play Flow

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun playRouteCreatesNewSessionAndClearsPreviousSessionState() {
    val requestedUrls = CopyOnWriteArrayList<String>()
    val proxy = LocalHlsProxy(
        log = {},
        onPlayRequested = requestedUrls::add,
    )

    proxy.start()
    try {
        val firstBody = "url=${URLEncoder.encode("https://example.com/a.m3u8", "UTF-8")}"
        rawHttpRequest(
            proxy.port,
            "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${firstBody.length}\r\n\r\n$firstBody",
        )

        val secondBody = "url=${URLEncoder.encode("https://example.com/b.m3u8", "UTF-8")}"
        val response = rawHttpRequest(
            proxy.port,
            "POST /control/play HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: ${secondBody.length}\r\n\r\n$secondBody",
        )

        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertEquals(listOf("https://example.com/a.m3u8", "https://example.com/b.m3u8"), requestedUrls.toList())
    } finally {
        proxy.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest.playRouteCreatesNewSessionAndClearsPreviousSessionState`

Expected: FAIL after assertions added around new session wiring

- [ ] **Step 3: Write minimal implementation**

```kotlin
// In LocalHlsProxy:
private val sessionManager = PlaybackSessionManager(
    createSessionId = { "session-${System.currentTimeMillis()}" },
    cleanupSession = { session ->
        segmentCache?.clear()
        diagnosticsLog("[diag] session closed ${session.sessionId}")
    },
)

private fun handlePlayRequest(body: String, output: OutputStream) {
    val url = decodeFormUrl(body)
    if (url == null) {
        writeJson(output, 400, false, "Missing URL")
        return
    }

    val session = sessionManager.startSession(
        sourceUrl = url,
        entryManifestUrl = url,
        localRootDir = "session-${System.currentTimeMillis()}",
    )
    latestPlayerPositionMs = null
    activeManifestUrl = null
    activeVodSession?.cancel()
    activeVodSession = null
    diagnosticsState.resetForPlayback(
        sourceUrl = session.sourceUrl,
        localProxyUrl = resolvePlayableUri(url, baseUrl),
        settings = proxySettingsStore.load(),
    )
    onPlayRequested(url)
    writeJson(output, 200, true, "Play request sent. You can return to the TV.")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest.playRouteCreatesNewSessionAndClearsPreviousSessionState --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest.newPlayRequestClearsPreviousPlaybackCacheEvenWithoutGracefulStop`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt
git commit -m "feat: start sessionized play flow in local proxy"
```

### Task 9: Route DLNA Play Through Session Startup

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/DlnaRendererController.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/DlnaRendererControlTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun playActionUsesCurrentUriAndStartsUnifiedPlaybackPath() {
    val played = mutableListOf<String>()
    val controller = DlnaRendererController(
        log = {},
        onPlayRequested = played::add,
        onStopRequested = {},
        onPauseRequested = {},
    )

    controller.handleControlRequest(
        serviceName = "AVTransport",
        soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
        body = """<s:Envelope><s:Body><u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><CurrentURI>https://example.com/video.m3u8</CurrentURI></u:SetAVTransportURI></s:Body></s:Envelope>""",
    )
    controller.handleControlRequest(
        serviceName = "AVTransport",
        soapActionHeader = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
        body = """<s:Envelope><s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"></u:Play></s:Body></s:Envelope>""",
    )

    assertEquals(listOf("https://example.com/video.m3u8"), played)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.DlnaRendererControlTest.playActionUsesCurrentUriAndStartsUnifiedPlaybackPath`

Expected: FAIL if current assertions do not verify unified path behavior

- [ ] **Step 3: Write minimal implementation**

```kotlin
// Keep existing public API, but ensure LocalHlsProxy reuses the same startSession path
// by treating onPlayRequested callback as the one and only session startup trigger.
// Update test expectations to assert the callback path rather than duplicate direct play handling.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.DlnaRendererControlTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/DlnaRendererController.kt app/src/test/java/labs/newrapaw/dlna/probe/DlnaRendererControlTest.kt
git commit -m "feat: unify dlna play path with session startup"
```

### Task 10: Replace Segment Diagnostics With Slot Diagnostics Model

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun diagnosticsCanReportBlockedSlotAndDegradedSubtitleSeparately() {
    val state = PlaybackDiagnosticsState(sampleLimit = 20)
    state.resetForPlayback(
        sourceUrl = "https://origin.example/video.m3u8",
        localProxyUrl = "http://127.0.0.1:43000/session/1/manifest.m3u8",
        settings = ProxySettingsState(prefetchConcurrency = 3),
    )

    state.updateSlotDiagnostics(
        currentSlotIndex = 10,
        bufferedSlotIndex = 12,
        continuousReadySlotCount = 2,
        continuousReadyDurationMs = 8_000L,
        blockedDependencyKinds = listOf("audio_segment"),
        degradedDependencyKinds = listOf("subtitle_segment"),
    )

    val snapshot = state.snapshot()

    assertEquals(10, snapshot.currentPlaybackSegmentIndex)
    assertEquals(12, snapshot.bufferedSegmentIndex)
    assertTrue(snapshot.insights.any { it.code == "slot_blocked_audio" })
    assertTrue(snapshot.insights.any { it.code == "slot_degraded_subtitle" })
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest.diagnosticsCanReportBlockedSlotAndDegradedSubtitleSeparately`

Expected: FAIL with unresolved slot diagnostics API

- [ ] **Step 3: Write minimal implementation**

```kotlin
// Extend PlaybackDiagnosticsSnapshot with slot-oriented fields:
// - bufferedSegmentIndex: Int?
// - blockedDependencyKinds: List<String>
// - degradedDependencyKinds: List<String>
//
// Add:
// fun updateSlotDiagnostics(
//     currentSlotIndex: Int?,
//     bufferedSlotIndex: Int?,
//     continuousReadySlotCount: Int,
//     continuousReadyDurationMs: Long,
//     blockedDependencyKinds: List<String>,
//     degradedDependencyKinds: List<String>,
// )
//
// Update diagnosticsInsights/primaryBottleneck to emit:
// - slot_blocked_<kind>
// - slot_degraded_<kind>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/PlaybackDiagnostics.kt app/src/test/java/labs/newrapaw/dlna/probe/PlaybackDiagnosticsStateTest.kt
git commit -m "feat: upgrade diagnostics to slot dependency model"
```

### Task 11: Switch MainActivity To Session-Local Playback and Telemetry Bridge

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/MainActivityLoggingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun sourceContainsSessionTelemetryBridgeUpdates() {
    val source = java.io.File("src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt").readText()

    assertTrue(source.contains("PlaybackTelemetryBridge"))
    assertTrue(source.contains("session-local"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.MainActivityLoggingTest.sourceContainsSessionTelemetryBridgeUpdates`

Expected: FAIL because session-local bridge wiring does not exist

- [ ] **Step 3: Write minimal implementation**

```kotlin
// In MainActivity:
// - create PlaybackTelemetryBridge from active session slots
// - hand player a session-local manifest URL returned by LocalHlsProxy/session layer
// - replace legacy direct proxy telemetry updates with slot-aware bridge updates
// - keep 500ms polling only as bridge feed if AnalyticsListener alone is insufficient
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.MainActivityLoggingTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt app/src/test/java/labs/newrapaw/dlna/probe/MainActivityLoggingTest.kt
git commit -m "feat: switch player to session-local playback telemetry"
```

### Task 12: Upgrade Admin Cache Page to Slot-Level Health Graph

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun diagnosticsSectionRendersSlotLevelStatesForBlockedAndDegradedPlayback() {
    val html = buildDiagnosticsPanelHtml(
        PlaybackDiagnosticsSnapshot.empty().copy(
            playbackStatus = PlaybackDiagnosticsStatus.PLAYING,
            currentPlaybackSegmentIndex = 5,
            bufferedSegmentIndex = 7,
            continuousReadySegmentCount = 2,
            continuousReadyDurationMs = 8_000L,
            insights = listOf(
                DiagnosticsInsight("slot_blocked_audio", "当前 slot 缺少音频"),
                DiagnosticsInsight("slot_degraded_subtitle", "当前字幕降级"),
            ),
        ),
    )

    assertTrue(html.contains("slot"))
    assertTrue(html.contains("buffer-edge"))
    assertTrue(html.contains("当前字幕降级"))
    assertTrue(html.contains("当前 slot 缺少音频"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest.diagnosticsSectionRendersSlotLevelStatesForBlockedAndDegradedPlayback`

Expected: FAIL because current UI is still segment-centric

- [ ] **Step 3: Write minimal implementation**

```kotlin
// In ControlPage.kt and ControlPageShell.kt:
// - rename health graph semantics from raw TS cache to slot playability
// - render blocked/degraded states in slot graph
// - keep blue playhead and buffer edge marker
// - show slot detail panel including dependency causes
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.ControlPageTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt app/src/main/java/labs/newrapaw/dlna/probe/ControlPageShell.kt app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt
git commit -m "feat: render slot-level playback health graph"
```

### Task 13: Remove Legacy Prefetch-Dominant VOD Path

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt`
- Modify: `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun legacyPrefetchStateIsNotRequiredForCurrentVODSessionDiagnostics() {
    val source = java.io.File("src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt").readText()

    assertTrue(!source.contains("VodPrefetchSession("))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest.legacyPrefetchStateIsNotRequiredForCurrentVODSessionDiagnostics`

Expected: FAIL because LocalHlsProxy still instantiates legacy prefetch session

- [ ] **Step 3: Write minimal implementation**

```kotlin
// Remove LocalHlsProxy dependence on VodPrefetchSession for VOD playback.
// Keep compatibility helpers only if still used by non-VOD or parsing tests.
// Narrow HlsSegmentCache responsibilities or replace it with adapter usage around SessionAssetStore.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.VodPrefetchSessionTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest`

Expected: PASS, or updated legacy tests removed/replaced appropriately

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe/VodPrefetchSession.kt app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt app/src/test/java/labs/newrapaw/dlna/probe/VodPrefetchSessionTest.kt app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt
git commit -m "refactor: retire legacy vod prefetch path"
```

### Task 14: End-to-End Regression Verification

**Files:**
- Modify as needed based on failures from prior tasks

- [ ] **Step 1: Run focused session test suite**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.session.ManifestPlannerTest --tests labs.newrapaw.dlna.probe.session.PlaybackSessionManagerTest --tests labs.newrapaw.dlna.probe.session.SessionAssetStoreTest --tests labs.newrapaw.dlna.probe.session.SessionDownloaderTest --tests labs.newrapaw.dlna.probe.session.SessionLocalServerTest --tests labs.newrapaw.dlna.probe.session.PlaybackTelemetryBridgeTest
```

Expected: PASS

- [ ] **Step 2: Run proxy/diagnostics regression suite**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.LocalHlsProxyStabilityTest --tests labs.newrapaw.dlna.probe.ControlPageTest --tests labs.newrapaw.dlna.probe.PlaybackDiagnosticsStateTest --tests labs.newrapaw.dlna.probe.DlnaRendererControlTest
```

Expected: PASS

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit final implementation**

```bash
git add app/src/main/java/labs/newrapaw/dlna/probe app/src/test/java/labs/newrapaw/dlna/probe docs/superpowers/specs/2026-06-28-vod-sessionized-proxy-design.md docs/superpowers/plans/2026-06-28-vod-sessionized-proxy-implementation.md
git commit -m "feat: implement sessionized vod proxy pipeline"
```

## Self-Review

### Spec coverage

- Session manager: Task 2, Task 8, Task 9
- Planner and unified timeline: Task 1, Task 3
- Session-scoped cache/store: Task 4
- Downloader priorities/startup model: Task 5
- Local session server: Task 6
- Player telemetry truth: Task 7, Task 11
- Slot/asset diagnostics: Task 10, Task 12
- Old VOD prefetch retirement: Task 13
- Verification/build: Task 14

### Placeholder scan

- No `TODO` / `TBD`
- Some integration tasks necessarily describe edits rather than full final code because they touch large existing files; those tasks still specify exact files, target APIs, test names, and commands

### Type consistency

- Session domain names are consistent across tasks:
  - `PlaybackSession`
  - `PlaybackSessionStatus`
  - `SessionAsset`
  - `TimelineSlot`
  - `PlaybackTelemetryBridge`

