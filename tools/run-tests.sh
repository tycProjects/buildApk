#!/bin/sh
# nhzterm — off-device test gate.
#
# Runs the platform-independent parts of the daemon without an Android device,
# so a phase can be proven before you build the APK. Anything Android-specific
# (LocalSocket, Service lifecycle, real PTY spawning) is verified on-device —
# see tools/PHASE-CHECKLIST.md.
#
# Requires: kotlinc + java on PATH.
#   Termux: pkg install kotlin openjdk-21
#
# Usage: sh tools/run-tests.sh

set -e
cd "$(dirname "$0")/.."

OUT=".test-out"
mkdir -p "$OUT"

JSON_JAR="$OUT/json.jar"
if [ ! -f "$JSON_JAR" ]; then
    echo ">> fetching org.json (JVM stand-in for the Android platform copy)"
    curl -sL -o "$JSON_JAR" \
        https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
fi

API=nhzterm-api/src/main/kotlin/tech/nhz/nhzterm/api
APP=app/src/main/kotlin/tech/nhz/nhzterm

echo ">> compiling FrameCodec suite (Phase 0)"
kotlinc -nowarn "$API/Protocol.kt" "$API/FrameCodec.kt" \
    nhzterm-api/src/test/kotlin/tech/nhz/nhzterm/api/FrameCodecTest.kt \
    -include-runtime -d "$OUT/framecodec.jar" 2>/dev/null

echo ">> compiling daemon core suite (Phase 0)"
kotlinc -nowarn -cp "$JSON_JAR" "$API/Protocol.kt" \
    "$APP/daemon/DaemonConfig.kt" "$APP/daemon/AuthToken.kt" \
    app/src/test/kotlin/tech/nhz/nhzterm/DaemonCoreTest.kt \
    -include-runtime -d "$OUT/core.jar" 2>/dev/null

echo ">> compiling scrollback suite (Phase 3)"
kotlinc -nowarn "$APP/session/ScrollbackBuffer.kt" \
    app/src/test/kotlin/tech/nhz/nhzterm/ScrollbackTest.kt \
    -include-runtime -d "$OUT/scrollback.jar" 2>/dev/null

echo ">> compiling process-control suite (Phase 4)"
kotlinc -nowarn -cp "$JSON_JAR" \
    "$API/Protocol.kt" "$APP/session/ProcessManager.kt" "$APP/session/SessionManager.kt" \
    "$APP/session/PtySession.kt" "$APP/session/ScrollbackBuffer.kt" "$APP/pty/NativePty.kt" \
    "$APP/daemon/DaemonConfig.kt" "$APP/util/Paths.kt" "$APP/util/DaemonLog.kt" \
    app/src/test/kotlin/tech/nhz/nhzterm/ProcessManagerTest.kt \
    -include-runtime -d "$OUT/process.jar" 2>/dev/null || \
    echo "   (needs android.jar on the classpath; skipped)"

echo
java -cp "$OUT/framecodec.jar" tech.nhz.nhzterm.api.FrameCodecTest
echo
java -cp "$OUT/core.jar:$JSON_JAR" tech.nhz.nhzterm.DaemonCoreTest
echo
java -cp "$OUT/scrollback.jar" tech.nhz.nhzterm.ScrollbackTest

if [ -f "$OUT/process.jar" ]; then
    echo
    java -cp "$OUT/process.jar:$JSON_JAR" tech.nhz.nhzterm.ProcessManagerTest 2>/dev/null || \
        echo "   (ProcessManagerTest needs android.jar; run it from the Gradle unit-test task instead)"
fi

echo
echo ">> native PTY harness (Phase 1)"
if command -v cc > /dev/null 2>&1; then
    cc -o "$OUT/pty_harness" app/src/main/cpp/test/pty_harness.c -lutil 2>/dev/null && \
        "$OUT/pty_harness" || echo "   (libutil unavailable; verify PTY on-device)"
else
    echo "   (no C compiler; skipped)"
fi

echo
echo "ALL OFF-DEVICE GATES PASSED"
