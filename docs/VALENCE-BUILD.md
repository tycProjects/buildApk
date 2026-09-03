# Building nhzterm with Valence Studio (on-device)

Verified against the `valence-studio` source itself (2026-09). The Gradle
files at this repo root (`settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, `app/`, `api/`) are **Valence's own input format**
for its native "own-Gradle" build tier — not a separate toolchain.

## How Studio sees this repo

`server/local-builder.mjs` checks for `settings.gradle(.kts)` at the
project root. Finding one, it switches to the own-Gradle tier:

1. Uses `./gradlew` if present → else system `gradle` → else
   **auto-downloads Gradle 8.7 once (~120 MB)** into its cache.
2. Writes `local.properties` (`sdk.dir=~/.valence/android-sdk`, plus
   `ndk.dir` when an NDK is installed).
3. Runs `assembleDebug --no-daemon`.
4. Returns the first `*.apk` found under a `debug/` directory — typically
   `app/build/outputs/apk/debug/app-debug.apk`.

This tier preserves our `AndroidManifest.xml` and `jniLibs/` untouched
(the WebView/source tiers would stamp their own manifest).

## Step by step

```sh
# 1. Upload nhzterm-vX.Y.Z-studio.zip to Valence Studio's web UI.
#    MUST be the -studio.zip: it has settings.gradle.kts at the ZIP
#    ROOT. Verified in Valence source (cli/archive.mjs extractCart +
#    server/local-builder.mjs:278): Studio extracts the upload AS-IS —
#    it does NOT strip a top-level folder — and checks for
#    settings.gradle(.kts) at the extracted root. The full release zip
#    wraps everything in nhzterm/, which buries settings.gradle.kts one
#    level too deep and silently falls back to the WebView tier.

# 2. Build. First build in a fresh Termux env will auto-install
#    termux-ndk — EXPECTED, see "The NDK gate" below. Let it finish.

# 3. Install the APK Studio produces and launch nhzterm.
```

## ⚠️ Tier check — make sure Studio picked the own-Gradle tier

Valence has several build tiers. Only own-Gradle builds this repo
correctly. Check the first lines of the build log:

**WRONG tier (source/WebView template):**
```
[valence] building APK (sdk: ...)
generated /data/data/com.termux/files/usr/tmp/valence-android-XXXXXX   <-- template generated in /tmp
entry     app/src/main/assets/public/index.html                        <-- WebView entry?!
engine    Android System WebView (Chromium provider)                   <-- WebView engine?!
```
This means Studio did NOT find `settings.gradle.kts` at the root of the
folder you opened. It flattens sources into a single-module template,
drops the `api/` module, and stamps its own manifest — builds of
nhzterm code through this tier fail with `Unresolved reference: api /
Protocol / R`, which are symptoms of the wrong tier, not code bugs.

**RIGHT tier (own-Gradle):** no `entry`/`engine`/`generated ...tmp`
lines at all; Gradle runs directly in your project folder; tasks
include `:api:...` (two modules) and any diagnostics reference
`com/nhztech/nhzterm/...` paths.

If you see the wrong-tier lines: you uploaded the full release zip
instead of the `-studio.zip`, or an old pre-v0.2.0 project cart.
Re-upload `nhzterm-vX.Y.Z-studio.zip`.

## The NDK gate (first build)

Studio scans the whole project for C/C++ sources before building.
`nhzsh/` contains `.c` files, so a build throws
*"project contains C/C++/CMake — NDK not installed"* until Valence's NDK
exists. In the Termux environment Studio **auto-installs termux-ndk**
itself and continues. This is expected on the first build — it is not an
error in this repo, and the same NDK then serves `make android` for
nhzsh below.

## Adding the native libraries (REQUIRED for real sessions)

Two `.so` files per ABI belong in `app/src/main/jniLibs/<abi>/`:
`libptyhelper.so` (real forkpty PTYs — **without it, stock devices with
no tmux/screen/socat cannot create sessions at all**, §12.3) and
`libnhzsh.so` (the nhzsh shell). One command in Termux:

```sh
cd nhzterm
sh tools/build-native-android.sh     # builds + places both, all ABIs
```

It uses Valence's own NDK (`~/.cartridge/android-ndk`, or
`~/.valence/android-ndk` on older installs — installed automatically by
the first Studio build). Then rebuild in Studio — Gradle packages
`jniLibs/` into the APK automatically; no CMake step exists.

The app still builds and launches without the `.so` files — but on a
stock phone it will have no PTY backend and no nhzsh; treat that state
as diagnostic-only, not shippable.

## Known harmless noise

`aapt2: No package ID 7f found for resource ID 0x7f0300XX` lines during
`processDebugResources` come from Termux's `aapt2` binary
(`aapt2FromMavenOverride`) — noisy but non-fatal; the build continues
past them.


## valence.json

Included for Studio metadata (schema format 1: name/id/version/
versionCode/min 26/target 34, notifications permission, no network).
The own-Gradle tier does not require it; it costs nothing and keeps
`valence validate` happy.

## If the build fails

Paste the exact error back — the own-Gradle tier returns Gradle's real
compiler output, so Kotlin/manifest diagnostics come through verbatim.
The repo has no sandbox-side toolchain; Studio is the compiler loop.
