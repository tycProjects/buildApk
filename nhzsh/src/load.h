#ifndef NHZSH_LOAD_H
#define NHZSH_LOAD_H

#include "state.h"

/*
 * nhzsh library system — Part 2, Phase 7 of the build plan (concept §5.4).
 *
 *   load <name> [as <alias>]   find <name>.sh on the search path, source it
 *                              into the current shell, and (with `as`)
 *                              create a wrapper alias <alias> -> <name>_main
 *                              so callers never touch the library's full
 *                              internal entry-point name.
 *   unload <name>              remove it from the loaded table (and drop
 *                              its wrapper alias).
 *   list-libs                  show what is currently loaded.
 *
 * Search path, checked in priority order:
 *   1. <script dir>/lib/<name>.sh   (cwd/lib when interactive)
 *   2. $NHZSH_USER_LIB/<name>.sh    (default: $HOME/.nhzsh/lib)
 *   3. $NHZSH_SYS_LIB/<name>.sh     (default: /usr/local/share/nhzsh/lib)
 *
 * Loading an already-loaded library is a no-op — it never silently
 * re-sources the file.
 */

int builtin_load(ShellState *st, int argc, char **argv);
int builtin_unload(ShellState *st, int argc, char **argv);
int builtin_list_libs(ShellState *st);

#endif /* NHZSH_LOAD_H */
