# nhzsh

A POSIX-subset shell in C, written for **nhzterm** — the shell that runs inside `nhztermd`'s real PTY sessions. It is a first-class participant in the `nhzterm-api` ecosystem: it actively reports its foreground PID to the daemon, and it has a real built-in library system (`load`) that most shells don't offer.

Part 2 of the build plan (`../docs/nhzterm-nhzsh-build-plan.md`); design rationale in `../docs/nhzterm-project-concept.md` §5.

## Build & test

```sh
make          # builds ./nhzsh
make test     # runs the full per-phase test suite (Phases 1–8)
make clean
```

Cross-compile for Android (produces `build-android/<abi>/libnhzsh.so`, the PIE executable the APK ships in `jniLibs/` and the daemon stages to `system/bin/nhzsh`):

```sh
make android ANDROID_NDK_HOME=/path/to/ndk
```

## Usage

```sh
nhzsh               # interactive when stdin is a TTY, otherwise reads stdin
nhzsh script.sh     # run a script
nhzsh -c 'command'  # run one command
```

Interactive mode and script mode are the **same code path** (concept §5.2, Stage 5): the only differences are where lines come from and whether the `$ ` prompt is printed (suppressed when not attached to a TTY).

## Supported grammar (v1)

```
command_list := pipeline (( '&&' | '||' | ';' ) pipeline)*
pipeline     := command ('|' command)* ['&']
command      := word+ redirect*
redirect     := ('>' | '>>' | '<') word
```

- **Quoting:** single (fully literal), double (expandable, no glob/split), backslash escapes
- **Expansion:** `$VAR`, `${VAR}`, `$?`, `$$`, `$(...)` command substitution (incl. pipelines inside), `~`/`~/path`, glob `*` `?` `[...]`, IFS word-splitting of unquoted expansions
- **Builtins (in-process, no fork):** `cd`, `pwd`, `exit`, `export`, `unset`, `alias`, `unalias`, `history`, `true`, `false`, `load`, `unload`, `list-libs`
- **Everything else:** real `fork()` + `execvp()`, real pipes between pipeline stages, `$?` tracked (pipeline status = last stage)

**Not in v1 (deliberately):** control flow (`if`/`for`/`while`), functions, here-documents, `$(( ))` arithmetic, advanced parameter expansion (`${VAR:-x}`), job control beyond basic `&`. `tests/scripts/gap_probe.sh` + `tests/phase8_report.md` document these as the real-world signal for what to build next.

## The `load` library system (Phase 7)

```sh
load mylib          # finds mylib.sh on the search path, sources it once
load mylib as m     # + creates wrapper alias  m -> mylib_main
m                   # runs the library's entry point
list-libs           # name / alias / path of everything loaded
unload mylib        # removes it (and its wrapper alias)
```

- **Search path (priority order):** `<script dir>/lib/` (cwd when interactive) → `$NHZSH_USER_LIB` (default `~/.nhzsh/lib`) → `$NHZSH_SYS_LIB` (default `/usr/local/share/nhzsh/lib`). Future `nhzpm` installs can simply drop libraries into these locations.
- **Load tracking:** a second `load` of the same name is a no-op — never a silent re-source.
- **`as` convention:** `load foo as f` creates alias `f` → `foo_main`. Libraries expose a `<name>_main` entry-point alias so callers never reference full internal names (concept §5.4).

## Daemon integration hook (Phase 6)

`nhztermd` spawns nhzsh with two environment variables:

| Variable | Meaning |
|---|---|
| `NHZSH_SESSION_ID` | the daemon's session id for this PTY |
| `NHZSH_CONTROL_SOCKET` | unix socket path for the control side-channel (`@name` = Linux/Android abstract namespace) |

On every **foreground** command exec, nhzsh sends (fire-and-forget, never blocking the shell):

```json
{"type":"foreground_pid","session_id":"<id>","pid":1234}
```

and when it finishes:

```json
{"type":"foreground_pid","session_id":"<id>","pid":null}
```

Framing matches `nhzterm-api` §6.1: **4-byte big-endian length prefix + UTF-8 JSON body.** This is what makes the UI's *Kill Process* action possible without `tcgetpgrp` (concept §9). Subshells (`$(...)`) and backgrounded pipelines never report. Unreachable daemon = silent no-op; the shell carries on.

Verified by `tests/test_daemon_link.c` against a stub listener: correct frame order, reported PID proven alive mid-command, `pid: null` on completion.

## Tests

Every build-plan phase has real tests behind it (`make test`):

| Phase | Test | Covers |
|---|---|---|
| 1 Lexer | `tests/test_lexer.c` | words, operators, quotes+flags, comments, escapes, errors |
| 2 Parser | `tests/test_parser.c` | AST shape, pipelines, redirects, background, error cases |
| 3 Expander | `tests/test_expander.c` | vars, `$?`, cmdsub, splitting, globbing, tilde, combined |
| 4 Executor | `tests/test_executor.c` | builtins-in-process, pipes, redirects, `&&`/`\|\|`/`;`, `$?`, aliases, `&` |
| 5 State/REPL | `tests/test_shell.sh` | cwd/env persistence, script/stdin/`-c` modes, exit codes |
| 6 Daemon hook | `tests/test_daemon_link.c` | frame order + live PID + null, over a real socket |
| 7 `load` | `tests/test_load.sh` | search path, no-op reload, `as` wrapper, unload, errors |
| 8 Real scripts | `tests/run_phase8.sh` | unmodified `.sh` scripts + gap report (`tests/phase8_report.md`) |

The suite is also clean under `-fsanitize=address,undefined` with leak detection enabled.
