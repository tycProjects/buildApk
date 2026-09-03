#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>

#include "daemon_link.h"
#include "state.h"

void state_init(ShellState *st, int interactive) {
    memset(st, 0, sizeof *st);
    st->last_status = 0;
    st->running = 1;
    st->interactive = interactive;
    st->control_fd = -1;

    const char *e;
    if ((e = getenv("NHZSH_SESSION_ID"))) st->session_id = strdup(e);
    if ((e = getenv("NHZSH_CONTROL_SOCKET"))) st->control_socket = strdup(e);
}

void state_destroy(ShellState *st) {
    daemon_close(st);
    Alias *a = st->aliases;
    while (a) {
        Alias *nx = a->next;
        free(a->name);
        free(a->value);
        free(a);
        a = nx;
    }
    Lib *l = st->libs;
    while (l) {
        Lib *nx = l->next;
        free(l->name);
        free(l->path);
        free(l->alias);
        free(l);
        l = nx;
    }
    for (int i = 0; i < st->nhistory; i++) free(st->history[i]);
    free(st->history);
    free(st->bg);
    free(st->session_id);
    free(st->control_socket);
    free(st->script_dir);
    memset(st, 0, sizeof *st);
    st->control_fd = -1;
}

Alias *alias_get(ShellState *st, const char *name) {
    for (Alias *a = st->aliases; a; a = a->next)
        if (!strcmp(a->name, name)) return a;
    return NULL;
}

void alias_set(ShellState *st, const char *name, const char *value) {
    Alias *a = alias_get(st, name);
    if (a) {
        free(a->value);
        a->value = strdup(value);
        return;
    }
    a = calloc(1, sizeof *a);
    a->name = strdup(name);
    a->value = strdup(value);
    a->next = st->aliases;
    st->aliases = a;
}

void alias_del(ShellState *st, const char *name) {
    Alias **pp = &st->aliases;
    while (*pp) {
        if (!strcmp((*pp)->name, name)) {
            Alias *gone = *pp;
            *pp = gone->next;
            free(gone->name);
            free(gone->value);
            free(gone);
            return;
        }
        pp = &(*pp)->next;
    }
}

void history_add(ShellState *st, const char *line) {
    if (!line || !*line) return;
    if (st->nhistory + 1 > st->history_cap) {
        st->history_cap = st->history_cap ? st->history_cap * 2 : 64;
        st->history = realloc(st->history, (size_t)st->history_cap * sizeof(char *));
    }
    st->history[st->nhistory++] = strdup(line);
}

void bg_track(ShellState *st, pid_t pid) {
    if (st->nbg + 1 > st->bgcap) {
        st->bgcap = st->bgcap ? st->bgcap * 2 : 16;
        st->bg = realloc(st->bg, (size_t)st->bgcap * sizeof(pid_t));
    }
    st->bg[st->nbg++] = pid;
}

/* Opportunistic zombie reaping for backgrounded jobs (job control proper
 * is explicitly post-v1 per the build plan). */
void bg_reap(ShellState *st) {
    int w = 0;
    for (int i = 0; i < st->nbg; i++) {
        int s;
        pid_t r = waitpid(st->bg[i], &s, WNOHANG);
        if (r == 0) st->bg[w++] = st->bg[i]; /* still running */
    }
    st->nbg = w;
}
