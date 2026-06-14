# DLNA Server

Linux prototype of a DLNA Digital Media Renderer (DMR). It advertises this machine as a media receiver, accepts basic UPnP AVTransport and RenderingControl SOAP commands, and delegates playback to `mpv`.

## Requirements

- Node.js 22 or newer
- `mpv` available on `PATH`
- Linux machine on the same LAN as the DLNA controller

Install dependencies:

```bash
npm install
```

Install `mpv` on Ubuntu/Debian:

```bash
sudo apt install mpv
```

## Run

```bash
npm run dev
```

Startup logs show the HTTP description URL and SSDP status. When a controller sends media, logs include the received URL and the URL passed to playback:

```text
[AVTransport] Set URI: http://example.test/video.mp4
[AVTransport] Play: http://example.test/video.mp4
```

Useful options:

```bash
npm run dev -- --port 49152 --name "NewraPaw DLNA Receiver" --advertise-address 192.168.1.20 --mpv-path /usr/bin/mpv
```

Use `--advertise-address` when the default `127.0.0.1` description URL is not reachable from other devices.
Use `--mpv-path` if `mpv` is installed but not available on the service `PATH`.

## Verify Locally

```bash
curl http://127.0.0.1:49152/description.xml
```

The response should include `NewraPaw DLNA Receiver` and `MediaRenderer:1`.

## Verify From A DLNA Controller

1. Start this service with an address reachable from the controller:

   ```bash
   npm run dev -- --advertise-address <linux-lan-ip>
   ```

2. Make sure the firewall allows UDP `1900` and TCP `49152`.
3. Open a DLNA/UPnP controller app on a phone or desktop.
4. Select `NewraPaw DLNA Receiver`.
5. Push an HTTP video or audio URL.
6. Confirm `mpv` opens and responds to play, pause, stop, and volume commands.

## Scripts

```bash
npm test
npm run build
npm run dev
npm start
```

## Android Honor Smart Screen Probe

The Android probe lives in `android/`. It validates APK installation and ExoPlayer playback through the same HLS normalization approach proven by the Linux prototype.

After launching the probe on the TV, open the displayed `Open on phone` URL from a phone or computer on the same LAN. Paste the long m3u8 URL in that browser page and press `Play`; you do not need to type long URLs with the TV remote.

Build when JDK 17 and Android SDK 36 are available:

```bash
cd android
./gradlew :app:assembleDebug
```

See `docs/android-honor-probe.md` for sideload and TV verification steps.
