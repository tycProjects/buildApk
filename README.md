# Screen Recorder (Android)

A minimal Android app that records the full device screen at maximum
practical quality:

- **Native resolution** — no downscaling, captures at your exact screen size
- **60 fps** — no dropped frames
- **100 Mbps bitrate** — high enough that H.264 compression artifacts are
  essentially invisible to the eye
- Foreground service so recording keeps running while you use other apps

## About "no compression"

Being fully transparent: Android phones encode video in hardware using
H.264/H.265, and there is no true lossless mode exposed to apps — that's a
chip limitation, not a setting. What this app does is push every dial (bitrate,
resolution, frame rate) to the point where you will not see grain, blocking,
or macroblocking artifacts in normal viewing. If you truly need pixel-perfect
lossless capture, that generally requires a PC-based tool (e.g. OBS with the
FFV1 codec) — happy to build that version instead if useful.

## How to build

1. Unzip this project.
2. Open the folder in **Android Studio** (Giraffe or newer recommended).
3. Let Gradle sync (it will download the Android Gradle Plugin + Kotlin
   plugin — needs internet access).
4. Connect a device or start an emulator (Android 7.0 / API 24+).
5. Click **Run**.

## How to use

1. Tap **START**.
2. Accept the system "Start recording or casting?" screen-capture prompt —
   this is required by Android and can't be skipped.
3. The app minimizes automatically so it captures the app you want, not
   itself.
4. Reopen the app and tap **STOP** (or tap the notification) to end.
5. Files save to: `Android/data/com.example.screenrecorder/files/Movies/`
   on device storage.

## Notes

- No microphone/system audio is captured by default (kept simple per your
  "just pure record" ask) — audio capture can be added if you want it.
- Recording quality settings live in `RecordingService.kt` under
  `VIDEO_BITRATE` and `VIDEO_FRAME_RATE` if you want to tune them further.
