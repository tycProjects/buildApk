#ifndef NHZSH_EXPANDER_H
#define NHZSH_EXPANDER_H

#include <stddef.h>

#include "state.h"

/*
 * nhzsh expander — Part 2, Phase 3 of the build plan.
 *
 * Expands one raw word into zero or more final argv words, following
 * POSIX-subset semantics driven by the lexer's per-character flags:
 *
 *   1. tilde expansion        ~, ~/path          (unless literal)
 *   2. variable expansion     $VAR ${VAR} $? $$  (unless literal)
 *   3. command substitution   $(...)             (unless literal)
 *   4. word splitting         on IFS space/tab/newline produced by 2–3
 *                             (never inside quotes)
 *   5. glob expansion         * ? [...]          (never on literal chars)
 *
 * Returns a malloc'd array of malloc'd strings; *out_n may be 0 (e.g. an
 * unquoted $EMPTY expands to no words at all). A quoted empty word ("") is
 * preserved as a single empty argv entry, exactly like POSIX shells.
 */
char **expand_word(ShellState *st, const char *w, const unsigned char *flags,
                   size_t wlen, int *out_n);

void free_str_array(char **a, int n);

#endif /* NHZSH_EXPANDER_H */
