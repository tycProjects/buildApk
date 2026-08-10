# SmoothCamera

A Kotlin, Camera2-based Android camera app that locks preview + video to
**60fps** and grabs zero-lag stills straight off the live preview.

## How the 60fps lock works

`MainActivity.pickFpsRangeAndSizes()` checks the camera in this order:

1. **Standard path (most phones since ~2017):** looks through
   `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` for a range that is exactly or
   contains 60fps, and locks `CONTROL_AE_TARGET_FPS_RANGE` to `(60,60)` on a
   normal `CameraCaptureSession`.
2. **High-speed fallback:** if the sensor doesn't expose a normal 60fps AE
   range, it looks at `getHighSpeedVideoFpsRanges()` and, if 60fps is
   supported there, opens a `CameraConstrainedHighSpeedCaptureSession` and
   submits the request as a high-speed burst (required by the Camera2 API
   for >30fps on those devices).
3. **Last resort:** uses the highest fps range the sensor actually reports,
   and tells the user their hardware can't guarantee 60fps.

Video is recorded with `MediaRecorder` using `setVideoFrameRate()` matched to
the locked range, H.264 encoding, and video stabilization turned on when the
sensor supports it.

Photos are captured directly from the `TextureView`'s live buffer
(`textureView.bitmap`), so there's no separate slow still-capture pipeline to
stall the 60fps preview — tap-to-shoot feels instant.

## Opening the project

1. Unzip this folder.
2. Open it in **Android Studio (Koala or newer recommended)**.
3. Let Gradle sync — Android Studio will generate the Gradle wrapper jar
   automatically the first time you sync/run (it's intentionally excluded
   from this zip to keep it small and text-only).
4. Run on a **physical device** (the emulator's virtual camera doesn't
   report real fps capabilities).

## Permissions

The app requests `CAMERA` and `RECORD_AUDIO` at runtime. Grant both, or the
app will close.

## Where files are saved

- Photos: `Android/data/com.example.smoothcamera/files/Pictures/`
- Videos: `Android/data/com.example.smoothcamera/files/Movies/`

(App-specific external storage — no extra permission needed on modern
Android, visible via a file manager or `adb pull`.)

## Notes / things you may want to tweak

- `TARGET_FPS` in `MainActivity.kt` — change to 30/120/240 if you want a
  different lock (120/240 will only work through the high-speed path on
  supported hardware).
- Bitrate is hardcoded to 16 Mbps in `startRecordingInternal()` — bump it up
  for higher-resolution 60fps footage.
- The layout uses stock Android drawables as button icons as placeholders —
  swap `@android:drawable/...` in `activity_main.xml` for your own icons.
