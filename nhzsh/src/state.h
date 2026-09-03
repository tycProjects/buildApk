#ifndef NHZSH_STATE_H
#define NHZSH_STATE_H

#include <sys/types.h>

/*
 * nhzsh state — Part 2, Phase 5 of the build plan.
 *
 * The shell process is long-lived: cwd lives in the OS process itself
 * (chdir), environment variables live in the real environ (setenv/getenv),
 * and everything else lives here. Interactive mode and script mode share
 * this exact same state and loop (concept doc §5.2, Stage 5).
 */

typedef struct Alias {
    char *name;
    char *value;
    struct Alias *next;
} Alias;

/* A loaded library, tracked by the `load` builtin (Phase 7). */
typedef struct Lib {
    char *name;             /* library name as given to `load` */
    char *path;             /* file that was sourced */
    char *alias;            /* alias created by `as <alias>`, or NULL */
    struct Lib *next;
} Lib;

typedef struct ShellState {
    int last_status;      /* $? */
    int running;          /* REPL loop control; `exit` clears it */
    int interactive;      /* isatty(stdin) at startup */
    int capture;          /* non-zero while running inside $(...) — a subshell */

    /* Daemon integration hook (Phase 6, concept doc §9).
     * nhztermd spawns nhzsh with NHZSH_SESSION_ID and NHZSH_CONTROL_SOCKET set. */
    char *session_id;
    char *control_socket; /* unix socket path; leading '@' = abstract namespace */
    int control_fd;       /* lazily connected; -1 = not connected */

    Alias *aliases;
    Lib *libs;

    char **history;       /* in-memory command history (basic, per plan) */
    int nhistory;
    int history_cap;

    char *script_dir;     /* directory of the running script, for lib/ search */

    pid_t *bg;            /* backgrounded pids, for zombie reaping */
    int nbg;
    int bgcap;
    int bg_count;         /* job counter for `[N] pid` messages */
} ShellState;

void state_init(ShellState *st, int interactive);
void state_destroy(ShellState *st);

Alias *alias_get(ShellState *st, const char *name);
void alias_set(ShellState *st, const char *name, const char *value);
void alias_del(ShellState *st, const char *name);

void history_add(ShellState *st, const char *line);

void bg_track(ShellState *st, pid_t pid);
void bg_reap(ShellState *st);

#endif /* NHZSH_STATE_H */
