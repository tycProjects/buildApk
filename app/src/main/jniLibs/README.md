# jniLibs — prebuilt native binaries (concept doc §12.1)

This directory ships **prebuilt** native libraries per ABI. There is no
CMake/NDK step in the app's Gradle build — the `.so` files are
cross-compiled separately and dropped in here; Gradle just packages
them.

## Two libraries

| Library | Source | Job |
|---|---|---|
| `libptyhelper.so` | `../jni/ptyhelper.c` | §4 #4: real `forkpty()` PTYs via JNI — **this is what makes sessions work on stock devices** (no tmux/screen/socat needed). Full resize support. |
| `libnhzsh.so` | `../../../nhzsh/` | the nhzsh shell; `ShellStager` copies it to `system/bin/nhzsh` at daemon startup |

Without `libptyhelper.so` for the device's ABI, the PTY probe falls back
to tmux/screen/socat — and if none exist (normal on a stock phone),
**sessions cannot be created at all** (§12.3). Without `libnhzsh.so`,
sessions still run, but on `/system/bin/sh` instead of nhzsh.

## Building (one command, Termux on the phone)

```sh
cd nhzterm
sh tools/build-native-android.sh
```

The script finds Valence's NDK (`~/.cartridge/android-ndk`, or
`~/.valence/android-ndk` on older installs — a Valence Studio build
auto-installs it once), compiles `libptyhelper.so` for all three ABIs,
runs `make android` for nhzsh, and copies every result into
`jniLibs/<abi>/`. Then rebuild the APK in Studio.

## Expected layout

```
jniLibs/
├── arm64-v8a/     libptyhelper.so + libnhzsh.so   <- required (§12.3)
├── armeabi-v7a/   ...                             <- optional legacy
└── x86_64/        ...                             <- optional emulator
```

Android's installer extracts these into the app's native library
directory — the one exec-permitted location — which is precisely what
sidesteps the Android 10+ execute-permission restriction (§12.1).
