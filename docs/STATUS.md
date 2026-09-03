# nhzterm — Workspace Status Log

Living status for the canonical workspace copy of the project. The original
build plan (`nhzterm-nhzsh-build-plan.md`) remains the authoritative plan;
this log records what has been rebuilt/verified **in this workspace**.

---

## 2026-09-03 (v0.2.0) — Full Kotlin app written: daemon + API + terminal UI

Part 1 rebuilt fresh in this workspace (prior on-device sources were never
shared). **All code complete on disk**; compilation happens on-device via
Valence Studio's own-Gradle tier (`docs/VALENCE-BUILD.md`) — the sandbox
has no Android toolchain by design, and the user builds the APKs.

### What exists now
- `api/` — **nhzterm-api** client library (zero-dependency module):
  4-byte BE length + JSON framing, token handshake, auto-starts the
  daemon, all §6.3/§6.4 methods, main-thread callbacks
- `app/` daemon (`daemon/`) — `NhztermdService` (FGS, notification
  first, START_STICKY, specialUse type on 34+), `SessionManager`
  (cap 15, meta.json, scrollback 5000), `Pty` (tmux control-mode →
  screen hardcopy-poll → socat waitslave probe chain + PtyProbe),
  `ProcessManager` (SIGTERM→1.5s→SIGKILL, foreground-PID via nhzsh
  control channel), `ControlServer` + `ApiServer` (abstract
  LocalSockets, token + signature-permission guarded), `ShellStager`
  (atomic content-checked `libnhzsh.so` → `system/bin/nhzsh`),
  `RuntimeDirs` (assets/themes → etc/themes seeding)
- `app/` UI (`ui/`) — `MainActivity` (§10.3–10.9 menus, volume-key
  combos, Ctrl / Ctrl+Alt tables, link picker), `TerminalView`
  (custom canvas renderer, selection, URL detect without auto-click),
  `VtParser` + `TerminalScreen` (VT100/xterm subset, strict
  byte-at-a-time, scrollback), `ExtraKeysBar` (row1 `≡ ESC TAB CTRL
  ALT HOME ↑ END` with ≡ wired; row2 `/ - PGUP PGDN ← ↓ →`),
  `SessionPanel` (auto-create on empty — no dead end), `ThemeRegistry`
  (25 themes as JSON assets + user `etc/themes` override), `FontRegistry`
  (25 names), `SettingsStore`
- `assets/` — 25 theme JSONs, in-app help (`doc/README.md`)
- `jniLibs/README.md` — `make android` procedure (binaries added
  on-device; app runs without them via /system/bin/sh fallback)
- `valence.json` — schema-format-1 metadata for Studio

### Verification done here (no compiler in sandbox)
- 23 Kotlin files: brace/paren/bracket balance clean; all packages
  match directories; all cross-package references resolve
- Self-review pass caught & fixed: VtParser infinite-loop state bug,
  property-init-order NPE (ExtraKeysBar), wrong CTRL/ALT child indexes
  (3/4, not 4/5), `resize()` compile error, numpad Ctrl+Alt digit
  crash, DEL byte handling, unused imports
- nhzsh unchanged since v0.1.0 (all tests still green)

### Open items (resume order)
1. **User builds v0.2.0 APK via Valence Studio** (`docs/VALENCE-BUILD.md`)
   and reports any compiler errors — Studio is the compiler loop.
2. Cross-compile `libnhzsh.so` (`make android`) → jniLibs → rebuild.
3. Run `docs/ui-phase7-device-verification.md` on the real device.
4. Concept §14: idle timeout + proot scope still open (kill policy is
   implemented: SIGTERM→1.5s→SIGKILL).

---

## 2026-09-03 — Workspace established; nhzsh rebuilt end-to-end and fully verified

### What this workspace now contains
- `docs/` — concept doc + build plan (unchanged originals) + this log + the new
  **Phase 7 real-device verification kit** (`ui-phase7-device-verification.md`)
- `nhzsh/` — the complete C shell (Part 2, Phases 0–8), built fresh from the plan
- `app/`, `api/` — scaffolded stubs (Kotlin daemon + client library not yet
  rebuilt in this workspace — see Open Items)

### nhzsh (Part 2) — status: ✅ complete and verified here
Built fresh from the build plan and verified with the plan's own discipline
("no phase starts before the previous has real passing tests"):

| Phase | Status | Evidence |
|---|---|---|
| 0 Skeleton | ✅ | repo layout per plan; echo-loop superseded by full shell |
| 1 Lexer | ✅ | `test_lexer.c` — 22 checks |
| 2 Parser | ✅ | `test_parser.c` — 20 checks |
| 3 Expander | ✅ | `test_expander.c` — 17 checks |
| 4 Executor | ✅ | `test_executor.c` — 21 checks |
| 5 State & REPL | ✅ | `test_shell.sh` — 9 checks |
| 6 Daemon hook | ✅ **now confirmed** (was ❓ unconfirmed) | `test_daemon_link.c` — 8 checks against a stub `nhztermd` listener: `foreground_pid` frames arrive pid→(alive)→null, in order, around a real command's lifetime |
| 7 `load` builtin | ✅ **now confirmed** (was ❓ unconfirmed) | `test_load.sh` — 9 checks: search path, no-op reload, `as` wrapper, unload |
| 8 Real-script validation | ✅ **now run** (was ❓ not yet run) | `run_phase8.sh` + generated `tests/phase8_report.md`; gap probe documents unsupported constructs |

- `make test` → **ALL PHASES PASSED** (107 unit checks + script validations)
- Clean under `-fsanitize=address,undefined` + LeakSanitizer (zero leaks)
- Bugs found & fixed during verification (all were real): flags-array
  truncation (`strlen` on flag bytes where 0 is valid), missing argv NULL
  terminators (EFAULT from `execvp`), post-expansion free of NULL flags array,
  and lexer splitting `$(...)` at internal spaces/pipes (caught by Phase 8's
  real scripts — the process working as designed)

### Notable design decisions made while building (documented in code)
- `as <alias>` wrapper maps to the library's `<name>_main` entry-point alias
  (concept §5.4 doesn't fix the convention; this is concrete + tested)
- Multi-stage pipelines report the **last stage's** PID to the daemon;
  process-group kill semantics arrive with job control (explicitly post-v1)
- Expansion order is bash-correct (tilde → vars → cmdsub → split → glob);
  the plan's listed order is followed as test sequencing, not runtime order
- Control-channel framing = nhzterm-api §6.1 (4-byte BE length + JSON), for
  protocol consistency

### Open items (resume order)
1. **Phase 7 UI fixes — real-device verification** (user's phone): run
   `docs/ui-phase7-device-verification.md` top to bottom. Cannot be done from
   this workspace (no device/Android SDK).
2. **Part 1 Kotlin app**: not yet rebuilt in this workspace. Decision needed —
   port the existing on-device sources here, or rebuild fresh. The verified
   `nhzsh` here is the same binary the Integration Point expects
   (`DaemonConfig.defaultShell = "nhzsh"` → staged `system/bin/nhzsh`).
3. **Integration re-verification on device**: re-run Part 1 Phase 3's
   create/write/attach/kill test against real `nhzsh` (per the build plan's
   Integration Point note), and wire the now-proven `foreground_pid` frames
   into `process.kill` end-to-end (kit items C4/C5).
4. Open decisions from concept §14 (idle timeout, proot scope, kill-signal
   policy) still open.
