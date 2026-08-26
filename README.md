# Video Metadata Editor (Android / Kotlin)

A native Android app for reading and fully rewriting video metadata: title,
artist, album, comment, genre, date/creation time, GPS location, and any
custom key/value tag ffmpeg supports (per-format metadata). It can also wipe
all existing metadata before writing new values — full control, in one app.

## How it works

- Pick any video with the system file picker (SAF) — no storage permission
  needed.
- The app runs `ffprobe` (via FFmpegKit) to list every metadata tag already
  on the file, at both the container and per-stream level, and pre-fills the
  editable fields.
- When you hit **Save**, it remuxes the file with `ffmpeg -c copy` (stream
  copy — no re-encoding, no quality loss, fast) while injecting your edited
  `-metadata key=value` pairs, optionally after `-map_metadata -1` to strip
  everything else first.
- Output is written back through SAF via `ACTION_CREATE_DOCUMENT`, so you
  choose where the edited file is saved.

This uses ffmpeg under the hood specifically because hand-editing MP4/MOV
container boxes (moov/udta/meta atoms, chunk offset tables, etc.) is easy to
get subtly wrong and corrupt files. ffmpeg handles that bookkeeping for you
while still giving you arbitrary metadata keys — genuinely "full control"
without the risk.

## Opening the project

1. Unzip this project.
2. Open the folder in Android Studio (Koala/2024.x or newer recommended).
3. Let Gradle sync — it will download AndroidX, Material, and FFmpegKit.
4. Run on a device or emulator (minSdk 24 / Android 7.0+).

## Important note on the FFmpegKit dependency

This project originally used `com.arthenica:ffmpeg-kit-full:6.0-2`. The
upstream FFmpegKit project was retired by its maintainer, and **all of its
binaries were pulled from Maven Central on April 1, 2025** — that coordinate
now fails every build with `Could not find com.arthenica:ffmpeg-kit-full:6.0-2`.

This project now points at `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`
instead — a community rebuild of the same upstream source, still published
under the original `com.arthenica.ffmpegkit` package, so no Kotlin code
changes were needed, only the dependency coordinate.

Community forks are less stable than an official release, so if this one
ever stops resolving too:

- Check https://github.com/moizhassankh/ffmpeg-kit-android-16KB for a newer
  version, or
- Search "ffmpeg-kit fork maven" for other active rebuilds.

None of the Kotlin code needs to change for a coordinate swap — the app only
calls the standard `FFmpegKit` / `FFprobeKit` / `ReturnCode` API surface.

If you'd rather not depend on ffmpeg at all, the alternative is to hand-edit
MP4 boxes directly with a library like `org.mp4parser:isoparser`, but that
only covers the MP4/MOV family (no MKV/AVI/etc.), requires careful handling
of `stco`/`co64` chunk-offset tables whenever the `moov` box size changes,
and is considerably more code for the same result.

## Extending it further

The custom-field UI already lets you set arbitrary metadata keys, but a few
things are easy to bolt on if you want even more control:

- **Per-stream metadata**: add `-metadata:s:v:0 key=value` / `-metadata:s:a:0
  key=value` args instead of (or alongside) the global `-metadata` args.
- **Chapters**: ffmpeg can read/write chapter markers via a metadata file
  passed with `-i chapters.txt -map_metadata 1`.
- **Disposition flags** (e.g. marking a track as "default" or "forced"):
  `-disposition:s:0 default`.

## Files

```
VideoMetadataEditor/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/videometaeditor/MainActivity.kt
        └── res/
            ├── layout/activity_main.xml
            └── values/{strings.xml, themes.xml}
```
