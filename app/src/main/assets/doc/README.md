# nhzterm — in-app help

## What this is
A real terminal. `nhztermd` (a Foreground Service) owns real PTY sessions
running `nhzsh`; this UI is one client of it — sessions survive closing
this window, exactly like tmux.

## Sessions
- Open the side panel: long-press `≡` (bottom-left) or Ctrl+Alt+→
- `+ New Session` creates one; tap a session to attach; long-press to rename; `✕` kills
- Status dots: green = running, amber = idle, gray = finished
- First launch auto-creates a session — you can never hit a dead end

## Long-press menu
Copy · Paste · Open (link under selection) · More → Open URL / Share /
Refresh (view only — never kills the session) / Kill Process (PID) /
Style / Keep Screen On / Help / Settings / Report Issue

**Kill Process** terminates the command currently in the session's
foreground (nhzsh reports it live); the shell itself survives. Nothing
running? You get an honest message, not a dead tap.

## Extra keys
Row 1: `≡ ESC TAB CTRL ALT HOME ↑ END`
Row 2: `/ - PGUP PGDN ← ↓ →`
- `≡` **tap = open/close the session side panel**, **long-press =
  toggle the extra-key rows**
- CTRL/ALT stay armed until your next key, Termux-style

## Zoom (Termux-style)
- **Pinch** on the terminal = zoom text in/out (live, 0.4x–4x)
- Ctrl+Alt `+` / `-` = step zoom (hardware keyboard)
- Long-press menu → Style → Text size also works

## Volume keys (touch-only devices)
- Vol Down + key = Ctrl + key (Vol Down+C = SIGINT)
- Vol Up + Q/K: toggle extra keys · E: Esc · T: Tab · W/S/A/D: arrows
- Vol Up + L: | · H: ~ · U: _ · P/N: page up/down · B/F: word back/forward
- Vol Up + .: SIGQUIT · Vol Up + 1..0: F1..F10 · Vol Up + V: system volume

## Keyboard shortcuts
Ctrl+A/E/C/D/Z/L/K/U/W/Y/R — readline classics.
Ctrl+Alt: C new session · R rename · N/P next/prev · 1-9 jump ·
U link picker · V paste · M menu · K toggle keyboard · +/− zoom ·
→/← panel open/close.

## Shell notes (nhzsh)
POSIX-subset: pipes, redirects, &&, ||, ;, background &, $VAR, $(...),
globs, ~. Builtins: cd pwd exit export unset alias unalias history
load unload list-libs. Library system: `load mylib as m` then `m`.

## Links
Links are plain text by design — long-press → Open, or Ctrl+Alt+U for
the on-screen link picker.
