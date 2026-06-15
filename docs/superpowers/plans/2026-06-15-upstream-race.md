# Upstream Race Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow HLS upstream requests to race direct and configured proxy paths, using whichever succeeds first.

**Architecture:** Store an upstream mode alongside the selected proxy. `LocalHlsProxy` keeps cache keys unchanged, but the cache fetcher uses a new upstream fetch function that either goes direct, uses the proxy only, or starts direct and proxied OkHttp calls concurrently and cancels the loser.

**Tech Stack:** Kotlin, OkHttp, existing JVM unit tests and socket-based local HTTP fixtures.

---

### Task 1: Proxy Settings Mode

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/ProxyConfig.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/ProxyConfigTest.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/ControlPageTest.kt`

- [ ] Add failing tests for `UpstreamMode.PROXY_ONLY` and `UpstreamMode.RACE_DIRECT_AND_PROXY`.
- [ ] Persist `upstreamMode` in shared preferences JSON with `PROXY_ONLY` default.
- [ ] Add management page radio controls for proxy-only and race mode.

### Task 2: Race Fetching

**Files:**
- Modify: `app/src/main/java/labs/newrapaw/dlna/probe/LocalHlsProxy.kt`
- Test: `app/src/test/java/labs/newrapaw/dlna/probe/LocalHlsProxyStabilityTest.kt`

- [ ] Add a failing test where direct upstream is slow and configured HTTP proxy responds fast.
- [ ] Implement direct/proxy racing for manifest and segment upstream fetches.
- [ ] Cancel the losing OkHttp call after the first successful 2xx body read.
- [ ] If both paths fail, return a useful upstream error.

### Task 3: Verification

**Files:**
- Android app and tests

- [ ] Run `JAVA_HOME=$HOME/.local/opt/jdk-17 ANDROID_HOME=$HOME/Android/Sdk ANDROID_SDK_ROOT=$HOME/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug`.
