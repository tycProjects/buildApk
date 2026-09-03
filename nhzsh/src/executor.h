#ifndef NHZSH_EXECUTOR_H
#define NHZSH_EXECUTOR_H

#include <stdio.h>

#include "parser.h"
#include "state.h"

/*
 * nhzsh executor — Part 2, Phase 4 of the build plan.
 *
 * Builtins run in-process (no fork). Everything else is a real child
 * process via fork()+exec(), with real pipes between pipeline stages.
 */

/* Execute a parsed command list; returns the exit status of the last
 * pipeline actually run (honoring && / || short-circuiting). */
int exec_cmdlist(ShellState *st, CmdList *list);

/* Execute one pipeline. Foreground pipelines report their PID to
 * nhztermd via the daemon hook (Phase 6) for the duration of the run. */
int exec_pipeline(ShellState *st, Pipeline *pl);

/* Run `text` with stdout captured (used for $(...) command substitution).
 * Runs in a forked child so builtins inside cannot mutate the parent
 * shell's state — POSIX subshell semantics. Returns malloc'd output with
 * trailing newlines stripped. */
char *exec_capture(ShellState *st, const char *text);

/* Parse + execute one line of input. This is the single shared code path
 * for interactive input, script files, `-c`, and sourced libraries. */
int exec_line(ShellState *st, const char *line);

/* Read and execute lines from an open file (script mode / `load`). */
int exec_stream(ShellState *st, FILE *f);

#endif /* NHZSH_EXECUTOR_H */
