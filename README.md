# PawCast

Android DLNA Digital Media Renderer (DMR). Advertises the device as a media receiver on the local network, accepts UPnP SOAP commands from DLNA controllers, and plays pushed media URLs through ExoPlayer.

## Features

- **DLNA Discovery** — SSDP multicast, responds to controller search requests
- **UPnP Services** — AVTransport, RenderingControl, ConnectionManager
- **HLS Proxy** — rewrites upstream manifests, strips PNG wrappers from segments
- **Segment Cache** — disk-backed LRU cache with prefetch
- **Proxy Support** — HTTP, SOCKS5, SOCKS5H with direct/proxy race mode
- **Web Control Page** — remote play, proxy management, cache stats, APK update
- **TV UI** — menu-driven interface optimized for D-pad navigation
- **Foreground Service** — keeps DLNA discoverable in background
- **Remote APK Update** — push update URL from web page, install on device

## Build

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

This triggers CI to build and publish a signed APK as a GitHub Release.

## Scripts

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run unit tests
```
