# nhzterm — Project Concept

**Status:** Living spec — actively being built. This document reflects the current, correct state of the design as of the latest work; it is not a historical record of how the design changed over time.
**Author context:** NJ Daymiel (NHZTech / "Nick Codings") — built for phone-first dev workflows, running directly on Android, consumed by Valence Studio and future clients
**One-line pitch:** A standalone Android terminal application with its own background daemon, its own custom shell, and a documented API — so any application on the device can spawn and control real PTY sessions without owning a UI or a shell of its own.
**Implementation stack:** Kotlin (Android app + Foreground Service daemon logic + native terminal renderer UI) + C via JNI/NDK (PTY acquisition, `nhzsh`).

---

## Table of Contents
1. What This Is & Why It Exists
2. Core Principles
3. Architecture
4. PTY Acquisition Strategy
5. nhzsh — The Shell
6. nhzterm-api — Protocol Specification
7. Daemon Lifecycle
8. Operational Specs & Limits
9. Process Handling — Kill Process (PID) Design
10. Front-End UX Specification
11. Style — Fonts & Themes
12. File Layout / Packaging
13. Security Considerations
14. Open Decisions
15. Explicitly Rejected Ideas
16. Glossary

---

## 1. What This Is & Why It Exists

`nhzterm` is a real Android application built around three parts working together:

- **`nhztermd`** — a Kotlin Android Foreground Service that owns real pseudo-terminal (PTY) sessions and a custom shell (`nhzsh`)
- **`nhzterm-api`** — a documented client protocol that any application uses to talk to the daemon: create sessions, send input, read output, spawn and control processes
- **`nhzterm` (UI)** — a reference client, built entirely in Kotlin as a native Android terminal renderer, that is one consumer of the API — not a requirement for the daemon to function

This exists because a terminal-in-an-app can be built badly in two common ways, and `nhzterm` deliberately avoids both:

1. **Fake terminals.** Piping commands over HTTP or a simple byte stream without a real PTY means full-screen programs (`vim`, `htop`, `less`, anything using live cursor redraw) simply do not work. `nhzterm` uses a real PTY from the start — this is not a "v2 feature," it is true from the first working version.
2. **Terminals welded to one app.** If the terminal only exists as a feature bolted onto one specific application, every other application that wants shell access has to reinvent the same plumbing. `nhzterm` is architected the opposite way: the daemon is the actual product, and any UI (including its own) is just a client of it.

`nhzterm` runs as a genuine, standalone Android app. Its own private app storage (`data/data/<package>/files/`) is treated as an operating-system-style root, following the same pattern Termux itself uses for its own `$PREFIX`/`$HOME` — no `proot`, no dependency on Termux being installed, no chroot into someone else's distribution.

## 2. Core Principles

1. **No web-tech dependency anywhere.** `nhztermd`'s daemon logic is a native Kotlin Android component (a Foreground Service); `nhzsh` and the PTY-acquisition code are C, reached via JNI. The UI is a native Kotlin terminal renderer — not a WebView, not a browser engine, not a JavaScript runtime. The entire app is native, top to bottom.
2. **Real PTY from day one.** No pipe-mode compromise. `vim`/`htop`/any full-screen terminal program must work from the first usable version, not be deferred.
3. **Headless-first.** The daemon must be fully operable with zero UI open. Sessions persist independent of any attached client, the same way `tmux`'s server keeps sessions alive independent of any attached client.
4. **Zero manual daemon management.** No user and no other application ever starts `nhztermd` by hand. It starts itself the moment anything needs it.
5. **POSIX-compatible shell grammar.** `nhzsh` does not invent a new core scripting language. Existing `.sh` scripts must keep working unmodified. Anything custom to `nhzsh` is additive (new builtins), never a change to the core grammar.
6. **Standalone identity.** `nhzterm` has its own name, its own versioning, its own release cycle — independent of any application that happens to use it, including Valence Studio.
7. **Structure that matches how the project actually behaves in production.** Design decisions are corrected the moment real-device testing shows they were wrong — a design that only works "on paper" is treated as incomplete, not done.

## 3. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  nhztermd — Kotlin, Android Foreground Service                     │
│  - No web tech, no browser dependency                              │
│  - Owns: PTY sessions (via JNI → C), nhzsh processes,                │
│    foreground-PID map, scrollback ring buffers, session metadata    │
│  - Listens on: Android LocalSocket (speaks nhzterm-api)              │
│  - Auth: token handshake (§13)                                       │
│  - Shows a persistent notification the instant it starts — this is   │
│    what makes it exempt from Android's phantom-process-killer (§7)   │
└────────────────────────────┬───────────────────────────────────────┘
                              │ Android LocalSocket
              ┌────────────────┼──────────────────────┐
              │                                        │
   ┌──────────▼─────────┐                  ┌───────────▼───────────┐
   │ nhzterm UI           │                  │ Other applications      │
   │ (native Kotlin        │                  │ (e.g. Valence Studio)   │
   │ terminal renderer),   │                  │ connect to the           │
   │ draws output directly  │                  │ LocalSocket directly,    │
   │ to a Canvas/Compose    │                  │ same token handshake,    │
   │ surface — no WebView,  │                  │ no shell of their own    │
   │ no browser engine      │                  │ needed                   │
   └───────────────────────┘                  └─────────────────────────┘
```

**Why this shape, specifically:**
- Sessions must outlive any one application that happens to be using them. Closing the terminal UI should never kill a long build running inside a session.
- Multiple applications may want to observe or drive the same session — e.g. the `nhzterm` UI displaying a build that Valence Studio's backend actually kicked off.
- Any application wanting shell access should not have to reimplement PTY handling, session bookkeeping, or scrollback buffering — the daemon is the single place that logic lives.
- A Kotlin Foreground Service specifically (rather than a plain background process or a Node.js-style headless daemon) gets a **persistent notification and OS-level protection from Android's phantom-process-killer as a structural guarantee**, not something bolted on afterward with wake-locks and watchdog processes. This is the single biggest reason the daemon logic is Kotlin rather than any other language: it is the only way to get that guarantee on Android.
- The reference UI is a **native Kotlin terminal renderer** — no WebView, no JavaScript engine, no browser dependency of any kind. It talks to the daemon via `LocalSocket` with the same protocol and same token handshake as any other client. This is the same connection path a fully external client (like Valence Studio) would use — there is no special in-app bridge, no `JavascriptInterface`, no fast lane. Every client speaks the same protocol.

## 4. PTY Acquisition Strategy

`nhztermd` needs a real pseudo-terminal for every session it creates. Neither the JVM/Kotlin layer nor plain Node-style JavaScript has a built-in way to call `forkpty()` — this has to happen at the native (C) layer, reached from Kotlin via JNI where the daemon needs to reach it directly.

**Startup probe, checked in this order, first match wins:**
1. **`tmux`** (checked via `command -v tmux` / `ProcessBuilder`) — if present, sessions are driven through `tmux new-session -d` plus control-mode. This is the preferred method: `tmux` already has mature, battle-tested PTY handling and session persistence built in, so `nhztermd` doesn't need to reimplement that.
2. **`screen`** — used if `tmux` is not present.
3. **`socat`** — can allocate a PTY directly (`socat PTY,link=... EXEC:...`) and bridge it to a pipe or socket.
4. **Compiled native helper (last resort only).** A small native library (`.so`, built with the NDK) whose only job is `forkpty()` followed by `exec("nhzsh")`, loaded directly via JNI from the Kotlin service — no external process required for this path. This is only used when none of the above three are available on the device.

The point of probing rather than hardcoding one method is that different devices will have different tools already installed — probing keeps `nhztermd` working across the widest range of real devices without forcing every user to install one specific tool first.

## 5. nhzsh — The Shell

### 5.1 Design Philosophy
`nhzsh` is a POSIX-subset shell, built up in deliberate stages, that is also a first-class participant in the `nhzterm-api` ecosystem — it actively reports information back to the daemon (§9) rather than being a shell the daemon merely happens to run. It is explicitly **not** trying to match `bash`'s full feature set. The goal is *just enough shell* to run real development workflows, plus the specific hooks the daemon needs to do its job.

**Implementation language: C.** This matches what real production shells are written in (`bash`, `dash`, `busybox`'s `ash`), gives direct, zero-overhead access to the exact syscalls a shell is constantly making (`fork()`, `exec()`, `forkpty()`), and is the fastest practical choice available for this kind of systems-level program.

### 5.2 Build Order
Each stage below is meant to be independently testable before moving to the next — do not begin a later stage before the current one has real passing tests behind it.

**Stage 1 — Lexer.** Tokenizes raw input text into: words, operators (`|`, `>`, `>>`, `<`, `&&`, `||`, `;`, `&`), single- and double-quoted strings, and `#` comments.

**Stage 2 — Parser.** Converts the lexer's token stream into an abstract syntax tree, following this grammar:
```
command_list  := pipeline (( '&&' | '||' | ';' ) pipeline)*
pipeline      := command ('|' command)*
command       := word+ redirect*
redirect      := ('>' | '>>' | '<') word
```

**Stage 3 — Expander.** Resolves, in this order: variable expansion (`$VAR`, `${VAR}`), command substitution (`$(...)`), glob expansion (`*`, `?`), and tilde expansion (`~`).

**Stage 4 — Executor.**
- Builtins run **in-process**, no `fork()`: `cd`, `export`, `unset`, `alias`, `pwd`, `exit`. (`cd` in particular *cannot* be external — it has to modify the shell's own process state directly.)
- Everything else runs as a real child process: `fork()` + `exec()`, with stdin/stdout/stderr wired correctly per pipeline stage (each `|` needs a real pipe between the two sides).
- The exit code of the last command (`$?`) is tracked and made available to the expander.

**Stage 5 — State & the REPL loop.**
- A persistent loop: read the next command (from stdin when interactive, or sequentially from a file when running a script), parse, expand, execute, repeat.
- `cwd` and environment variables persist across iterations of this loop — the shell process itself is long-lived, this is not re-initialized per line.
- Basic command history (an in-memory list is sufficient).
- **Interactive mode and script-interpreter mode are the same code path**, not two different features. The only real differences are: where the next line of input comes from (keystrokes via the PTY vs. sequential lines from a file), and whether the prompt (`$ `) gets printed (suppressed when not attached to a real TTY). This is why building "the shell" and "the interpreter for `.sh` files" is effectively one piece of work, not two.

**Stage 6 — Daemon integration hook (foreground-PID reporting).** See §9 for the full rationale. In short: whenever `nhzsh` execs a new foreground command, it reports that command's PID to `nhztermd` over a control side-channel distinct from the main PTY byte stream; when that command finishes, it reports `pid: null`. This is fire-and-forget — it does not wait for a response.

**Stage 7 — The `load` builtin.** See §5.4 below for the full feature description.

**Stage 8 — Custom builtins.** Anything project-specific beyond the standard set is added only after the POSIX-subset core (stages 1–6) is solid — custom behavior must never require a change to the core grammar itself.

### 5.3 Explicitly Deferred (not required for v1)
- Job control beyond basic foreground/background (`fg`, `bg`, `jobs`)
- Here-documents (`<<EOF`)
- Advanced parameter expansion (`${VAR:-default}`, `${VAR#pattern}`) — add once plain `$VAR` is solid

### 5.4 What's New in nhzsh — the Built-In Library System
Most shells offer nothing beyond `source`/`.` — dumping a file's contents into the current scope, with no structure, no aliasing, and no concept of "is this already loaded." `nhzsh` treats libraries as a genuine first-class feature:

- **`load <name> [as <alias>]`** — locates `<name>.sh`, sources it, and (if `as` is used) generates a short alias function so callers don't need to reference the library's full internal function name.
- **Load tracking** — loading the same library a second time is a no-op, rather than silently re-sourcing the file and redefining everything inside it.
- **A real search-path list**, checked in priority order — script-local `lib/`, then a user-level location, then a system-level location — rather than one single hardcoded folder. This means libraries can be scoped per-project or shared system-wide.
- **`unload <name>` / `list-libs`** as companion builtins, giving a session a way to inspect or reverse what's currently loaded.
- **Distributable libraries, as a future goal.** Once the search-path/manifest design is solid, `nhzpm` (the separate package-manager project) can install `nhzsh` libraries the same way it installs anything else, and `load` simply finds them wherever it already knows to look — no special-casing required.

## 6. nhzterm-api — Protocol Specification

### 6.1 Transport
Android `LocalSocket` — Android's own Unix-domain-socket-backed IPC primitive. It can bind in the abstract namespace, so no filesystem path is required at all. Message framing is length-prefixed JSON: a 4-byte big-endian length header followed by a UTF-8 JSON body. This is simple to debug and works identically whether the caller is the in-app native UI or a fully separate application — both use the same protocol.

### 6.2 Handshake (required at the start of every connection)
```json
// Client -> Daemon (must be the first message sent)
{ "type": "hello", "protocol_version": 1, "token": "<auth token>" }

// Daemon -> Client
{ "type": "hello_ack", "protocol_version": 1, "accepted": true }
// or, on a mismatch or bad token:
{ "type": "hello_ack", "protocol_version": 1, "accepted": false, "reason": "bad_token" | "protocol_mismatch" }
```

### 6.3 Session Control Methods
| Method | Params | Returns |
|---|---|---|
| `session.create` | `{ shell?: string, cwd?: string }` | `{ session_id, pty_cols, pty_rows }` |
| `session.attach` | `{ session_id }` | `{ scrollback: string, status: "running"\|"idle"\|"finished" }`, then a live output stream begins |
| `session.list` | `{}` | `{ sessions: [{ session_id, name, status, created_at }] }` |
| `session.kill` | `{ session_id }` | `{ ok: true }` |
| `session.write` | `{ session_id, data: string }` | (none — fire-and-forget; the write shows up in the output stream) |
| `session.resize` | `{ session_id, cols, rows }` | `{ ok: true }` |
| `session.rename` | `{ session_id, name }` | `{ ok: true }` |

### 6.4 Process Control Methods
| Method | Params | Returns |
|---|---|---|
| `process.spawn` | `{ cmd, args, cwd? }` | `{ process_id, pid }` |
| `process.status` | `{ process_id }` | `{ status: "running"\|"exited", exit_code? }` |
| `process.stop` | `{ process_id }` | `{ ok: true }` |
| `process.list` | `{}` | `{ processes: [...] }` |
| `process.kill` | `{ pid }` | `{ ok: true }` — used to implement the UI's "Kill Process" action; see §9 |

### 6.5 Streamed Events (daemon → client, sent without the client asking, once attached)
```json
{ "type": "output", "session_id": "...", "data": "<raw bytes, base64 or utf-8>" }
{ "type": "session_status_changed", "session_id": "...", "status": "finished" }
{ "type": "error", "code": "SESSION_LIMIT_REACHED" | "SESSION_NOT_FOUND" | ..., "message": "..." }
```

### 6.6 Error Codes (initial set)
`AUTH_FAILED`, `PROTOCOL_MISMATCH`, `SESSION_LIMIT_REACHED`, `SESSION_NOT_FOUND`, `PROCESS_NOT_FOUND`, `INTERNAL_ERROR`

### 6.7 Client Language Support (deferred, not v1 scope)
The initial `nhzterm-api` client is targeted at Kotlin (in-app) and whatever language Valence Studio's own backend uses. Supporting additional client languages later (a Python SDK, for example) is a real future goal but not required for v1 — the protocol itself (§6.1–6.6) is entirely language-agnostic (length-prefixed JSON over `LocalSocket`), so adding a new language binding later requires no protocol changes, only a new client implementation of the same wire format.

## 7. Daemon Lifecycle

Because `nhztermd` is a real Kotlin Android Foreground Service and not a headless background script, most of what would otherwise be complicated "keep the daemon alive" engineering is solved by Android's own service model rather than being something this project has to build itself.

### 7.1 Autostart
No user and no application ever starts `nhztermd` manually.
1. Any client — the in-app native UI, or an external application like Valence Studio — attempts to connect/bind to the service.
2. If it isn't already running, `startForegroundService()` (for the in-app path) or an authorized `Intent`/binding request (for an external app, with the appropriate permission declared) starts it.
3. The client then proceeds with the `LocalSocket` handshake (§6.2) once the service is up.

### 7.2 Staying Alive / Auto-Respawn
- The service is declared `START_STICKY`, meaning Android itself restarts it if it's ever killed — this removes the need for a separate, manually-monitored watchdog process, which a plain background daemon would otherwise require.
- Light in-process crash recovery (catch an unexpected exception, log it, cleanly reset internal session-tracking state) is still worth having, but this is ordinary internal housekeeping, not a second monitored process.

### 7.3 Phantom-Process-Killer — Solved, Not Mitigated
Android 12 and later includes a "phantom-process-killer" that specifically targets background processes that are **not** covered by a Foreground Service. Because `nhztermd` **is** a Foreground Service with a persistent notification from the moment it starts, it is exempt from this by construction — this isn't a workaround layered on top, it's a direct structural consequence of the architecture choice in §3.
- **Wake lock** remains available as a separate, **opt-in, off-by-default** setting — but its purpose here is purely about keeping the CPU awake during active work while the screen is off (e.g. a long-running build), not about surviving process death, since the Foreground Service already guarantees that on its own.

## 8. Operational Specs & Limits

| Spec | Value | Rationale |
|---|---|---|
| Max concurrent sessions | **15** | High enough for real multi-session development work, low enough to prevent runaway resource use on a phone |
| Scrollback buffer | **~5,000 lines** per session, ring buffer | Enough history to catch up after reattaching to a session; bounded so memory use per session stays predictable |
| Socket auth | A random token, generated the first time the daemon starts, stored in a location only first-party app code can read; required as the very first message on every connection | Prevents any other local process from attaching and controlling sessions or spawning processes |
| Protocol versioning | An integer version number exchanged during the handshake; a mismatch produces a clean, explicit error rather than silently misparsing messages | Lets `nhzterm-api` evolve over time without silently breaking older clients |
| Packaging | `nhzsh` and the PTY-helper native library ship as JNI `.so` files bundled per ABI inside the APK itself, using Android's own standard native-library packaging mechanism | Reuses a mechanism Android already solves correctly, instead of inventing a second, manual packaging pipeline |
| Wake lock | User-facing opt-in setting, **off by default** | The battery cost of a wake lock must be a deliberate choice, never a forced default |
| Split-pane / multi-pane layout | **Explicitly out of scope for nhzterm itself** | Valence Studio's own IDE already provides split-pane layout; duplicating that inside the terminal adds real complexity without adding real capability |

## 9. Process Handling — Kill Process (PID) Design

**The problem:** the UI's "Kill Process" action needs to terminate specifically whatever command is currently running in the foreground of a session — not the session's shell process itself. Figuring out "what is currently in the foreground" would normally require a tty-level syscall (`tcgetpgrp` against the PTY master) from outside the shell process.

**The solution — made possible because `nhzsh` is a custom shell, not a stock one:** `nhzsh` self-reports.
- Whenever it execs a new foreground command, it sends `{ "type": "foreground_pid", "session_id": ..., "pid": ... }` to `nhztermd` over a control channel that is separate from the main PTY byte stream.
- When that foreground command finishes, it sends `{ "type": "foreground_pid", "session_id": ..., "pid": null }`.

`nhztermd` maintains a simple `session_id -> current_foreground_pid` map built entirely from these self-reports. The UI's "Kill Process" action calls `process.kill(pid)` via `nhzterm-api` (§6.4) against whatever PID is currently tracked for that session; the daemon sends the actual OS signal to that specific process, leaving the shell and the session itself alive and intact.

**Edge case:** if the tracked PID is `null` (nothing currently in the foreground) when "Kill Process" is invoked, the daemon returns `PROCESS_NOT_FOUND` rather than silently doing nothing — this lets the UI show an honest "nothing is currently running" state instead of a dead tap.

## 10. Front-End UX Specification

### 10.1 Rendering
The terminal view is a **native Kotlin renderer** — a custom `View` or `Composable` that draws terminal output directly to a hardware-accelerated `Canvas` surface. No WebView, no JavaScript runtime, no browser engine of any kind. This gives complete, direct control over every rendering decision: glyph drawing, cursor behavior, color handling, touch gesture behavior, and resize response — none of which has to negotiate with or work around a browser engine's own defaults. The renderer must implement the ANSI/VT100/xterm escape-code surface (cursor movement, color, alternate screen, bold/italic/underline, wide characters/Unicode) from scratch in Kotlin, or by wrapping a well-tested native (C/C++) terminal emulation library via JNI if the from-scratch surface proves too large for v1 scope.

### 10.2 Link Handling — Deliberately Not Auto-Clickable
Links render as plain text. There is no `addon-web-links` auto-linkification. Instead, link interaction happens through the long-press menu below — this matches the interaction model Termux users are already familiar with, rather than the more common "blue underlined clickable text" web convention.

### 10.3 Long-Press Context Menu
Long-pressing anywhere on the terminal surface opens a popup menu:
- **Copy**
- **Paste**
- **Open** — opens a currently-highlighted/selected URL directly (only shown at all if the current selection is actually a link)
- **More →** (submenu)
  - **Open URL** — conditional, same as above, only present when a link is highlighted
  - **Share Selected Text** — hands the current selection to the OS share sheet
  - **Refresh** — recovers a frozen or unresponsive-looking terminal view (re-renders from the client's last known state, or re-requests scrollback from the daemon) without killing the underlying session
  - **Kill Process (PID)** — see §9
  - **Style** — opens the font/theme picker (§11)
  - **Keep Screen On** — a per-session override of the phone's screen-timeout while this session is active or idling
  - **Help** — an in-app, README-style usage and shortcuts reference
  - **Settings**
  - **Report Issue**

### 10.4 Session Management UI
This is not optional polish — it is required for the app to be usable at all, and its absence has already caused a confirmed real-device bug (a blank terminal with no way to create a session).

- **A persistent session side panel** (drawer or dedicated tab) listing every currently open session, with a clearly visible **"+" / New Session** action always reachable from it. Without this, there is no way for a user to create a session once the default one is closed or killed — the terminal simply has nothing to attach to and no way forward.
- **Auto-create on first launch.** If the UI opens and `session.list()` comes back with zero sessions, the client automatically calls `session.create()` and attaches to the result, rather than showing a blank terminal with no visible next step. An empty state must never be a dead end.
- **Renaming** — any session can be renamed from this panel (`session.rename`, §6.3).
- **Live status per session** — running / idle / finished — shown in the panel, since sessions persist headlessly and may well have changed state while the UI was closed.

### 10.5 Image Rendering
Inline image support (in the style of the sixel or kitty-graphics-protocol conventions) is confirmed to be in scope. The exact implementation approach is left to the build phase.

### 10.6 Bottom Bar / Extra Keys
A two-row, Termux-style extra-keys bar, grouped exactly as follows — **both rows must render their full set of keys; a row rendering with a key silently missing is a bug, not a stylistic variation:**

**Row 1 (8 keys):** `≡` `ESC` `TAB` `CTRL` `ALT` `HOME` `↑` `END`
**Row 2 (7 keys):** `/` `-` `PGUP` `PGDN` `←` `↓` `→`

The `≡` key at the start of Row 1 has a real, required function — it is not decorative. Depending on how the UI's overall navigation is structured, it should either toggle the extra-keys row itself (the conventional Termux behavior) or open the session side panel from §10.4 — but it must do *something* meaningful when tapped, never render as an inert, unwired icon.

### 10.7 Volume Key Emulation (for devices without a physical keyboard)
Volume Down acts as a Ctrl modifier (e.g. Vol Down+C sends Ctrl+C). Volume Up acts as a modifier for navigation and special characters:

| Combo | Action |
|---|---|
| Vol Up+Q / Vol Up+K | Toggle Extra Keys row |
| Vol Up+V | Show the Android system volume slider |
| Vol Up+E | Esc |
| Vol Up+T | Tab |
| Vol Up+W / S / A / D | Up / Down / Left / Right arrow |
| Vol Up+L | `\|` |
| Vol Up+H | `~` |
| Vol Up+U | `_` |
| Vol Up+P | Page Up |
| Vol Up+N | Page Down |
| Vol Up+B | Alt+B (move back one word) |
| Vol Up+F | Alt+F (move forward one word) |
| Vol Up+. | Ctrl+\ (SIGQUIT) |
| Vol Up+1..0 | F1 through F10 |

### 10.8 Standard Ctrl Shortcuts (via a physical keyboard, the extra-keys row, or Vol Down+key)
`Ctrl+A` — jump to start of line
`Ctrl+E` — jump to end of line
`Ctrl+C` — interrupt the running foreground process (SIGINT)
`Ctrl+D` — end-of-file / close the current session
`Ctrl+Z` — suspend the current process to the background (SIGTSTP)
`Ctrl+L` — clear the screen
`Ctrl+K` — cut from the cursor to the end of the line
`Ctrl+U` — cut from the cursor to the start of the line
`Ctrl+W` — cut the single word immediately before the cursor
`Ctrl+Y` — paste text previously cut with Ctrl+K / Ctrl+U / Ctrl+W
`Ctrl+R` — reverse history search

### 10.9 Hardware Keyboard Shortcuts (Ctrl+Alt combinations, Bluetooth/USB keyboards)
`Ctrl+Alt+C` — open a new session
`Ctrl+Alt+R` — rename the current session
`Ctrl+Alt+N` / Down Arrow — switch to the next session
`Ctrl+Alt+P` / Up Arrow — switch to the previous session
`Ctrl+Alt+1..9` — jump directly to session number 1 through 9
`Ctrl+Alt+→` — open the left navigation drawer
`Ctrl+Alt+←` — close the left navigation drawer
`Ctrl+Alt+M` — open the context menu (Copy, Paste, Reset, etc.)
`Ctrl+Alt+U` — launch the URL picker overlay (extracts clickable links from the current screen)
`Ctrl+Alt+V` — paste clipboard text
`Ctrl+Alt+(+ / -)` — zoom terminal text size in or out
`Ctrl+Alt+K` — toggle the soft/touch keyboard on or off

## 11. Style — Fonts & Themes

**Fonts (25):** JetBrains Mono, Fira Code, Cascadia Code, Iosevka, Hack, IBM Plex Mono, Source Code Pro, Space Mono, Terminus, Victor Mono, Monaspace, Inconsolata, Ubuntu Mono, DejaVu Sans Mono, Consolas, Menlo, SF Mono, Roboto Mono, Anonymous Pro, PT Mono, Operator Mono, Input Mono, Recursive Mono, Departure Mono, Comic Mono

**Themes (25):** Dracula, Nord, Gruvbox Dark, Gruvbox Light, Solarized Dark, Solarized Light, Tokyo Night, Catppuccin Mocha, Catppuccin Latte, Monokai, One Dark, Ayu Dark, Night Owl, Rosé Pine, Matrix Green, Cyberpunk Neon, Material Theme, Oceanic Next, Palenight, Synthwave '84, Horizon, Kanagawa, Everforest, GitHub Dark, Zenburn

**Suggested defaults shipped out of the box:** Dracula, Nord, Gruvbox Dark, and Matrix Green — a spread from "serious daily-driver" to the deliberately playful, classic green-on-black hacker aesthetic.

## 12. File Layout / Packaging

### 12.1 What Ships Inside the APK (build-time contents, not runtime state)

```
app/
├── src/main/
│   ├── kotlin/                       # nhztermd service logic, nhzterm-api
│   │                                 # server-side implementation, session
│   │                                 # and process management, LocalSocket
│   │                                 # handling
│   ├── jniLibs/
│   │   ├── arm64-v8a/
│   │   │   ├── libnhzsh.so
│   │   │   └── libptyhelper.so       # only shipped if the probe from §4
│   │   │                             # needs a fallback for this ABI
│   │   ├── armeabi-v7a/
│   │   │   └── ...
│   │   └── x86_64/                   # emulator/desktop testing only
│   │       └── ...
│   ├── assets/
│   │   ├── themes/                   # default theme set (§11), copied into
│   │   │                             # app-private storage on first run
│   │   └── doc/
│   │       └── README.md             # backs the UI's "Help" menu item (§10.3)
│   └── res/                          # standard Android resources — icons,
│                                      # the notification layout the
│                                      # Foreground Service requires, etc.
```

No separate manual install step is needed for the native binaries themselves — Android's own APK installer extracts `jniLibs/` into the app's native library directory automatically, and that specific location is inherently exec-permitted. This is precisely what sidesteps the Android 10+ execute-permission restriction that a plain Termux-hosted daemon would otherwise need workarounds for.

### 12.2 Runtime State (app-private storage, `context.filesDir`)

```
<app-private-files-dir>/
│
├── system/
│   └── bin/
│       └── nhzsh                    # An EXEC-PERMITTED COPY, staged from the
│                                     # APK's own native library directory at
│                                     # daemon startup — see explanation below.
│                                     # This is the actual path PTY sessions
│                                     # exec(), and it matches NHZOS's own
│                                     # system/bin/ directory convention
│                                     # (alongside a future su, nhzpm)
│
├── etc/
│   ├── nhztermd.json                # daemon config: session cap (15), buffer
│   │                                 # size (5000 lines), wake-lock default (off)
│   ├── nhzshrc                      # nhzsh's own startup config
│   └── themes/                      # active/user-modified theme copies
│                                    # (assets/themes/ from §12.1 is the
│                                    # untouched, restorable original)
│
├── sessions/                        # persisted session METADATA only —
│   │                                 # live scrollback stays in daemon memory
│   └── <session_id>/
│       └── meta.json                # { name, status, created_at, cwd, shell }
│
├── var/
│   ├── log/
│   │   └── nhztermd.log
│   └── cache/
│
├── run/
│   └── auth.token                   # LocalSocket auth token (§8/§13) —
│                                     # app-private storage is already
│                                     # inaccessible to other apps by default
│                                     # on modern Android, so this file is
│                                     # defense-in-depth, not the sole boundary
│
└── home/                            # default working directory for sessions
                                      # created via automation (§6.4's
                                      # process.spawn) that aren't tied to a
                                      # person interactively attached to them
```

**Why `nhzsh` must be staged as a real file at `system/bin/nhzsh`, rather than referenced directly from the `.so`:** §12.1's `jniLibs/arm64-v8a/libnhzsh.so` is the *source of truth* for the compiled binary, and it's where Android's installer guarantees exec permission — but that guarantee only helps code the Kotlin daemon calls **in-process** through JNI. A PTY session is a genuinely separate **child process**, spawned via `tmux`/`screen`/`socat`/`forkpty` per §4 — it needs to `exec()` a real file sitting at a real, stable filesystem path, not a library the daemon's own process happens to have loaded. Handing a session the raw `.so` path directly is also fragile in practice: on some devices the native library directory can be a symlink pointing into the APK itself, and the extracted `.so` file is not guaranteed to remain a plain, independently-executable file across every device and Android version.

So, on daemon startup, `nhztermd` copies `libnhzsh.so` from its own native library directory into `system/bin/nhzsh` inside its app-private storage, and explicitly marks the copy executable. This copy is:
- **Idempotent and content-aware** — an unchanged binary is not re-copied on every routine restart, but a genuinely different binary (after an app upgrade) does get re-staged.
- **Written atomically** — the copy is written to a temporary file first, then renamed into place, so an interrupted copy (e.g. the app gets killed mid-write) can never leave a truncated, still-"executable" binary sitting at the real path.
- **Safe to replace even if a session currently has the old copy open** — on Linux, renaming a new file over an in-use one succeeds regardless; the process that still has the old file open keeps working against the old inode until it exits naturally.

This is also exactly why `nhzsh`'s C **source code** living at the top level of the project repository (as its own sibling project next to `nhzterm/`) is completely correct and expected — that's simply where the source lives before compilation. It has nothing to do with, and should never be confused with, where the *compiled, staged* binary ends up on a running device.

**What changed from an earlier, since-abandoned Termux-daemon-era layout:** binaries are no longer manually placed by any kind of `install.sh` step — they ship inside the APK's native library directory and are staged into `system/bin/` by the daemon's own code at startup. There is no socket *file* anywhere in this layout — Android's `LocalSocket` binds in the abstract namespace, so no filesystem path is needed for it at all.

### 12.3 Permissions Checklist (deploy-blocking if any of these are wrong)
- `run/auth.token` — app-private storage is already sandboxed per-app by Android itself; still create this file with restrictive permissions as defense-in-depth, not as the only protection.
- The Foreground Service notification must be shown **immediately** on service start — this is not optional polish, it is a hard requirement for the phantom-process-killer exemption described in §7.3 to actually apply. A service that delays showing its notification is not protected during that window.
- `jniLibs/` ABI coverage must include, at minimum, `arm64-v8a` (the primary real-device target). Missing the actual device's ABI means the native library simply fails to load, with no PTY acquisition possible at all.
- `system/bin/nhzsh` must actually exist and be executable **after** daemon startup completes, every time — this is the thing that was previously broken and is now fixed via the staging mechanism in §12.2; it is worth explicitly re-verifying on a real device after any change touching daemon startup.

## 13. Security Considerations

- **The `LocalSocket` auth token** (§8) prevents arbitrary local processes or other apps from attaching to the daemon. Android's own app sandboxing already restricts which apps can even discover the socket, but the token handshake provides real defense-in-depth for the cross-app case (Valence Studio or any other external client).
- **Token storage** lives in app-private storage (`context.filesDir`), which modern Android already makes inaccessible to other apps by default without special permissions — a materially stronger baseline than a plain Unix socket file on a general-purpose Linux filesystem would ever have been.
- **`process.spawn` is a genuinely powerful capability** — it can run arbitrary commands. Since this is a single-user, local-only daemon, the realistic risk is another app on the same device attempting to bind to the service without authorization, which the token handshake plus Android's own component-permission model together address. No further sandboxing is planned for v1 beyond that.

## 14. Open Decisions

1. **Session idle timeout.** Does a session with zero attached clients live forever until explicitly killed, or does it eventually expire after some period of inactivity? This affects both memory footprint over long uptimes and the exact semantics of `session.create()`/`session.kill()`.
2. **First target environment scope.** Is `nhzterm` v1 targeting a single, general Android environment, or does it need explicit handling for running inside a `proot`-based guest environment as well? This affects the PTY probe in §4 and the `cwd`/environment-detection logic in §5.
3. **Kill Process signal policy.** Does "Kill Process" send `SIGKILL` immediately, or `SIGTERM` first with a short grace period before escalating to `SIGKILL`? (§9)

## 15. Explicitly Rejected Ideas

- **A pipe-mode shell bridge** (output over SSE, input over HTTP POST, no real PTY) — rejected because it permanently blocks full-screen TUI programs from ever working.
- **"PTY support can come later"** as a framing — rejected; a real PTY is a v1 requirement, never a deferred upgrade.
- **The terminal being scoped as a feature page inside Valence Studio first** — rejected; `nhzterm` is standalone first, and anything else (including Valence Studio) integrates with it afterward via `nhzterm-api`.
- **Web technology as a dependency of the daemon itself** — rejected; confined entirely to the optional UI client layer, never the core.
- **Split-pane / multi-pane layout inside `nhzterm`** — rejected; this duplicates functionality Valence Studio's own IDE already provides.
- **Auto-clickable links** (an `addon-web-links`-style automatic linkification) — rejected in favor of the long-press "Open" flow (§10.3), matching the interaction model Termux users already know.
- **A Node.js-based daemon runtime** — rejected in favor of Kotlin, once `nhzterm` was reframed as a genuine standalone Android app rather than a script hosted inside Termux. Kotlin gets a real Foreground Service (a *permanent* phantom-process-killer exemption, not a mitigation) and removes the need for a separate bridge-process design for the in-app UI.
- **WebView + xterm.js as the terminal UI** — rejected in favor of a fully native Kotlin terminal renderer. A WebView introduces a dependency on Android System WebView (a separate, OEM-controlled component that varies across devices), causes gesture conflicts between the browser engine's own touch behavior and the terminal's custom long-press/selection needs, and contradicts the project's whole premise of being a real terminal app rather than a wrapper. The UI is fully native, not a web page embedded inside a shell.
- **A `JavascriptInterface` bridge between the UI and the daemon** — no longer needed; the native UI talks to the daemon via `LocalSocket` directly, the same way any other client does.
- **`nhzterm` needing Debian, Ubuntu, or any other existing Linux distribution as its base** — not applicable; `nhzterm` runs as its own native Android application with its own app-private storage as its root, not as a guest rootfs booted inside some other environment.

## 16. Glossary

- **PTY (pseudo-terminal):** a kernel-provided device pair (master/slave) that lets a program behave as though it's attached to a real physical terminal — required for any interactive, full-screen program like `vim`.
- **Daemon:** a background process or service with no attached terminal/UI of its own, providing a service other processes connect to. Here, specifically: an Android Foreground Service (`nhztermd`), not a plain, unprotected background process.
- **Foreground process (shell sense):** the specific command currently "owning" a terminal session's input and output at a given moment, as distinct from the shell process itself, which is always running underneath it. Not to be confused with Android's own "Foreground Service" concept (§7.3) — same word, two different meanings in this document.
- **LocalSocket:** Android's own IPC primitive, backed by Unix domain sockets, usable both within one app and across separate apps (given appropriate permissions); can bind in the abstract namespace without needing any filesystem path at all.
- **JNI (Java Native Interface):** the mechanism Kotlin/JVM code uses to call directly into native C code — this is how `nhztermd` reaches `forkpty()`-level functionality and runs `nhzsh`.
- **Ring buffer:** a fixed-size buffer that discards its oldest data once full, used here for bounded per-session scrollback storage.
- **Phantom-process-killer:** an Android 12+ system behavior that kills background processes not covered by a Foreground Service, once they exceed certain limits — the specific mechanism §7.3 addresses.
