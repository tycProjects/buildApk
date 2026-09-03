# Phase 7 — Reference UI: Real-Device Verification Kit

**Purpose:** the build plan's #1 resume-order item. The two Phase 7 fixes (session side panel + `≡` key) were specified after a real-device test found them missing, and the project's own rule is explicit: *"the pattern of 'looks right in code, broken on a real device' has already happened twice — real-device confirmation is not optional."* This kit turns that rule into a concrete, sign-off-able procedure.

**Device target:** arm64-v8a, Android 12+ (phantom-process-killer territory), fresh install preferred.

**Sign-off rule:** every box below must be checked **on the device**, not in code review. A feature that exists in source but fails on-device counts as FAILED.

---

## Part A — Session side panel (concept doc §10.4) — THE fix under test

| # | Step | Expected | Pass |
|---|------|----------|------|
| A1 | Fresh install → open app | A session is **auto-created and attached** (`session.list()` empty → client calls `session.create()`). Terminal shows a live `nhzsh` prompt — never a blank screen with no next step. | ☐ |
| A2 | Locate the persistent session side panel (drawer/tab) | Panel is reachable from the main terminal view at all times, without leaving it. | ☐ |
| A3 | Open the panel | Lists every open session with **live status** per session: running / idle / finished. | ☐ |
| A4 | Find the **“+” / New Session** action | Visible in the panel without scrolling gymnastics; always reachable. | ☐ |
| A5 | Tap “+” | New session created (`session.create`), appears in the list, and the UI attaches to it. | ☐ |
| A6 | In session 2: `echo from-two` → switch back to session 1 → switch to session 2 again | Session 2's scrollback replays, `from-two` is visible. Sessions persist independently. | ☐ |
| A7 | Kill the attached session (type `exit` or Kill it from the panel) | UI does **not** dead-end: it either attaches to another live session or offers creation. **Blank terminal with no way forward = FAIL** (this is the original bug). | ☐ |
| A8 | Rename a session from the panel (`session.rename`) | New name persists across app close + reopen (metadata persisted in `sessions/<id>/meta.json`). | ☐ |
| A9 | Create sessions up to 15, then try a 16th | 16th is refused cleanly — the UI surfaces the `SESSION_LIMIT_REACHED` error honestly; no crash, no silent no-op. | ☐ |

## Part B — Extra-keys bar (concept doc §10.6) — THE other fix under test

**Row 1 must render exactly these 8 keys, in order — count them on screen:**
`≡` `ESC` `TAB` `CTRL` `ALT` `HOME` `↑` `END`

**Row 2 must render exactly these 7 keys, in order:**
`/` `-` `PGUP` `PGDN` `←` `↓` `→`

| # | Step | Expected | Pass |
|---|------|----------|------|
| B1 | Look at Row 1 | **8 keys visible, `≡` present in position 1.** 7-of-8 rendering = the known bug = FAIL. | ☐ |
| B2 | Look at Row 2 | 7 keys visible, none missing. | ☐ |
| B3 | Tap `≡` | It **does something meaningful** — toggles the extra-keys rows or opens the session panel (§10.4). An inert, unwired icon = FAIL. Record which behavior it implements: ____________ | ☐ |
| B4 | Tap `ESC` while in `vi`/nano-equivalent | Escape is delivered (mode change observable). | ☐ |
| B5 | Tap `TAB` after typing a partial command | Completion attempted (bell/list or completion, depending on shell state). | ☐ |
| B6 | Tap `CTRL` then `C` while `sleep 30` runs | SIGINT delivered — `sleep` dies, prompt returns. | ☐ |
| B7 | Tap `ALT` then a key | Alt modifier delivered (e.g. Alt+B moves back one word at the prompt). | ☐ |
| B8 | Tap `HOME` / `END` at a long prompt line | Cursor jumps to line start / end. | ☐ |
| B9 | Tap `↑` `↓` `←` `→` | Arrows delivered (history / cursor movement). | ☐ |
| B10 | Tap `PGUP` `PGDN` | Scrollback pages (or delivered as sequences, per implementation — record which: ____________). | ☐ |
| B11 | Tap `/` and `-` | Characters arrive at the prompt exactly as typed. | ☐ |

## Part C — Regression sweep (previously-working Phase 7 features must still work)

| # | Step | Expected | Pass |
|---|------|----------|------|
| C1 | Run `vim` (or any full-screen TUI) inside a session | Renders correctly — real PTY, alternate screen, cursor addressing. | ☐ |
| C2 | Run `htop`-equivalent if available | Live redraw without corruption. | ☐ |
| C3 | Long-press the terminal surface | Context menu: Copy / Paste / Open (conditional) / More → (Open URL, Share Selected Text, Refresh, Kill Process, Style, Keep Screen On, Help, Settings, Report Issue). | ☐ |
| C4 | More → **Kill Process** while `sleep 60` runs | The `sleep` dies; **the session and shell survive** (prompt returns). With the verified `nhzsh` hook wired, this now works end-to-end via `process.kill` against the reported foreground PID. | ☐ |
| C5 | More → Kill Process while nothing is running | Honest "nothing is currently running" state — **not** a dead tap (daemon returns `PROCESS_NOT_FOUND`, §9). | ☐ |
| C6 | More → Refresh on a live session | View recovers (re-render / scrollback re-request); session is **not** killed. | ☐ |
| C7 | Style picker | Font change re-renders glyphs; theme change swaps the palette. Spot-check ≥2 fonts + ≥2 themes (defaults: Dracula, Nord, Gruvbox Dark, Matrix Green). | ☐ |
| C8 | Volume keys (if targeting touch-only devices): Vol Down+C, Vol Up+E, Vol Up+W | Ctrl+C interrupt, Esc, Up-arrow respectively (§10.7). | ☐ |
| C9 | Close the app UI entirely → reopen | Sessions survived (daemon is headless-first); scrollback replays on reattach. | ☐ |
| C10 | Force-stop the app from Android Settings | Persistent notification + `START_STICKY` bring `nhztermd` back on its own; reconnect works. | ☐ |
| C11 | Daemon notification | "nhztermd running" notification appears **immediately** at service start (§7.3 / §12.3 — this is the phantom-killer exemption, not polish). | ☐ |
| C12 | On-device file check (via the terminal itself): `ls -l <filesDir>/system/bin/nhzsh` | Staged binary exists and is executable **after daemon startup** (§12.3 checklist item — re-verify after any daemon-startup change). | ☐ |

## Sign-off

```
Verified by: ______________________  Device: ______________________
Android version: ____________  App build: ____________  Date: ____________

Part A (session panel):  ___ / 9      Part B (extra keys): ___ / 11
Part C (regression):     ___ / 12

Result:  ☐ PASS — Phase 7 verified on real device, project is stable end-to-end
         ☐ FAIL — record failures below and re-run this kit after fixes
```

**Failures found:**

| Item | What actually happened |
|------|------------------------|
|      |                        |

---

*Companion note:* nhzsh's side of C4/C5 (foreground-PID reporting, Part 2 Phase 6) is now independently verified in the development workspace — `tests/test_daemon_link.c` proves the `foreground_pid` frames (pid → alive → null) arrive correctly over the length-prefixed control channel. What remains device-specific is the Kotlin daemon's handling of those frames and the UI's wiring of `process.kill`.
