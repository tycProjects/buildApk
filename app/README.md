# app/ — nhzterm Android application (stub)

This directory will hold the Kotlin Android app:

- **`nhztermd`** — Foreground Service daemon: PTY session ownership, `nhzsh`
  staging (`system/bin/nhzsh` from `libnhzsh.so`), the `nhzterm-api` server on
  an abstract-namespace `LocalSocket`, token auth, foreground-PID map fed by
  nhzsh's control channel, scrollback ring buffers.
- **`nhzterm` UI** — the native Kotlin terminal renderer (reference client;
  talks to the daemon over the same LocalSocket protocol as any external app).
- **`jniLibs/`** — per-ABI `libnhzsh.so` (built via `../nhzsh/Makefile`'s
  `make android`).

**Status:** not yet built in this workspace. Per `../docs/STATUS.md`, the
existing implementation lives on the author's device/environment; a decision
is pending on whether to port those sources here or rebuild fresh. Until then,
the authoritative specs are:

- `../docs/nhzterm-project-concept.md` (§3, §6, §7, §9, §10, §12)
- `../docs/nhzterm-nhzsh-build-plan.md` (Part 1, Phases 0–8)
- `../docs/ui-phase7-device-verification.md` (the current top-priority task)
