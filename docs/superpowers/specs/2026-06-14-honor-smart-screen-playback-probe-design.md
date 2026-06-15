# Honor Smart Screen Playback Probe Design

## Goal

Build a minimal Android APK probe for Honor Smart Screen that verifies installation, app startup, Media3 ExoPlayer playback, and the local HLS compatibility proxy needed for PNG-wrapped HLS segments.

## Scope

The probe is not a full DLNA receiver. It does not implement SSDP, SOAP, or UPnP DMR services in this phase. It validates the highest-risk Android TV migration assumptions before porting the Linux DMR control layer.

The app is added as an independent Android project at the repository root so the Linux prototype remains usable and testable.

## Target Environment

The first target is Honor Smart Screen, which may not behave like a standard Android TV device. The APK should use ordinary Android app packaging rather than relying on Android TV-only distribution assumptions.

The first build targets modern Android APIs while keeping the app simple enough to sideload. It uses AndroidX Media3 ExoPlayer for playback. Android official documentation lists Media3 as the current Jetpack media library, and its release page currently shows Media3 `1.10.0`, which requires compiling against Android API 36 or newer. The app must allow local cleartext HTTP because ExoPlayer will read from `http://127.0.0.1:<port>/...`; Android network security configuration supports explicit cleartext opt-in for such cases.

## User Experience

On launch, the app shows one full-screen probe page suitable for a TV remote:

- app title: `PawCast`
- local IP address
- local proxy base URL
- playback status
- latest log lines
- `Play Test Stream` button
- `Stop` button

The Play button starts the local HLS proxy, builds a proxied URL for a bundled test m3u8 value, and sends it to ExoPlayer. Stop releases the player but keeps the app open.

## Components

`MainActivity`

- Owns the screen lifecycle.
- Creates and releases ExoPlayer.
- Starts and stops the local proxy server.
- Displays probe status and logs.

`LocalHlsProxy`

- Binds to localhost on an available TCP port.
- Serves `/proxy/hls.m3u8?u=<base64url>` by fetching the upstream m3u8, rewriting media segment lines to localhost proxy URLs, and returning `application/vnd.apple.mpegurl`.
- Serves `/proxy/segment.ts?u=<base64url>` by fetching the upstream segment, stripping any PNG wrapper before MPEG-TS sync bytes, and returning `video/mp2t`.

`PngWrappedSegmentStripper`

- Scans segment bytes for MPEG-TS sync byte `0x47` at offsets `n`, `n + 188`, and `n + 376`.
- Returns the original bytes if no wrapper is detected.
- Returns the slice starting at the sync offset when a wrapper exists.

`ProbeConfig`

- Holds the test stream URL.
- Defaults to an empty string in source to avoid committing expiring signed URLs.
- Allows entering or pasting a URL through a simple text field if remote input is available; otherwise developers can set a build-time string for device testing.

## Android Configuration

Required permissions:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

Required cleartext configuration:

- Permit cleartext traffic for `127.0.0.1` and `localhost`.
- Do not permit arbitrary cleartext hosts.

Dependencies:

- Android Gradle Plugin
- Kotlin Android plugin
- AndroidX Media3 ExoPlayer
- AndroidX Activity Compose or a simple native view Activity

The first version should prefer a simple native Android view or minimal Compose UI over TV-specific Leanback. The goal is installation and playback validation, not final TV UX.

## Data Flow

1. User launches the app.
2. App starts `LocalHlsProxy` on localhost.
3. User presses `Play Test Stream`.
4. App maps the test URL to `http://127.0.0.1:<port>/proxy/hls.m3u8?u=<base64url>`.
5. ExoPlayer requests the local manifest.
6. Proxy fetches the upstream manifest and rewrites segment URLs to `/proxy/segment.ts`.
7. ExoPlayer requests each local segment.
8. Proxy fetches the upstream PNG-wrapped segment, strips the wrapper, and returns MPEG-TS bytes.
9. ExoPlayer decodes the normalized stream.

## Error Handling

The app logs and displays:

- proxy bind failure
- upstream manifest fetch failure
- upstream segment fetch failure
- missing or invalid test URL
- ExoPlayer playback errors

The UI should keep the last 50 log lines so a photo of the TV screen is enough to debug early device failures.

## Testing

Unit tests cover:

- base64url URL encoding and decoding
- manifest rewrite for absolute and relative segment URLs
- PNG wrapper stripping
- pass-through behavior for normal TS segments

Manual device tests cover:

- APK sideload/install on Honor Smart Screen
- app launch from TV UI
- local IP/proxy URL displayed
- playback of a known working m3u8 through the local proxy
- stop and replay behavior

## Out Of Scope

- DLNA discovery
- UPnP SOAP control
- background receiver service
- polished Android TV UI
- app store packaging
- DRM playback
- authentication beyond direct URL fetching
