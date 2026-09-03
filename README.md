# nhzterm

A standalone Android terminal application with its own background daemon, its own custom shell, and a documented API. `nhzterm` is a background service (`nhztermd`) that owns real PTY sessions and speaks a documented client protocol (`nhzterm-api`) — any application can attach, control sessions, and spawn processes without needing its own shell or its own UI. The entire app is native Kotlin, top to bottom — no WebView, no JavaScript runtime, no browser engine anywhere.

Built by **NJ Daymiel** ([NHZTech](https://codeberg.org/NHZTech) / "Nick Codings").

---

## What's in this repo

| Component | What it is |
|---|---|
| **`nhztermd`** | The daemon. A Kotlin Android Foreground Service. Owns PTY sessions, listens on an Android `LocalSocket`, has zero web-tech dependency. |
| **`nhzsh`** | A custom POSIX-subset shell, written in C, that runs inside those sessions. Has a built-in library/module system (`load`) most shells don't offer. |
| **`nhzterm-api`** | The documented client protocol/SDK — session control and process control — that any application uses to talk to `nhztermd`. |
| **`nhzterm` (UI)** | The reference client: a fully native Kotlin terminal renderer, one consumer of `nhzterm-api` among possibly several. No WebView. |

## Why this exists

Most "terminal in an app" implementations either shell out over plain pipes (no real TTY, so full-screen tools like `vim`/`htop` don't work), use a WebView to embed a JavaScript terminal library (fragile, device-dependent, web-tech baggage), or tightly couple the terminal to one specific app. `nhzterm` does none of these:

- **Real PTY from day one** — full TUI app support, not deferred to a later version
- **Daemon/client split** — sessions persist independent of any UI being open, the same model `tmux` uses
- **Fully native** — the UI is a custom Kotlin terminal renderer drawing directly to a Canvas surface; no browser engine involved anywhere in the stack
- **No forced UI dependency** — the core daemon works with zero UI open; the native terminal view is just one client of it

## Architecture

```
nhztermd (Kotlin, Android Foreground Service — no web tech whatsoever)
  owns PTY sessions (via JNI → C) + nhzsh instances
  listens on an Android LocalSocket, speaks nhzterm-api
        │
   ┌────┼────────────────────┐
   │                          │
nhzterm UI              other applications
(native Kotlin          (attach as a client over
 terminal renderer,     nhzterm-api — no shell of
 talks to daemon via    their own needed)
 LocalSocket directly,
 same protocol as any
 other client)
```

Sessions are created, attached to, and controlled through `nhzterm-api` — see the full protocol spec in `docs/nhzterm-project-concept.md`.

## Features

- Real PTY sessions — `vim`, `htop`, and other full-screen terminal apps work normally
- Session persistence — close the UI, sessions keep running; reattach and pick up where you left off with scrollback replay
- Multi-session support (up to 15 concurrent), each with independent scrollback
- Auto-respawn if the service is killed, via Android's own `START_STICKY` mechanism
- Optional wake-lock setting (off by default) for keeping the CPU awake during long-running builds with the screen off
- `nhzsh`: a from-scratch, POSIX-compatible shell written in C, with a built-in library system (`load <name> [as <alias>]`)
- Long-press context menu (Copy / Paste / Open / Kill Process / Style / Keep Screen On / and more)
- Font and theme picker (25 fonts, 25 themes)
- Termux-style extra-keys row and volume-key shortcuts for touch-only devices
- Fully native UI — custom Kotlin terminal renderer, no WebView or JavaScript anywhere

## Project status

**In active development.** Being built in stages per the build plan in `docs/nhzterm-nhzsh-build-plan.md` — the daemon and session/process control are in place and confirmed working on a real device; the native Kotlin UI renderer is the current active work.

Not yet ready for general use. No installable release yet.

## Repository layout

```
nhzterm/
├── app/
│   ├── src/main/
│   │   ├── kotlin/       # nhztermd service logic, nhzterm-api server side,
│   │   │                 # native terminal renderer UI
│   │   ├── jniLibs/      # prebuilt nhzsh + PTY-helper .so per ABI
│   │   ├── assets/       # default themes, help doc
│   │   └── res/          # Android resources, foreground-service notification
├── nhzsh/                # nhzsh source (C)
├── api/                  # nhzterm-api client library (for external apps)
└── docs/                 # concept doc + build plan
```

## Building

Standard Android Gradle project (Kotlin + NDK for the `nhzsh`/PTY native layer). SDK and Gradle toolchain are managed by the broader NHZTech development environment — see `docs/nhzterm-nhzsh-build-plan.md` Phase 0 for details.

## Design docs

- `docs/nhzterm-project-concept.md` — full architecture, protocol spec, directory layout, UI spec
- `docs/nhzterm-nhzsh-build-plan.md` — the step-by-step implementation plan this project follows

## License

TBD.

## Author

NJ Daymiel — NHZTech / Nick Codings — Dipolog City, Philippines
