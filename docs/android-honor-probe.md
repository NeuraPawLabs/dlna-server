# Honor Smart Screen Probe

This probe validates APK installation and playback on Honor Smart Screen before the DLNA receiver is ported.

## Local Build Requirements

- JDK 17
- Android SDK with platform 36
- Android build-tools

Check the local toolchain:

```bash
java -version
echo "$ANDROID_HOME"
ls "$ANDROID_HOME/platforms/android-36"
```

## Build

```bash
cd android
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Device Verification

1. Install the APK on Honor Smart Screen by U disk or the system-supported sideload flow.
2. Launch `NewraPaw DLNA Probe`.
3. On the TV screen, find the `Open on phone` URL, such as `http://192.168.1.23:43000`.
4. Open that URL from a phone or computer on the same Wi-Fi/LAN.
5. Paste a fresh m3u8 URL into the browser page and press `Play`.
6. Confirm the TV status changes from `Idle` to `Buffering` and then `Ready`.
7. Confirm video and audio play.
8. Press `Stop` from the browser page or TV app, then submit `Play` again.

If playback fails, photograph the visible log panel and copy any logcat output if available.

## Update Without USB Copy

After the first APK is installed, later debug builds can be installed through the probe's browser control page.

Build the latest APK:

```bash
cd android
./gradlew :app:assembleDebug
```

Serve the APK from the development machine:

```bash
cd app/build/outputs/apk/debug
python3 -m http.server 8080
```

On the TV, launch `NewraPaw DLNA Probe`. From a phone or computer on the same LAN, open the `Open on phone` URL shown on the TV. Paste this APK URL into the `Paste APK URL` field:

```text
http://<dev-machine-ip>:8080/app-debug.apk
```

Press `Install Update`. The TV downloads the APK and opens the system installer. Confirm the install on the TV.

Android does not allow this debug app to silently update itself, so the final confirmation click on the TV is still required.
