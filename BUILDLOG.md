# nhzterm — Build Log

Every build of this project gets an entry here, a version-stamped git commit,
a git tag (`vX.Y.Z`), and a matching zip copy in `../releases/` (workspace
root: `/home/user/releases/nhzterm-vX.Y.Z.zip`). This file is the project's
permanent memory of what was built, when, and what changed.

Versioning: `MAJOR.MINOR.PATCH` — MAJOR = stable public releases,
MINOR = feature/phase milestones, PATCH = fixes and small verified changes.

---

## v0.1.0 — 2026-09-03 — "nhzsh complete & verified"

**Commit:** `v0.1.0 — nhzsh (Part 2, Phases 0–8) rebuilt and fully verified; workspace established`
**Zip:** `nhzterm-v0.1.0.zip`

First numbered build of the canonical workspace.

### Contents
- **nhzsh** (C, `nhzsh/`): complete POSIX-subset shell per build plan —
  lexer, parser, expander, executor, state/REPL, daemon integration hook,
  `load`/`unload`/`list-libs` library system
- **Verification:** `make test` → ALL PHASES PASSED — 107 assertions across
  Phases 1–8; clean under ASAN/UBSAN with zero leaks; Phase 6 (daemon hook)
  and Phase 7 (`load`) — previously unconfirmed in the plan — now proven with
  real tests; Phase 8 real-script validation run, gap report generated
- **docs:** Phase 7 UI real-device verification kit (32-item sign-off
  checklist), STATUS log; concept doc + build plan (originals, unchanged)
- **app/, api/:** stubs pending the Kotlin port/rebuild decision
- **tooling:** `make android` NDK cross-compile target; `tools/release.sh`
  (commit + tag + zip in one step); this build log; `VERSION` file

### Known bugs fixed during this build (found by the tests themselves)
1. flags-array truncation (`strlen` on flag bytes where 0 is a valid value)
2. argv arrays not NULL-terminated → `execvp` EFAULT crashes
3. NULL-flags dereference in `command_clear` after expansion
4. lexer split `$(...)` at internal spaces/pipes (caught by Phase 8 scripts)

### Next up (carried forward)
1. Phase 7 UI fixes — real-device verification (user's phone, kit in docs/)
2. Part 1 Kotlin: port existing sources vs. rebuild fresh — decision pending
3. Integration Point re-verification on device (create/write/attach/kill +
   `process.kill` wired to nhzsh's now-proven foreground-PID frames)
4. Concept §14 open decisions: idle timeout, proot scope, kill-signal policy

---

## v0.2.0 — 2026-09-03 — Full Kotlin app: nhztermd daemon + nhzterm-api + terminal UI

**Zip:** `nhzterm-v0.2.0.zip`

Part 1 of the concept doc, written fresh in this workspace against the
doc's exact conditions (§3–§12). Division of labor: this workspace holds
all code; the user compiles via **Valence Studio** on-device (own-Gradle
tier — the root Gradle files are Valence's own input format for it, see
`docs/VALENCE-BUILD.md`).

### New in this build
- **`api/` module — nhzterm-api (§6):** zero-dep Android library.
  4-byte big-endian length + JSON frames over abstract LocalSocket,
  token handshake (`run/auth.token`), auto-starts `NhztermdService`,
  100×50 ms connect retry, every §6.3/§6.4 method, main-thread callbacks.
- **`nhztermd` daemon (§7–§9):** Foreground Service — notification
  posted before any boot work, START_STICKY, specialUse FGS type on
  API 34+. SessionManager (15-session cap, `meta.json`, 5000-line
  scrollback), PTY backend probe chain **tmux control-mode → screen
  hardcopy-poll → socat waitslave** with capability flags (resize not
  supported on socat — documented), ProcessManager (kill = SIGTERM →
  1.5 s → SIGKILL, targets nhzsh's live foreground PID over the
  control channel), ShellStager (SHA-256 content check, temp+rename,
  0755 `libnhzsh.so` → `system/bin/nhzsh`), env-script per session
  (HOME/TERM/COLORTERM/PATH/NHZSH_*), ControlServer + ApiServer
  (token + signature-permission guarded).
- **Terminal UI (§10–§12):** custom canvas renderer (no WebView, no
  AndroidX), VT parser/screen (byte-at-a-time, SGR 0–107/256-color,
  scroll regions, scrollback), extra-keys bar — row 1
  `≡ ESC TAB CTRL ALT HOME ↑ END` (≡ wired: tap=rows, long-press=panel;
  self-check enforces 8/7 keys), session side panel (+ auto-creates on
  empty — never a dead end), volume-key combos (§10.7), Ctrl (§10.8) +
  Ctrl+Alt (§10.9) tables, long-press menu (§10.3–10.6: copy/paste/
  open/share/refresh/kill-process/style/help/settings/report), URL
  detect without auto-click (§10.2), **25 themes** as JSON assets
  (§11) + user `etc/themes` override, 25 font names, in-app help from
  `assets/doc/README.md`.
- **`docs/VALENCE-BUILD.md`** — on-device build guide (own-Gradle tier
  detection, Gradle 8.7 auto-download, termux-ndk gate explanation,
  `make android` → jniLibs flow).

### Verification (sandbox has no Android toolchain — by design)
- 23 Kotlin files: balance checker clean; packages ↔ directories ↔
  imports all resolve; cross-file reference audit clean
- Review pass fixed 8 real bugs before release: VtParser state-handler
  infinite loop, ExtraKeysBar init-order NPE, wrong CTRL/ALT child
  indexes, `TerminalScreen.resize()` compile error, numpad Ctrl+Alt+N
  crash, DEL byte, missing Protocol import, unused imports
- nhzsh untouched (v0.1.0, `make test` all-green)

### Next
1. User builds APK via Valence Studio; paste back any compiler errors
2. `make android` → `libnhzsh.so` into `app/src/main/jniLibs/arm64-v8a/`
3. Real-device pass: `docs/ui-phase7-device-verification.md`

---

## v0.2.1 — 2026-09-03 — Build-guide fixes after first on-device build attempt

**Zip:** `nhzterm-v0.2.1.zip`

### What the first device build log revealed (diagnosis, not code bugs)
User's first Studio build failed — but it never compiled v0.2.0 code:
- Log paths were `app/src/main/java/tech/nhz/nhzterm/{daemon,ipc,
  session}/*.kt` — the **old pre-v0.2.0 on-device project** (package
  `tech.nhz.nhzterm`; files like `ipc/ProtocolHandler.kt`,
  `session/PtySession.kt` exist nowhere in this repo — verified 0 hits)
- Log showed `generated .../usr/tmp/valence-android-XXXX`, `entry
  public/index.html`, `engine Android System WebView` = Valence's
  **source/WebView template tier**, i.e. no `settings.gradle.kts` was
  found at the root of the opened folder
- Every error group follows from that: template tier flattens to one
  module (drops `api/` → `Unresolved reference: api/Protocol`),
  template namespace differs (`Unresolved reference: R`), no AndroidX
  (`setPriority` unresolved)
- aapt2 `No package ID 7f` lines = harmless Termux aapt2 noise
- Device SDK path observed: `~/.cartridge/android-sdk` (newer Valence
  layout, not `~/.valence/`)

### Fixes in this release (docs only, no code changes)
- `docs/VALENCE-BUILD.md`: new **Tier check** section (exact wrong-tier
  vs right-tier log signatures, "open the `nhzterm/` folder itself"
  instruction), `.cartridge` NDK path, known-harmless-noise section
- `app/src/main/jniLibs/README.md`: `.cartridge` NDK path

### Next
User re-builds with the v0.2.1 zip: unzip → open the `nhzterm/` folder
(settings.gradle.kts at top level) → build → expect `:api:` tasks and
`com/nhztech/nhzterm` paths in any diagnostics.

---

## v0.2.2 — 2026-09-03 — Studio-upload zip (archive-root layout); Valence source cloned & verified

**Zips:** `nhzterm-v0.2.2.zip` (full archive) + `nhzterm-v0.2.2-studio.zip` (Studio upload)

### Root-cause finding (Valence Studio cloned here, source-verified)
Cloned `github.com/nhztech/valence-studio` (commit `b039b1b`, "Valence v1")
into `/home/user/valence-studio` and read the actual build path:
- `server/local-builder.mjs` `handleBuild()`: upload zip →
  `extractCart(zip, <tmp>/project)` → tier check
  `existsSync(projectDir/settings.gradle[.kts])` (line 278)
- `cli/archive.mjs` `extractCart()`: extracts entries **as-is — no
  top-level-folder stripping**
- ⇒ Our release zips (which wrap everything in `nhzterm/`) would have
  failed tier detection too: settings.gradle.kts lands at
  `project/nhzterm/`, one level too deep → WebView template tier.
  This explains the failed device build completely (old project cart,
  same failure mode).

### Changes
- `tools/release.sh`: now emits BOTH zips — full archive (backup) and
  `-studio.zip` with project contents at the ARCHIVE ROOT (excludes
  `.git`, `*.o`, `nhzsh/nhzsh`, `nhzsh/build-android/`)
- `docs/VALENCE-BUILD.md`: upload-the-`-studio.zip` instructions with
  the source-verified reason; tier-check section updated

### Verified
- `-studio.zip` root listing shows `settings.gradle.kts` at zip root
- No symlinks anywhere in the zip (extractCart rejects them)
- Unpacked size ≪ archive.mjs 256 MiB limit

---

## v0.2.3 — 2026-09-03 — Compiler-verified fixes: package typo, Process.pid(), val-getter

**Zips:** `nhzterm-v0.2.3.zip` + `nhzterm-v0.2.3-studio.zip` (upload this)

### The v0.2.2-studio build reached real kotlinc — own-Gradle tier confirmed
working end-to-end (both modules configured, `:api` built clean, real
compiler errors returned verbatim). 18 errors in 4 files; 19 of 23
Kotlin files passed the compiler frontend with zero errors.

### Root causes (all mine, all fixed)
1. **ApiServer.kt line 1: `package com.nhzterm.nhzterm.daemon`** — typo
   (nhzterm vs nhztech) put ApiServer in a phantom package → all its
   same-package refs (SessionManager/ProcessManager/ApiClient/AuthToken/
   DaemonConfig) unresolved AND ApiServer itself unresolvable from
   NhztermdService (incl. the cascade `it` error). 15 of 18 errors.
   Lesson: eyeballed package audit missed it; audit is now a script.
2. **ProcessManager.kt: `p.pid()`** — java.lang.Process.pid() is Java 9+
   and does NOT exist on Android's libcore. Replaced with reflection on
   ProcessImpl's private `pid` field (Termux's technique), -1 fallback
   (kill(pid<=0) already rejects gracefully).
3. **TerminalScreen.kt:43: `var grid ... get()`** — a getter-only `var`
   without backing field is illegal; must be `val`. Verified nothing
   reassigns it.

### Verified after fixes
- Automated package↔directory audit: 0 mismatches (23 files)
- Balance check: 23/23 OK
- ApiServer's cross-file member usage (sessions.attach/create/
  detachClient/kill/list/rename/resize/write, processes.spawn/status/
  stop/list/kill) all match actual declarations

### Valence Studio side (diagnosed from cloned source — it behaved)
- own-Gradle detection, extractCart, dual-module build, Termux SDK +
  aapt2 override, compiler-error passthrough: all worked
- Observations for NHZTech: UI regenerates valence.json from the form
  (showed v1.0.0 — harmless, Gradle uses build.gradle.kts versionName);
  packed 125 files vs 111 in the zip (leftover workspace files merge
  in — inert but bloat); extractCart doesn't strip single top-level
  folder; buildlog console duplicated the tail (polling offset glitch)

---

## v0.3.0 — 2026-09-03 — Real-device fix wave: native PTY backend, extra-keys layout, ≡ wiring, pinch zoom

**Zips:** `nhzterm-v0.3.0.zip` + `nhzterm-v0.3.0-studio.zip`

### First on-device run of the APK exposed 5 problems — all fixed against
### the concept doc / build plan (docs re-read before touching code)

1. **NO SESSIONS ON STOCK DEVICES (root cause of "not responsive, no
   input").** The PTY probe only knew tmux→screen→socat; a stock phone
   has none of them, so `session.create` could never work — blank
   terminal, every keystroke dropped. Concept §4 position #4 (compiled
   native forkpty helper) was "deferred" — no longer:
   - NEW `app/src/main/jni/ptyhelper.c` — forkpty+execv, read/write,
     TIOCSWINSZ resize, kill, waitpid, close (full gcc type-check passed
     against a faithful JNI stub; NDK build is the final gate)
   - NEW `daemon/PtyHelper.kt` — JNI bindings (optional load; probe
     skips cleanly when the .so is absent)
   - NEW `NativePty` backend in `Pty.kt` — byte-transparent, real
     resize, exit detection via master-fd EIO, zombie reaping,
     SIGTERM→1.5s→SIGKILL kill policy. Reattach honestly unsupported
     (children die with the daemon, same as socat)
   - NEW `tools/build-native-android.sh` — one Termux command builds
     libptyhelper.so + libnhzsh.so for all 3 ABIs into jniLibs/
2. **"Runs sh, not nhzsh"** — expected while jniLibs is empty (stager
   has nothing to stage). Fix = the build script above; docs updated to
   call the no-.so state diagnostic-only.
3. **Extra keys = "one overlay block, not per key"** — Material Button
   defaults minWidth=88dp/minHeight=48dp; 8 keys need 700+dp and
   smeared across the screen. Zeroed all minimums + stateListAnimator;
   weight=1 params now solely size the keys → equal, aligned blocks.
4. **≡ rewired to user expectations (§10.6 allows either):**
   tap = open/close session side panel (§10.4); long-press = toggle
   rows; Vol Up+Q/K unchanged; solo-strip ≡ tap restores rows.
   Back button now closes the panel before leaving the app.
5. **Termux-style pinch zoom** — ScaleGestureDetector on TerminalView,
   live 0.4x–4.0x text resize, persisted via settings.textZoom (same
   store as Ctrl+Alt +/- and the Style menu). Two-finger touch cancels
   long-press. In-app help updated.

### Verified here
- package audit 0/24 mismatches; balance 24/24; ptyhelper.c full type
  check; all new cross-references resolved by grep audit
- NOT verified here (no device/NDK): the NDK compile of ptyhelper.c and
  the real-device PTY session — `sh tools/build-native-android.sh` +
  Studio build is the gate; then run docs/ui-phase7-device-verification.md

### Next
1. On device: `sh tools/build-native-android.sh` → rebuild in Studio
2. Install, verify: session auto-creates, prompt is nhzsh, input echoes,
   ≡ opens panel, extra keys aligned, pinch zooms
3. Phase 7 verification kit sign-off
