# nhzterm

A standalone terminal daemon with a custom shell, exposing a documented API so
any application can spawn and control real PTY sessions without owning a UI or
a shell of its own.

**Status:** Part 1 Phases 0-8 and Part 2 (`nhzsh`) Phases 0-8 code-complete.
336 automated assertions pass off-device. On-device verification is the
remaining work — see [`tools/PHASE-CHECKLIST.md`](tools/PHASE-CHECKLIST.md).

Three things, not one:

| Component | What it is |
|---|---|
| `nhztermd` | Headless Android Foreground Service owning real PTY sessions and `nhzsh` |
| `nhzterm-api` | Documented protocol/SDK any app uses to talk to the daemon |
| `nhzterm` (UI) | Reference client (WebView + xterm.js) — *one consumer of the API*, not a requirement |

Valence Studio is just another `nhzterm-api` client, not the terminal's owner.

---

## Building

The Gradle and Android SDK toolchain is **already solved by the Valence
Framework** — this repo deliberately contains no SDK-detection, SDK-install, or
Gradle-bootstrap logic (build plan Part 1, Phase 0.2).

```sh
# once, if not already done — installs SDK 34 into ~/.valence/android-sdk
# and writes the android.aapt2FromMavenOverride property
sh setup-termux-android.sh

cp local.properties.template local.properties   # edit if your path differs
./gradlew assembleDebug
```

The `aapt2` override matters: Google's Maven `aapt2` is x86-64-only
(VALENCE-DOCS §15/§17). Valence's setup script writes the override into
`~/.gradle/gradle.properties`; this project intentionally does **not** redefine
it in-project, which would shadow it.

## Testing

```sh
sh tools/run-tests.sh      # off-device gates: framing, config, auth
```

```sh
cd ../nhzsh && make test   # nhzsh: all 8 phase gates
```

Requires `kotlinc` and `java` (`pkg install kotlin openjdk-21`), plus `cc` for
the native PTY harness. 336 assertions across 12 suites, all passing.

The PTY harness is the one that matters most: it exercises `forkpty` +
`termios` + `TIOCSWINSZ` against a **real kernel**, proving `test -t 0` returns
true (a genuine controlling terminal, not a pipe) and that SIGWINCH is
delivered. That is §2 principle 2 demonstrated, not asserted.

Android-specific behaviour (LocalSocket, Service lifecycle, JNI PTY spawning)
can only be verified on hardware — see
[`tools/PHASE-CHECKLIST.md`](tools/PHASE-CHECKLIST.md).

## Layout

```
app/                        the Android app: daemon + reference UI
  daemon/NhztermdService    foreground service, lifecycle, notification (§7)
  daemon/DaemonConfig       etc/nhztermd.json, §14 decisions as config
  daemon/AuthToken          256-bit token, constant-time compare (§13)
  ipc/SocketListener        abstract-namespace LocalSocket accept loop
  util/Paths                runtime dir tree (§12.2)
  util/DaemonLog            logcat + rotating var/log/nhztermd.log
  ui/TerminalActivity       Phase 0 placeholder; real UI is Phase 7
  pty/PtyProbe              tmux -> screen -> socat -> JNI probe (§4)
  pty/NativePty             JNI bridge to libptyhelper.so
  cpp/ptyhelper.c           forkpty() + exec, the real PTY (§4)
  session/PtySession        one PTY-backed session, reader thread
  session/SessionManager    15-session cap, metadata persistence (§8, §12.2)
  session/ScrollbackBuffer  5000-line ring buffer (§8)
  session/ProcessManager    process.* methods (§6.4)
  ipc/ProtocolHandler       handshake + method dispatch (§6.2-§6.6)
  ipc/Connection            per-client framing + writer queue
  ui/TerminalActivity       WebView host + native menu actions (§10)
  ui/TerminalBridge         JavascriptInterface bridge (§3)
  assets/                   xterm.js UI, themes, help doc
nhzterm-api/                the client library, consumed by external apps
  api/Protocol              versions, methods, error codes, limits (§6, §8)
  api/FrameCodec            4-byte BE length prefix + UTF-8 JSON (§6.1)
  api/NhztermClient         documented SDK — what Valence Studio imports
tools/                      test runner + phase checklist
```

## Before it will run on a phone

Three things this repo cannot do for you:

1. **Vendor xterm.js** — drop `xterm.js` and `xterm.css` into
   `app/src/main/assets/`. Deliberately not fetched from a CDN (§2.1).
2. **Cross-compile the shell** — `cd ../nhzsh && make android
   NDK=$ANDROID_NDK_HOME ABI=arm64-v8a`, then copy `libnhzsh.so` into
   `app/src/main/jniLibs/arm64-v8a/`. Until then sessions fall back to `sh`
   (logged, degraded, not fatal).
3. **Run the on-device checklist** — nothing Android-specific has touched real
   hardware yet.

## Resolved open decisions (§14)

Locked in as **config**, not constants, so they change without a rebuild
(`etc/nhztermd.json`):

1. **Session idle timeout** — none. Sessions live until explicitly killed,
   matching tmux. A long build must never be reaped because the phone was in a
   pocket. (`session_idle_timeout_ms: 0`)
2. **First target environment** — Termux only for v1.
3. **Kill signal policy** — `SIGTERM`, then `SIGKILL` after a 3s grace, so
   interactive programs can restore the terminal and flush state.
   (`kill_grace_ms: 3000`)

## Design constraints (non-negotiable, §2)

1. No web-tech dependency in the core.
2. Real PTY from day one — `vim`/`htop` must work in v1, no pipe-mode.
3. Headless-first — sessions persist with zero clients attached.
4. Zero manual daemon management — autospawned by whichever client touches it.
5. POSIX-compatible shell grammar — existing `.sh` scripts keep working.
6. Standalone identity — own name, own repo, own versioning.
