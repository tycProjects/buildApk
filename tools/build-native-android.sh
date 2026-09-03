#!/bin/sh
# Build BOTH native libraries and place them into the APK's jniLibs.
# Run this in Termux on the device, from anywhere:
#
#   sh tools/build-native-android.sh
#
# Produces (per ABI):
#   app/src/main/jniLibs/<abi>/libptyhelper.so  — §4 #4 native forkpty PTY
#                                                 (sessions on stock devices!)
#   app/src/main/jniLibs/<abi>/libnhzsh.so      — the nhzsh shell itself
#
# Then rebuild the APK in Valence Studio (upload a fresh -studio.zip or
# rebuild the project dir) — Gradle packages jniLibs/ automatically.
#
# NDK source: Valence's termux-ndk (~/.cartridge/android-ndk on newer
# installs, ~/.valence/android-ndk on older ones — a Valence Studio build
# auto-installs it once). Override with ANDROID_NDK_HOME if elsewhere.
set -eu

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

NDK="${ANDROID_NDK_HOME:-}"
if [ -z "$NDK" ]; then
    for c in "$HOME/.cartridge/android-ndk" "$HOME/.valence/android-ndk"; do
        [ -d "$c" ] && NDK="$c" && break
    done
fi
[ -n "$NDK" ] && [ -d "$NDK" ] || {
    echo "error: no NDK found. Either run a Valence Studio build once"
    echo "       (auto-installs termux-ndk) or: export ANDROID_NDK_HOME=/path"
    exit 1
}
echo "==> NDK: $NDK"

# ---- libptyhelper.so (app/src/main/jni/ptyhelper.c) -------------------
for abi in arm64-v8a armeabi-v7a x86_64; do
    case $abi in
        arm64-v8a)   triple=aarch64-linux-android24 ;;
        armeabi-v7a) triple=armv7a-linux-androideabi24 ;;
        x86_64)      triple=x86_64-linux-android24 ;;
    esac
    clang=$(find "$NDK/toolchains/llvm/prebuilt" -name "$triple-clang" 2>/dev/null | head -1)
    [ -n "$clang" ] || { echo "error: no $triple-clang under $NDK"; exit 1; }
    out="app/src/main/jniLibs/$abi"
    mkdir -p "$out"
    echo "==> libptyhelper.so ($abi)"
    "$clang" -std=c11 -D_GNU_SOURCE -Wall -O2 -fPIC -shared \
        app/src/main/jni/ptyhelper.c -o "$out/libptyhelper.so"
done

# ---- libnhzsh.so (nhzsh/ via its own Makefile) ------------------------
echo "==> libnhzsh.so (all ABIs via nhzsh make android)"
(cd nhzsh && make android ANDROID_NDK_HOME="$NDK")
for abi in arm64-v8a armeabi-v7a x86_64; do
    if [ -f "nhzsh/build-android/$abi/libnhzsh.so" ]; then
        mkdir -p "app/src/main/jniLibs/$abi"
        cp "nhzsh/build-android/$abi/libnhzsh.so" "app/src/main/jniLibs/$abi/"
    fi
done

echo
echo "==> done. jniLibs contents:"
find app/src/main/jniLibs -name "*.so" | sort
echo
echo "Now rebuild the APK in Valence Studio. On next daemon start:"
echo "  - ShellStager copies libnhzsh.so -> system/bin/nhzsh (shell = nhzsh)"
echo "  - PTY probe selects the native helper if tmux/screen/socat are absent"
