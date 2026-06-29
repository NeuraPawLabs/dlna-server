# Linux CLI Proxy Player Design

Date: 2026-06-28

## Goal

Add a Linux command-line player entrypoint that reuses the existing HLS session proxy and cache mechanism.

The user will provide an `m3u8` URL. The tool will:

- start the same sessionized local proxy used by Android
- build the same local session manifest and asset cache
- print the local playback URL
- optionally launch a local external player such as `mpv` or `vlc`

This phase is about validating the proxy/cache pipeline on Linux without any DLNA, Android UI, or desktop GUI requirements.

## Non-Goals

This phase does not include:

- Linux GUI
- embedded player window
- DLNA renderer control
- Android feature changes beyond extracting shared code
- parity for Android-only admin pages

## Why This Exists

The current Android app contains the only runnable environment for the sessionized proxy. That makes debugging playback and cache behavior slower than necessary.

A Linux CLI runner gives three practical advantages:

1. direct reproduction from a workstation
2. the same proxy/cache logic under easier observation
3. faster iteration on session, prefetch, and diagnostics behavior

## Product Shape

The first Linux version is a CLI process.

Expected usage:

```bash
./gradlew :desktop:run --args="play <m3u8-url>"
```

Expected behavior:

1. Start a local HTTP proxy on a random free port.
2. Open a playback session for the provided URL.
3. Print the local manifest URL.
4. Try launching `mpv`.
5. If `mpv` is unavailable, try `vlc`.
6. If neither player exists, keep the proxy running and print the local URL for manual use.

## Architecture

## High-Level Split

The existing Android code mixes three concerns:

1. reusable proxy/cache/session logic
2. Android runtime integration
3. DLNA/admin/UI behavior

The Linux version should only depend on the first concern.

The implementation should introduce a shared JVM core and keep platform-specific shells thin.

### Shared JVM Core

Create a reusable module containing:

- HLS session planning
- session asset storage
- session local manifest serving
- upstream fetching
- prefetch scheduling
- playback diagnostics state
- proxy settings state
- logging hooks

This shared core must not depend on:

- Android SDK classes
- Android lifecycle/services
- DLNA renderer code

### Android Shell

Android keeps:

- foreground service
- activity/UI
- SSDP/DLNA
- APK update flow
- admin web pages

Android should instantiate the shared core rather than owning core playback logic directly.

### Linux CLI Shell

Linux adds:

- argument parsing
- player process launching
- stdout/stderr logging
- process lifetime management

## Module Plan

Recommended module structure:

- `:app`
  - Android shell
- `:desktop`
  - Linux CLI shell
- `:shared` or `:core`
  - reusable JVM proxy/session logic

If introducing `:shared` feels too disruptive in one step, a transitional `:desktop` module may temporarily reuse files moved from `:app` into a shared source package, but the target architecture should still keep Android dependencies out of the proxy core.

## Core API Shape

The shared module should expose a small API instead of leaking Android-era implementation details.

Suggested primary entrypoint:

- `CoreLocalHlsProxy`

Responsibilities:

- start and stop the local HTTP server
- create and manage playback sessions
- expose the local base URL
- expose the active session info
- expose diagnostics snapshots
- accept proxy settings and logging sinks

Suggested constructor dependencies:

- `OkHttpClient`
- log sink `(String) -> Unit`
- settings store abstraction
- session asset root directory

Suggested Linux-facing operations:

- `start()`
- `close()`
- `openSession(sourceUrl: String): ActiveSessionInfo`
- `diagnosticsSnapshot(): PlaybackDiagnosticsSnapshot`

## Linux CLI Behavior

Command set for phase 1:

- `play <url>`

Optional phase-1 flags:

- `--player=auto|mpv|vlc|none`
- `--proxy=http://host:port`
- `--upstream-mode=direct|proxy|race`
- `--prefetch-concurrency=<n>`

Minimum required behavior:

- validate URL presence
- create temp cache directory
- start proxy
- open session
- print:
  - source URL
  - local manifest URL
  - cache directory
  - selected player mode
- launch external player when configured
- keep process alive while proxy is serving
- handle Ctrl+C with clean shutdown and cache cleanup

## External Player Policy

The CLI should not implement playback itself.

Instead it should spawn:

- `mpv <local-manifest-url>`
- fallback `vlc <local-manifest-url>`

If the requested player is missing:

- emit a clear log message
- either try the fallback player or continue without launching, depending on mode

This keeps the Linux layer thin and preserves the proxy as the system under test.

## Cache and Session Semantics

The Linux runner must preserve the same core behavior as Android:

- sessionized local manifests
- prefetch queue behavior
- asset cache on disk
- cleanup of previous session when a new session starts
- startup gate behavior
- runtime asset state transitions

The Linux shell may choose a different cache root path, but not a different cache model.

## Logging and Diagnostics

Phase 1 uses console logging only.

The CLI should print:

- startup status
- chosen player
- local playback URL
- upstream source selection logs
- session asset request/response logs
- session prepare failures
- shutdown events

No HTML admin page is required for Linux phase 1.

Diagnostics state should still exist in the shared core because Android already uses it and Linux may later expose it via CLI or HTTP.

## Error Handling

The Linux CLI should fail clearly for:

- missing URL
- unsupported manifest structure
- failed session preparation
- local server bind failure
- missing external player when explicit player mode requires it

If player auto-launch fails but the local proxy is healthy:

- keep the proxy alive
- print the local playback URL
- exit only on user interruption or fatal proxy failure

## Testing Strategy

This feature should follow TDD.

### Shared Core Tests

Reuse or extend existing unit tests for:

- manifest planning
- session local manifest generation
- session downloader ordering
- proxy request/asset behavior

### Linux CLI Tests

Add JVM tests for:

- argument parsing
- player command selection
- fallback from `mpv` to `vlc`
- no-player mode
- local session URL reporting

Use fake process launching abstractions instead of spawning real players in tests.

## Migration Strategy

The safest path is incremental extraction:

1. identify Android-free proxy/session classes already reusable
2. move them into a shared module with package-compatible imports
3. define small abstractions for Android-specific dependencies
4. adapt Android app to consume the shared core
5. add Linux CLI shell on top of the shared core

Do not rewrite the proxy logic from scratch for Linux.

## Risks

### Risk 1: Extraction Scope Sprawl

`LocalHlsProxy.kt` currently carries both reusable logic and Android-app-specific behavior. Extraction can sprawl if done as a big-bang refactor.

Mitigation:

- extract only what Linux needs first
- leave DLNA/admin page code in Android shell
- add thin interfaces where separation is needed

### Risk 2: Process Lifetime Confusion

The CLI process must stay alive while the player reads the local manifest.

Mitigation:

- explicit blocking main loop
- signal handler for clean shutdown
- optional wait for child player exit in auto-launch mode

### Risk 3: Hidden Android Dependencies

Some classes may appear generic but still assume Android runtime behavior.

Mitigation:

- compile the shared module as plain JVM early
- fix dependency leaks before adding CLI features

## Success Criteria

This phase is successful when:

1. A Linux user can run one command with an `m3u8` URL.
2. The process starts the same proxy/cache/session mechanism as Android.
3. The process prints a local session manifest URL.
4. `mpv` or `vlc` can play through that local proxy URL.
5. Existing Android behavior still compiles and passes tests.
