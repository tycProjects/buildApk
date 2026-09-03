#ifndef NHZSH_BUILTINS_H
#define NHZSH_BUILTINS_H

#include "state.h"

/*
 * nhzsh builtins — Part 2, Phase 4 (cd, export, unset, alias, pwd, exit)
 * and Phase 7 (load, unload, list-libs), plus small companions
 * (unalias, history, true, false, echo-as-fallback is NOT here — echo is
 * an external command; the core set matches the build plan).
 *
 * All builtins run in-process: no fork().
 */

int is_builtin(const char *name);

/* Run a builtin; returns its exit status. */
int builtin_exec(ShellState *st, int argc, char **argv);

#endif /* NHZSH_BUILTINS_H */
