#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "daemon_link.h"
#include "executor.h"
#include "state.h"

/*
 * nhzsh — a POSIX-subset shell (concept doc §5).
 *
 * Usage:
 *   nhzsh                interactive when stdin is a TTY, else reads stdin
 *   nhzsh script.sh      run a script file
 *   nhzsh -c 'command'   run one command
 *
 * Interactive mode and script mode are the SAME code path (concept doc
 * §5.2, Stage 5): the only differences are where lines come from and
 * whether the prompt is printed.
 */

static char *dirname_of(const char *path) {
    char *r = strdup(path);
    char *slash = strrchr(r, '/');
    if (slash) {
        if (slash == r) r[1] = 0;
        else *slash = 0;
    } else {
        free(r);
        r = strdup(".");
    }
    return r;
}

/* Read one line from fd 0 one byte at a time. Deliberately unbuffered:
 * stdio's read-ahead would steal bytes from programs the shell spawns
 * (e.g. `cat` losing the tail of piped input). */
static int read_line_stdin(char **buf, size_t *cap) {
    size_t len = 0;
    for (;;) {
        if (len + 2 > *cap) {
            *cap = *cap ? *cap * 2 : 256;
            *buf = realloc(*buf, *cap);
        }
        char c;
        ssize_t r = read(0, &c, 1);
        if (r <= 0) {
            if (len == 0) return -1; /* EOF with nothing pending */
            break;
        }
        if (c == '\n') break;
        (*buf)[len++] = c;
    }
    (*buf)[len] = 0;
    return (int)len;
}

int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);
    ShellState st;

    /* nhzsh -c 'command' */
    if (argc >= 2 && !strcmp(argv[1], "-c")) {
        state_init(&st, 0);
        exec_line(&st, argc >= 3 ? argv[2] : "");
        int rc = st.last_status;
        state_destroy(&st);
        return rc;
    }
    if (argc >= 2 && argv[1][0] == '-' && argv[1][1] != 0 && strcmp(argv[1], "-")) {
        fprintf(stderr, "nhzsh: unknown option: %s\n", argv[1]);
        return 2;
    }

    /* nhzsh script.sh */
    if (argc >= 2 && strcmp(argv[1], "-")) {
        FILE *f = fopen(argv[1], "r");
        if (!f) {
            fprintf(stderr, "nhzsh: %s: %s\n", argv[1], strerror(errno));
            return 127;
        }
        state_init(&st, 0);
        st.script_dir = dirname_of(argv[1]);
        exec_stream(&st, f);
        fclose(f);
        int rc = st.last_status;
        state_destroy(&st);
        return rc;
    }

    /* Interactive (TTY) or reading commands from piped stdin. */
    int interactive = isatty(0);
    state_init(&st, interactive);
    if (interactive) {
        /* An interactive shell must survive Ctrl+C; the foreground child
         * gets the signal via the terminal and dies on its own. Children
         * reset SIGINT to SIG_DFL before exec (executor.c). */
        signal(SIGINT, SIG_IGN);
    }

    char *buf = NULL;
    size_t cap = 0;
    while (st.running) {
        bg_reap(&st);
        if (interactive) {
            fprintf(stderr, "$ ");
            fflush(stderr);
        }
        if (read_line_stdin(&buf, &cap) < 0) {
            if (interactive) fprintf(stderr, "\n");
            break; /* EOF */
        }
        history_add(&st, buf);
        exec_line(&st, buf);
    }

    free(buf);
    int rc = st.last_status;
    state_destroy(&st);
    return rc;
}
