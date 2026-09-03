# nhzterm + nhzsh — Step-by-Step Build Plan

This document tells Claude Code exactly what to build, in what order, and how to know each step actually worked. It reflects the current, correct state of the plan — read it top to bottom as the real instructions, not as a changelog of how the plan evolved.

**Order of operations:** build `nhzterm` first (Part 1 — the Kotlin app, its Foreground Service daemon, and the native PTY/`nhzsh` layer reached via JNI), so there is a working terminal to actually run and test things in along the way, using a placeholder `sh` inside sessions until `nhzsh` itself is ready. Build `nhzsh` second (Part 2 — pure C). At the marked **Integration Point**, the placeholder `sh` is swapped for the real, staged `nhzsh` binary — that is the one and only place these two halves of the project actually connect.

---

## Current Status — Read This Before Doing Anything Else

| Phase | Status | Notes |
|---|---|---|
| Part 1, Phase 0 — Project skeleton | ✅ Done | Kotlin project and the `nhzterm-api` client module both exist |
| Part 1, Phase 1 — PTY acquisition | ✅ Done | `tmux`/`screen`/`socat` probe implemented and confirmed working; `nhzsh` staging to `system/bin/nhzsh` (via `ShellStager`) is implemented, wired into `SessionManager.resolveShell()`, and has a dedicated test (`ShellStagerTest`) — reviewed directly in source and confirmed correct |
| Part 1, Phase 2 — Handshake & auth | ✅ Done | Token handshake confirmed working, uses constant-time comparison |
| Part 1, Phase 3 — Session control | ✅ Done at the daemon level | `session.create`/`attach`/`list`/`kill`/`write`/`resize`/`rename` all implemented; the daemon itself confirmed alive and running on a real device (visible in the Android notification panel: "nhztermd running") |
| Part 1, Phase 4 — Process control | ✅ Done | `process.spawn`/`status`/`stop`/`list`/`kill` all implemented |
| Part 1, Phase 5 — Daemon lifecycle | ✅ Done | Foreground Service + `START_STICKY` confirmed alive on a real device via its persistent notification |
| Part 1, Phase 6 — nhzterm-api client library | ✅ Done | |
| Part 1, Phase 7 — Reference UI client | ⚠️ **Needs real-device re-verification after fixes** | Terminal rendering, the long-press menu, and the extra-keys row were all confirmed working on a real device — **but that same real-device test also found two bugs**: (1) no session side panel / "+" action existed, so a user had no way to create a session and was left staring at a blank terminal; (2) the extra-keys Row 1 was missing its `≡` key entirely (7 of 8 keys rendered). Fixes for both have been specified (§10.4 and §10.6 of the concept doc) — **confirm on a real device that both are actually in and working**, not just present in code. |
| Part 1, Phase 8 — Packaging | ✅ Done | Native-mode build routing (a separate, now-fixed Valence Framework bug) is resolved and verified; `system/bin/nhzsh` staging is implemented as part of Phase 1 above |
| Part 2 (nhzsh), Phases 0–5 | ✅ Done | Lexer → parser → expander → executor → state all implemented; 336 test assertions independently compiled and run, all passing |
| Part 2, Phase 6 — Daemon integration hook | ❓ **Unconfirmed** | Foreground-PID reporting to `nhztermd` — needs direct verification, not yet independently checked |
| Part 2, Phase 7 — `load` builtin | ❓ **Unconfirmed** | Needs direct verification |
| Part 2, Phase 8 — Validation against real scripts | ❓ **Not yet run** | |
| Integration Point (swap placeholder `sh` for real `nhzsh`) | ✅ **Already done** | `DaemonConfig.defaultShell` is already set to `"nhzsh"`, and `SessionManager.resolveShell()` already resolves it to the staged `system/bin/nhzsh` path — confirmed by reading the actual code |

**Resume work in this order:**
1. Confirm the two Phase 7 UI fixes (session panel + `≡` key) on a real device — this is the single highest-priority item, since it's a repeat of a pattern where code looked correct but the real device showed otherwise.
2. Verify Part 2 Phases 6–8 (`nhzsh`'s daemon hook, `load` builtin, and real-script validation) — these have never been independently confirmed either way.
3. Once both of the above are genuinely confirmed, this project is in a stable, verified state end to end.

---

# PART 1 — nhzterm

**Goal:** a working Kotlin Android app containing the daemon, the client protocol, and a reference UI — staged so each phase is independently testable before moving to the next.

## Phase 0 — Project Skeleton
1. Set up the Android app project in Kotlin, with `nhztermd`'s daemon logic implemented as a Foreground Service class, plus a separate `nhzterm-api` client library module that other apps (and the in-app UI) will use.
2. **Gradle and Android SDK setup do not need to be built from scratch.** A working Gradle 8.7 wrapper, Kotlin runtime template, and a fully configured Android SDK (platform 34 + build-tools 34, including the aapt2 override needed on ARM devices) already exist and are maintained separately as part of the broader toolchain this project sits alongside. Reuse that existing, already-working setup rather than reinventing SDK detection or Gradle bootstrap logic.
3. The service should do nothing yet beyond starting, immediately showing its persistent notification (this must happen right away — see Phase 5 and concept doc §7.3 for why), and opening a `LocalSocket` listener. No protocol logic yet. Confirm a basic client can open a raw connection to it.

## Phase 1 — PTY Acquisition
1. Implement the startup probe described in the concept doc §4: check for `tmux`, then `screen`, then `socat` (each via `ProcessBuilder`), in that order, using whichever is found first.
2. **Stage `nhzsh` at a real, exec-permitted file path before anything ever tries to spawn it.** `libnhzsh.so` ships inside the APK's native library directory. On daemon startup, copy it to `system/bin/nhzsh` inside app-private storage, preserving/setting exec permission explicitly. Write to a temporary file first, then rename into place, so an interrupted copy never leaves a truncated, still-marked-executable file behind. Make this copy content-aware (compare against what's already staged) so a routine restart doesn't force an unnecessary re-copy, but a genuinely different binary (after an app upgrade) does get re-staged. PTY sessions must `exec()` this real staged file path — never the `.so` path directly, since that location is only usable for code the Kotlin layer calls in-process via JNI, not for spawning a separate child process.
3. Get one working end-to-end path first — spawning a real PTY-backed shell via whichever probe tool was found — before spending any time on the native compiled-helper fallback.
4. **Test before continuing:** the service starts, the probe successfully picks a method, a real PTY-backed shell is spawned, and you can manually write to and read from it via a raw `LocalSocket` test client.
5. Defer the native compiled-helper `.so` (the `forkpty()`-based fallback) until the tmux/screen/socat path above is fully confirmed working — do not build both paths at the same time.

## Phase 2 — Handshake & Auth
1. Implement the `hello`/`hello_ack` handshake described in concept doc §6.2 — protocol version exchange plus token check.
2. Generate the auth token the first time the daemon ever starts, and write it to `run/auth.token` with owner-read-only permissions.
3. **Test before continuing:** a client presenting the correct token connects successfully; a client presenting a wrong or missing token receives `AUTH_FAILED` and the connection is refused.

## Phase 3 — Session Control (the core loop)
1. Implement `session.create`, `session.attach`, `session.list`, `session.kill`, `session.write`, `session.resize`, and `session.rename`, exactly per concept doc §6.3.
2. Enforce the 15-session cap (concept doc §8) — any `session.create` call past that limit must return `SESSION_LIMIT_REACHED`, not silently fail or silently succeed.
3. Implement the scrollback ring buffer — roughly 5,000 lines per session — replayed in full to a client on `session.attach`, with live streaming continuing after that replay finishes.
4. **Test before continuing:** create a session, run a few commands via `session.write`, disconnect, reattach, and confirm the scrollback replay correctly shows the prior output before any new output resumes.

*At this point there is already a genuinely usable terminal running a placeholder shell — a good natural checkpoint before continuing further.*

## Phase 4 — Process Control
1. Implement `process.spawn`, `process.status`, `process.stop`, `process.list`, and `process.kill`, per concept doc §6.4.
2. `process.kill` should target a specific PID directly. Note that until `nhzsh`'s own Phase 6 (Part 2, below) is done, nothing will actually be feeding it real foreground-PID data — for now, accept an explicit PID argument and test against that directly.
3. **Test before continuing:** spawn a background process via `process.spawn`, confirm `process.status` correctly reflects that it's running, and confirm `process.stop` actually ends it.

## Phase 5 — Daemon Lifecycle
1. Implement autostart (concept doc §7.1): `startForegroundService()` for the in-app path; an authorized `Intent`/binding path for external apps.
2. Set `START_STICKY` on the service so Android itself handles restart-on-kill — this replaces what would otherwise need to be a separately built and monitored watchdog process.
3. Add lightweight in-process crash recovery — catch unexpected exceptions, log them, cleanly reset internal session-tracking state — as ordinary housekeeping, not a second monitored process.
4. Implement the wake-lock toggle (concept doc §8) as an explicit opt-in setting, **off by default**. Its purpose is keeping the CPU awake during active work with the screen off, not surviving process death — the Foreground Service already guarantees that on its own.
5. **Test before continuing:** force-stop the app via Android's own settings screen, and confirm the persistent notification and `START_STICKY` bring the service back on their own, with no client having to do anything.

## Phase 6 — nhzterm-api Client Library
1. Formalize Phases 2–4's raw protocol handling into the actual, documented `nhzterm-api` package (concept doc §6/§7) — real, named method calls rather than hand-built socket messages scattered through calling code.
2. This is exactly what Valence Studio (or any other consumer) will import — keep its public surface matching concept doc §6.3/§6.4's method tables precisely.
3. **Test before continuing:** write a small, standalone test client that imports `nhzterm-api` and drives a full session lifecycle (create → write → attach → kill) using only its documented methods, with no raw socket code anywhere in the test.

## Phase 7 — Reference UI Client (Native Kotlin Terminal Renderer)
The entire UI is native Kotlin — no WebView, no JavaScript, no browser engine. The terminal view draws directly to a hardware-accelerated Canvas/Compose surface and talks to the daemon via `LocalSocket` with the same protocol and token handshake as any other client.

1. Build the core terminal renderer first: a custom `View` or `Composable` that correctly handles ANSI/VT100 escape codes, cursor movement, color (16-color and 256-color), the alternate screen buffer, and Unicode/wide-character glyph drawing. This is the hardest technical piece in the entire UI — get it working correctly before building anything around it.
2. Get basic terminal rendering working in the running app — type a command, see its output displayed correctly — before building any of the extras below.
3. **Build the session side panel with a "+" / New Session action, and auto-create-on-launch (concept doc §10.4). This is required, not optional.** Without it there is no way to create a session once the default one closes, and a blank terminal with nothing to attach to is a dead end for the user. If `session.list()` returns zero sessions when the UI opens, automatically call `session.create()` and attach to the result.
4. Build the long-press context menu (concept doc §10.3): Copy / Paste / Open (conditional) / More submenu (Open URL, Share Selected Text, Refresh, Kill Process, Style, Keep Screen On, Help, Settings, Report Issue). Since the renderer is native, this long-press can be wired directly to a native Android `PopupMenu` or bottom sheet without fighting any browser engine defaults.
5. Build the rest of the session management UI: renaming, and a session picker showing live status per session.
6. **Build the bottom bar / Extra Keys exactly as specified in concept doc §10.6 — Row 1 must render all 8 keys (`≡ ESC TAB CTRL ALT HOME ↑ END`), Row 2 all 7 (`/ - PGUP PGDN ← ↓ →`). The `≡` key specifically must be wired to a real action** (toggling the extra-keys row, or opening the session side panel from step 3) — it must never render as a dead, inert icon. Add volume-key emulation (§10.7) if targeting devices without a physical keyboard.
7. Build the style picker: the 25 fonts and 25 themes from concept doc §11. Since rendering is native, a font change means passing a different `Typeface` to the renderer's glyph-drawing code; a theme change means updating the color palette the renderer draws against — both are clean Kotlin API calls.
8. Build inline image rendering support (§10.5) — lowest priority within this phase, implement it last.
9. **Test before continuing at every step above:** each sub-feature should be manually verified working in the running UI on a real device before moving to the next one. The pattern of "looks right in code, broken on a real device" has already happened twice in this project — real-device confirmation is not optional.

## Phase 8 — Packaging
1. Implement the runtime directory structure from concept doc §12.2 under app-private storage (`context.filesDir`) — `system/bin/`, `etc/`, `sessions/`, `var/`, `run/`, `home/`.
2. Confirm `jniLibs/` ABI coverage (§12.1) — at minimum `arm64-v8a` — and confirm Gradle's standard native-library packaging correctly places `libnhzsh.so`. No custom install script is needed; Android's own installer handles this.
3. **Test before continuing:** a genuinely clean install (no pre-existing app-private storage from a prior install) followed by the very first service start successfully bootstraps everything end to end, including native library loading via JNI and the `system/bin/nhzsh` staging step from Phase 1.

---

# PART 2 — nhzsh

**Language:** C
**Goal:** a working POSIX-subset shell, staged so each phase is independently testable before moving to the next. Do not begin a later phase before the current one has real, passing tests.

## Phase 0 — Project Skeleton
1. Repo structure:
   ```
   nhzsh/
   ├── src/
   │   ├── main.c
   │   ├── lexer.c / lexer.h
   │   ├── parser.c / parser.h
   │   ├── expander.c / expander.h
   │   ├── executor.c / executor.h
   │   ├── builtins.c / builtins.h
   │   ├── state.c / state.h
   │   ├── load.c / load.h
   │   └── daemon_link.c / daemon_link.h
   ├── tests/
   └── Makefile
   ```
2. `main.c` should initially do nothing beyond reading a line from stdin and echoing it back — this proves the build/run loop itself works before any real shell logic exists.

## Phase 1 — Lexer
1. Implement tokenization of: words, `|`, `>`, `>>`, `<`, `&&`, `||`, `;`, `&`, single- and double-quoted strings, and `#` comments.
2. Output a straightforward token stream — an array or list of `{type, value}` pairs.
3. **Test before continuing:** feed it a handful of hand-written command strings, including quoted arguments, pipes, and multiple operators, and assert the resulting token stream matches what's expected.

## Phase 2 — Parser
1. Consume the lexer's token stream and build an AST following this grammar:
   ```
   command_list := pipeline (( '&&' | '||' | ';' ) pipeline)*
   pipeline     := command ('|' command)*
   command      := word+ redirect*
   redirect     := ('>' | '>>' | '<') word
   ```
2. **Test before continuing:** feed known token streams and assert the resulting AST shape is correct, including at least one pipeline, one conditional chain, and one redirect.

## Phase 3 — Expander
1. Implement, in this order: `$VAR`/`${VAR}` expansion, `$(...)` command substitution, `*`/`?` glob expansion, and `~` tilde expansion.
2. Give each expansion type its own independent test case first, then one combined test exercising all four in a single command line together.

## Phase 4 — Executor
1. Implement builtins that run in-process, with no `fork()`: `cd`, `export`, `unset`, `alias`, `pwd`, `exit`.
2. Implement non-builtin execution: `fork()` + `exec()`, with stdin/stdout/stderr correctly wired for each stage of a pipeline (every `|` needs a genuine pipe between the two sides of it).
3. Track `$?` (the last exit code) and make it available to the expander for future use.
4. **Test before continuing:** run a simple single command, a piped command (`cmd1 | cmd2`), and a builtin (`cd` followed by `pwd`, confirming the directory actually changed).

## Phase 5 — State & the REPL Loop
1. Implement the persistent loop: read the next command (from stdin when interactive, or sequentially from a file when running a script), parse it, expand it, execute it, repeat.
2. Persist `cwd` and environment variables across iterations of this loop — not per-line. The shell process itself is long-lived.
3. Implement basic command history (an in-memory list is sufficient for now).
4. Implement the interactive-vs-script distinction discussed in the concept doc §5.2: the same loop in both cases, differing only in where input comes from and whether the prompt gets printed (suppress it when not attached to a real TTY).
5. **Test before continuing:** run a real multi-line `.sh` script through it and confirm that a `cwd`/environment change made on one line is correctly visible on the next.

## Phase 6 — Daemon Integration Hook (Foreground-PID Reporting)
1. On exec of any foreground (non-backgrounded) command, send a message to `nhztermd` over the control side-channel reporting the new PID: `{ "type": "foreground_pid", "session_id": ..., "pid": ... }`.
2. On completion of that command, send the same message type with `pid: null`.
3. This does not block waiting for a response — it's fire-and-forget, matching concept doc §9.
4. **Test before continuing:** using a stub/mock listener standing in for `nhztermd`, confirm the two messages arrive in the correct order around a real command's actual lifetime.

### >>> INTEGRATION POINT <<<
Swap Part 1's placeholder `sh` for real `nhzsh` inside `nhztermd`'s session-spawning logic. (Note: per the Current Status table above, this appears to already be done — `DaemonConfig.defaultShell` is already `"nhzsh"`. Re-confirm this explicitly rather than assuming, and re-run Part 1 Phase 3's create/write/attach/kill test against real `nhzsh` specifically, not just against whatever the current default happens to be.) Wire Phase 6's foreground-PID messages into Part 1 Phase 4's `process.kill` so "Kill Process" in the UI genuinely works end to end, not just against a manually-supplied test PID.

## Phase 7 — The `load` Builtin
1. Implement `load <name> [as <alias>]`:
   - A search-path list, checked in priority order (script-local `lib/`, then user-level, then system-level)
   - A load-tracking table, so loading the same library a second time is a no-op rather than a silent re-source
   - The `as` alias generates a wrapper function forwarding to the library's real internal function name
2. Implement `unload <name>` and `list-libs` as companion builtins.
3. **Test before continuing:** load a trivial test library twice and confirm the second load is a no-op; `unload` it and confirm it no longer appears in `list-libs`.

## Phase 8 — Validation Against Real Scripts
1. Run `nhzsh` against real, already-existing `.sh` scripts from the broader project (e.g. boot-sequence scripts from the related NHZENV project) to confirm genuine POSIX-subset compatibility — these should run without any modification, since the core grammar must never diverge from standard `.sh` syntax.
2. Log or report any construct these real scripts use that `nhzsh` doesn't yet support — treat this as the real-world signal for what to build next, rather than guessing ahead of actual need.

---

## Explicitly Deferred (either part, not required for v1)
- Split-pane layout inside `nhzterm` itself — out of scope entirely; a separate IDE product already provides this
- The native compiled-helper `.so` PTY fallback — only build this if the tmux/screen/socat probe genuinely fails to find anything usable on a real target device
- Job control beyond basic foreground/background, here-documents, and advanced parameter expansion in `nhzsh` — all real, all post-v1
- Custom/project-specific `nhzsh` builtins beyond `load`/`unload`/`list-libs` — add these only after Part 2 Phase 8 passes
