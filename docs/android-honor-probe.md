# Honor Smart Screen Probe

This probe validates APK installation and playback on Honor Smart Screen before the DLNA receiver is ported.

## Local Build Requirements

- JDK 17
- Android SDK with platform 35
- Android build-tools

Check the local toolchain:

```bash
java -version
echo "$ANDROID_HOME"
ls "$ANDROID_HOME/platforms/android-35"
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
3. Enter a fresh m3u8 URL if the built-in field is empty.
4. Press `Play Test Stream`.
5. Confirm the status changes from `Idle` to `Buffering` and then `Ready`.
6. Confirm video and audio play.
7. Press `Stop` and then `Play Test Stream` again.

If playback fails, photograph the visible log panel and copy any logcat output if available.
