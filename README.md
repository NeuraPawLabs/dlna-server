# PawCast

Android DLNA Digital Media Renderer for Honor Smart Screen. Advertises the device as a media receiver on the local network, accepts UPnP AVTransport and RenderingControl SOAP commands, and plays pushed media URLs through ExoPlayer.

## Features

- SSDP discovery by DLNA controllers on the same LAN
- UPnP AVTransport, RenderingControl, and ConnectionManager services
- Local HLS proxy with PNG-wrapped segment stripping
- HLS segment caching and prefetch
- Upstream direct/proxy race mode
- Web-based control page for remote play, proxy management, and APK updates

## Requirements

- JDK 17
- Android SDK with platform 36

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Device Verification

1. Install the APK on Honor Smart Screen by U disk or sideload.
2. Launch PawCast.
3. Open the displayed "Open on phone" URL from a phone on the same LAN.
4. Paste an m3u8 URL and press Play.
5. Confirm video and audio play.

For updates, serve the APK from the control page and confirm installation on the TV.

## Sideload and TV Verification

See `docs/android-honor-probe.md` for detailed sideload and TV verification steps.

## Scripts

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run unit tests
```
