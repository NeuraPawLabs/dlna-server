# HLS Proxy Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add app-local HLS segment caching and prefetching to speed playback through the existing Android proxy server.

**Architecture:** Keep ExoPlayer pointed at the local `/proxy/hls.m3u8` endpoint. `LocalHlsProxy` rewrites manifests as before, but segment responses go through a small disk-backed `HlsSegmentCache` that deduplicates concurrent downloads, stores transformed TS bytes, tracks stats, and trims by LRU. Manifest handling schedules lightweight prefetch for the next segment URLs using the same upstream proxy settings.

**Tech Stack:** Kotlin, OkHttp, Android app cache directory, existing JVM unit tests.

---

### Task 1: Segment Cache Core

**Files:**
- Create: `app/src/main/java/labs/newrapaw/dlna/probe/HlsSegmentCache.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/HlsSegmentCacheTest.kt`

- [ ] Write failing tests for cache miss, cache hit, in-flight deduplication, and LRU trimming.
- [ ] Implement `HlsSegmentCache.getOrFetch(url, fetcher)`, `prefetch(url, fetcher)`, `stats()`, and `clear()`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests labs.newrapaw.dlna.probe.HlsSegmentCacheTest`.

### Task 2: Proxy Integration

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ControlPage.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] Write failing tests that segment requests use cached bytes and `/control/cache/clear` clears the cache.
- [ ] Add `segmentCache` and `prefetchExecutor` to `LocalHlsProxy`.
- [ ] After manifest rewrite, schedule prefetch for the first four segment URLs.
- [ ] Add cache status and clear form to the web management page.
- [ ] Run related proxy and page tests.

### Task 3: Android Wiring and Full Verification

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/MainActivity.kt`

- [ ] Construct `HlsSegmentCache` under `cacheDir/hls-segments` with a 1GB default limit.
- [ ] Pass the cache into `LocalHlsProxy`.
- [ ] Run `JAVA_HOME=$HOME/.local/opt/jdk-17 ANDROID_HOME=$HOME/Android/Sdk ANDROID_SDK_ROOT=$HOME/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug`.
