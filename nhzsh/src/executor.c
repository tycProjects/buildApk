#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#include "builtins.h"
#include "daemon_link.h"
#include "executor.h"
#include "expander.h"
#include "lexer.h"
#include "parser.h"

/* Copy a flags array; length must be explicit (flag byte 0 is a valid
 * value — see the matching note in parser.c). */
static unsigned char *flags_dup(const unsigned char *f, size_t len) {
    if (!f) return NULL;
    unsigned char *r = malloc(len + 1);
    memcpy(r, f, len);
    r[len] = 0;
    return r;
}

/* ---- alias resolution ----
 * Alias substitution happens on the raw argv[0] BEFORE expansion (POSIX
 * order), replacing it with the words of the alias value. Chained aliases
 * are followed up to a fixed depth to survive `alias a=b; alias b=c`. */
static void resolve_aliases(ShellState *st, Command *c) {
    for (int depth = 0; depth < 16 && c->argc > 0; depth++) {
        Alias *a = alias_get(st, c->argv[0]);
        if (!a) break;

        int ntok;
        Token *t = lexer_tokenize(a->value, &ntok);
        int wc = 0;
        for (int i = 0; i < ntok; i++)
            if (t[i].type == TOK_WORD) wc++;
        if (wc == 0) {
            lexer_free(t, ntok);
            break;
        }

        int newargc = wc + c->argc - 1;
        char **nargv = malloc((size_t)(newargc + 1) * sizeof(char *));
        unsigned char **nflags = malloc((size_t)(newargc + 1) * sizeof(unsigned char *));
        int k = 0;
        for (int i = 0; i < ntok; i++) {
            if (t[i].type != TOK_WORD) continue;
            nargv[k] = strdup(t[i].value);
            nflags[k] = flags_dup(t[i].flags, strlen(t[i].value));
            k++;
        }
        lexer_free(t, ntok);
        for (int i = 1; i < c->argc; i++) {
            nargv[k] = c->argv[i];
            nflags[k] = c->aflags ? c->aflags[i] : NULL;
            k++;
        }
        nargv[k] = NULL;
        nflags[k] = NULL;
        free(c->argv[0]);
        if (c->aflags) free(c->aflags[0]);
        free(c->argv);
        free(c->aflags);
        c->argv = nargv;
        c->aflags = nflags;
        c->argc = newargc;
    }
}

/* ---- expansion of a command's argv (glob results may add words) ---- */
static void expand_command(ShellState *st, Command *c) {
    int cap = c->argc + 4, n = 0;
    char **na = malloc((size_t)cap * sizeof(char *));
    for (int i = 0; i < c->argc; i++) {
        int k;
        char **parts = expand_word(st, c->argv[i],
                                   c->aflags ? c->aflags[i] : NULL,
                                   strlen(c->argv[i]), &k);
        for (int j = 0; j < k; j++) {
            if (n + 2 > cap) { /* +2: always keep room for the NULL terminator */
                cap *= 2;
                na = realloc(na, (size_t)cap * sizeof(char *));
            }
            na[n++] = parts[j];
        }
        free(parts);
        free(c->argv[i]);
        if (c->aflags) free(c->aflags[i]);
    }
    free(c->argv);
    free(c->aflags);
    na[n] = NULL; /* argv stays NULL-terminated for execvp */
    c->argv = na;
    c->argc = n;
    c->aflags = NULL;
}

/* Expand a redirect target to a single pathname. */
static char *expand_target(ShellState *st, Redirect *r) {
    int k;
    char **parts = expand_word(st, r->target, r->tflags, strlen(r->target), &k);
    char *path = strdup(k > 0 ? parts[0] : "");
    free_str_array(parts, k);
    return path;
}

static int redir_oflags(RedirType t) {
    switch (t) {
    case REDIR_IN: return O_RDONLY;
    case REDIR_APPEND: return O_WRONLY | O_CREAT | O_APPEND;
    default: return O_WRONLY | O_CREAT | O_TRUNC;
    }
}

static int redir_fdno(RedirType t) { return t == REDIR_IN ? 0 : 1; }

/* Apply redirects inside the current process (builtins), saving the old
 * fds in saved[] so the caller can restore them afterwards. */
static int apply_redirs_inproc(ShellState *st, Redirect *rs, int saved[3]) {
    for (Redirect *r = rs; r; r = r->next) {
        char *path = expand_target(st, r);
        int fd = open(path, redir_oflags(r->type), 0644);
        if (fd < 0) {
            fprintf(stderr, "nhzsh: %s: %s\n", path, strerror(errno));
            free(path);
            return 0;
        }
        int fdno = redir_fdno(r->type);
        saved[fdno] = dup(fdno);
        dup2(fd, fdno);
        close(fd);
        free(path);
    }
    return 1;
}

static void restore_redirs(int saved[3]) {
    fflush(NULL);
    for (int i = 0; i < 3; i++) {
        if (saved[i] >= 0) {
            dup2(saved[i], i);
            close(saved[i]);
            saved[i] = -1;
        }
    }
}

/* Apply redirects inside a forked child; _exit(1) on failure. */
static void apply_redirs_child(ShellState *st, Redirect *rs) {
    for (Redirect *r = rs; r; r = r->next) {
        char *path = expand_target(st, r);
        int fd = open(path, redir_oflags(r->type), 0644);
        if (fd < 0) {
            fprintf(stderr, "nhzsh: %s: %s\n", path, strerror(errno));
            _exit(1);
        }
        dup2(fd, redir_fdno(r->type));
        close(fd);
        free(path);
    }
}

/* Final exec step in a forked child. Never returns. */
static void child_exec_cmd(ShellState *st, Command *c) {
    signal(SIGINT, SIG_DFL);  /* children must not inherit the interactive
                               * shell's SIG_IGN, or Ctrl+C would stop
                               * working on foreground programs */
    signal(SIGQUIT, SIG_DFL);

    if (c->argc == 0) _exit(0); /* redirect-only command: files were opened */

    if (is_builtin(c->argv[0])) {
        int rc = builtin_exec(st, c->argc, c->argv);
        fflush(NULL);
        _exit(rc & 0xff);
    }
    execvp(c->argv[0], c->argv);
    int e = errno;
    fprintf(stderr, "nhzsh: %s: %s\n", c->argv[0],
            e == ENOENT ? "not found" : strerror(e));
    fflush(NULL);
    _exit(e == ENOENT ? 127 : 126);
}

int exec_pipeline(ShellState *st, Pipeline *pl) {
    for (int i = 0; i < pl->ncmds; i++) {
        resolve_aliases(st, &pl->cmds[i]);
        expand_command(st, &pl->cmds[i]);
    }

    /* Fast path: a lone foreground builtin runs in-process — this is what
     * makes `cd` affect the shell itself (concept doc §5.2, Stage 4). */
    if (pl->ncmds == 1 && !pl->background && pl->cmds[0].argc > 0 &&
        is_builtin(pl->cmds[0].argv[0])) {
        int saved[3] = { -1, -1, -1 };
        int ok = apply_redirs_inproc(st, pl->cmds[0].redirs, saved);
        int rc = ok ? builtin_exec(st, pl->cmds[0].argc, pl->cmds[0].argv) : 1;
        restore_redirs(saved);
        st->last_status = rc;
        return rc;
    }

    int n = pl->ncmds;
    pid_t *pids = calloc((size_t)n, sizeof(pid_t));
    int prev_read = -1;

    for (int i = 0; i < n; i++) {
        int pipefd[2] = { -1, -1 };
        if (i < n - 1 && pipe(pipefd) < 0) {
            fprintf(stderr, "nhzsh: pipe: %s\n", strerror(errno));
            free(pids);
            st->last_status = 1;
            return 1;
        }
        fflush(NULL); /* keep stdio buffers out of the child */
        pid_t pid = fork();
        if (pid < 0) {
            fprintf(stderr, "nhzsh: fork: %s\n", strerror(errno));
            free(pids);
            st->last_status = 1;
            return 1;
        }
        if (pid == 0) {
            /* child: wire stdin from the previous stage, stdout to the next */
            if (prev_read >= 0) dup2(prev_read, 0);
            if (i < n - 1) dup2(pipefd[1], 1);
            if (prev_read >= 0) close(prev_read);
            if (i < n - 1) {
                close(pipefd[0]);
                close(pipefd[1]);
            }
            apply_redirs_child(st, pl->cmds[i].redirs); /* explicit redirs win */
            child_exec_cmd(st, &pl->cmds[i]);
        }
        if (i < n - 1) close(pipefd[1]);
        if (prev_read >= 0) close(prev_read);
        prev_read = (i < n - 1) ? pipefd[0] : -1;
        pids[i] = pid;
    }
    if (prev_read >= 0) close(prev_read);

    if (pl->background) {
        st->bg_count++;
        printf("[%d] %d\n", st->bg_count, (int)pids[n - 1]);
        fflush(stdout);
        for (int i = 0; i < n; i++) bg_track(st, pids[i]);
        free(pids);
        st->last_status = 0;
        return 0;
    }

    /* Foreground: report the running command to nhztermd (Phase 6),
     * wait for the pipeline, then report null. For multi-stage pipelines
     * the last stage's PID is reported; process-group semantics arrive
     * with job control, which is explicitly post-v1. */
    daemon_report_foreground(st, (int)pids[n - 1]);
    int status = 0;
    for (int i = 0; i < n; i++) {
        int s = 0;
        if (waitpid(pids[i], &s, 0) < 0) continue;
        if (i == n - 1) {
            if (WIFEXITED(s)) status = WEXITSTATUS(s);
            else if (WIFSIGNALED(s)) status = 128 + WTERMSIG(s);
        }
    }
    daemon_report_foreground(st, -1);

    free(pids);
    st->last_status = status;
    return status;
}

int exec_cmdlist(ShellState *st, CmdList *list) {
    int status = st->last_status;
    int skip = 0;
    for (int i = 0; i < list->npipes; i++) {
        if (i > 0) {
            ListOp op = list->ops[i - 1];
            if (op == OP_SEQ) skip = 0;
            else if (op == OP_AND) skip = (status != 0);
            else skip = (status == 0); /* OP_OR */
        }
        if (skip || !st->running) continue;
        status = exec_pipeline(st, &list->pipes[i]);
        st->last_status = status;
        if (!st->running) break; /* `exit` builtin */
    }
    return status;
}

char *exec_capture(ShellState *st, const char *text) {
    int pfd[2];
    if (pipe(pfd) < 0) return strdup("");
    fflush(NULL);
    pid_t pid = fork();
    if (pid < 0) {
        close(pfd[0]);
        close(pfd[1]);
        return strdup("");
    }
    if (pid == 0) {
        close(pfd[0]);
        dup2(pfd[1], 1);
        close(pfd[1]);
        /* Subshell: a copy of the state, so builtins inside $(...) cannot
         * mutate the parent shell, and the daemon hook stays silent. */
        ShellState cst = *st;
        cst.capture = 1;
        cst.control_fd = -1;
        cst.history = NULL;
        cst.nhistory = 0;
        cst.history_cap = 0;
        cst.bg = NULL;
        cst.nbg = 0;
        cst.bgcap = 0;
        int rc = exec_line(&cst, text);
        fflush(NULL);
        _exit(rc & 0xff);
    }
    close(pfd[1]);

    size_t cap = 1024, len = 0;
    char *buf = malloc(cap);
    for (;;) {
        if (cap - len < 256) {
            cap *= 2;
            buf = realloc(buf, cap);
        }
        ssize_t r = read(pfd[0], buf + len, cap - len - 1);
        if (r <= 0) break;
        len += (size_t)r;
    }
    close(pfd[0]);
    buf[len] = 0;

    int s;
    waitpid(pid, &s, 0);
    while (len > 0 && buf[len - 1] == '\n') buf[--len] = 0;
    return buf;
}

int exec_line(ShellState *st, const char *line) {
    int ntok;
    Token *toks = lexer_tokenize(line, &ntok);
    char *err = NULL;
    CmdList *list = parser_parse(toks, ntok, &err);
    lexer_free(toks, ntok);

    if (err) {
        fprintf(stderr, "nhzsh: syntax error: %s\n", err);
        free(err);
        st->last_status = 2;
        return 2;
    }
    if (!list) return st->last_status; /* empty / comment-only line */

    int rc = exec_cmdlist(st, list);
    cmdlist_free(list);
    return rc;
}

int exec_stream(ShellState *st, FILE *f) {
    char *line = NULL;
    size_t cap = 0;
    ssize_t n;
    while (st->running && (n = getline(&line, &cap, f)) > 0) {
        if (line[n - 1] == '\n') line[n - 1] = 0;
        exec_line(st, line);
        bg_reap(st);
    }
    free(line);
    return st->last_status;
}
