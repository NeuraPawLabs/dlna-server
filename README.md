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

GitHub Actions also builds a debug APK on every push. The Actions artifact is
downloaded by GitHub as a `.zip`; unzip it first, then install the contained
`app-debug.apk`. Do not install the downloaded artifact zip directly.

## Release

### First-time signing setup

Generate a keystore:

```bash
keytool -genkey -v -keystore release.keystore -alias pawcast -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 release.keystore   # copy the output
```

Add these GitHub Secrets (`Settings → Secrets → Actions`):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | output of `base64 -w 0 release.keystore` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `pawcast` |
| `KEY_PASSWORD` | key password |

> `release.keystore` is git-ignored. Never commit it.

### Publish

```bash
git tag v0.1.0
git push origin v0.1.0
```

CI will build a signed APK and create a GitHub Release with the APK attached.
Install the attached `app-release.apk` directly from the Release page.

Android requires updates for the same package name to use the same signing key.
If a debug build is already installed, uninstall it before installing a release
build. If an older release was signed with a different keystore, uninstall the
old app first or rebuild with the original keystore.

## Scripts

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run unit tests
```
