#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "parser.h"

typedef struct {
    Token *t;
    int n;
    int i;
    char *err;
} P;

static void perr(P *p, const char *m) {
    if (!p->err) p->err = strdup(m);
}

static Token *cur(P *p) { return &p->t[p->i]; }

/* Copy a flags array. NOTE: the length must be passed explicitly — a flag
 * byte of 0 is a perfectly valid value (plain unquoted char), so strlen()
 * would truncate at the first one. len is always strlen(word). */
static unsigned char *flags_dup(const unsigned char *f, size_t len) {
    if (!f) return NULL;
    unsigned char *r = malloc(len + 1);
    memcpy(r, f, len);
    r[len] = 0;
    return r;
}

static void command_clear(Command *c) {
    for (int i = 0; i < c->argc; i++) {
        free(c->argv[i]);
        if (c->aflags) free(c->aflags[i]); /* NULL after expand_command ran */
    }
    free(c->argv);
    free(c->aflags);
    Redirect *r = c->redirs;
    while (r) {
        Redirect *nx = r->next;
        free(r->target);
        free(r->tflags);
        free(r);
        r = nx;
    }
    memset(c, 0, sizeof *c);
}

static void pipeline_clear(Pipeline *pl) {
    for (int i = 0; i < pl->ncmds; i++) command_clear(&pl->cmds[i]);
    free(pl->cmds);
    memset(pl, 0, sizeof *pl);
}

void cmdlist_free(CmdList *l) {
    if (!l) return;
    for (int i = 0; i < l->npipes; i++) pipeline_clear(&l->pipes[i]);
    free(l->pipes);
    free(l->ops);
    free(l);
}

/* Parse one command: words and redirects in any order (a superset of the
 * strict `word+ redirect*` grammar — real shells accept interleaving too).
 * Returns 0 on error or if nothing was consumed; cleans up after itself
 * on error. */
static int parse_command(P *p, Command *out) {
    int acap = 4;
    memset(out, 0, sizeof *out);
    /* +1 slot: argv stays NULL-terminated for execvp at all times */
    out->argv = malloc((size_t)(acap + 1) * sizeof(char *));
    out->aflags = malloc((size_t)(acap + 1) * sizeof(unsigned char *));
    Redirect *rtail = NULL;
    int saw_any = 0;

    for (;;) {
        Token *t = cur(p);
        if (t->type == TOK_WORD) {
            if (out->argc + 1 > acap) {
                acap *= 2;
                out->argv = realloc(out->argv, (size_t)(acap + 1) * sizeof(char *));
                out->aflags = realloc(out->aflags, (size_t)(acap + 1) * sizeof(unsigned char *));
            }
            out->argv[out->argc] = strdup(t->value);
            out->aflags[out->argc] = flags_dup(t->flags, strlen(t->value));
            out->argc++;
            out->argv[out->argc] = NULL;
            out->aflags[out->argc] = NULL;
            saw_any = 1;
            p->i++;
        } else if (t->type == TOK_GT || t->type == TOK_DGT || t->type == TOK_LT) {
            RedirType rt = (t->type == TOK_GT)    ? REDIR_OUT
                           : (t->type == TOK_DGT) ? REDIR_APPEND
                                                  : REDIR_IN;
            p->i++;
            if (cur(p)->type != TOK_WORD) {
                perr(p, "expected a filename after redirection operator");
                command_clear(out);
                return 0;
            }
            Redirect *r = calloc(1, sizeof *r);
            r->type = rt;
            r->target = strdup(cur(p)->value);
            r->tflags = flags_dup(cur(p)->flags, strlen(cur(p)->value));
            if (rtail) rtail->next = r; else out->redirs = r;
            rtail = r;
            saw_any = 1;
            p->i++;
        } else {
            if (t->type == TOK_ERR) perr(p, t->value);
            break;
        }
    }
    return saw_any;
}

/* Parse one pipeline: command ('|' command)*, with an optional trailing '&'.
 * Returns 0 on error; cleans up everything it consumed. */
static int parse_pipeline(P *p, Pipeline *out) {
    int pcap = 4;
    memset(out, 0, sizeof *out);
    out->cmds = malloc((size_t)pcap * sizeof(Command));

    for (;;) {
        Command c;
        if (!parse_command(p, &c)) {
            pipeline_clear(out);
            return 0;
        }
        if (out->ncmds + 1 > pcap) {
            pcap *= 2;
            out->cmds = realloc(out->cmds, (size_t)pcap * sizeof(Command));
        }
        out->cmds[out->ncmds++] = c;
        if (cur(p)->type == TOK_PIPE) {
            p->i++;
            continue;
        }
        break;
    }

    if (cur(p)->type == TOK_AMP) {
        out->background = 1;
        p->i++;
    }
    return 1;
}

CmdList *parser_parse(Token *toks, int ntok, char **err_out) {
    P p = { toks, ntok, 0, NULL };
    *err_out = NULL;

    if (cur(&p)->type == TOK_ERR) {
        *err_out = strdup(toks[0].value ? toks[0].value : "lexical error");
        return NULL;
    }
    if (cur(&p)->type == TOK_EOF) return NULL; /* empty / comment-only line */

    CmdList *l = calloc(1, sizeof *l);
    int pcap = 4;
    l->pipes = malloc((size_t)pcap * sizeof(Pipeline));
    l->ops = malloc((size_t)pcap * sizeof(ListOp));

    for (;;) {
        Pipeline pl;
        if (!parse_pipeline(&p, &pl)) {
            if (!p.err) perr(&p, "unexpected token");
            goto fail;
        }
        if (l->npipes + 1 > pcap) {
            pcap *= 2;
            l->pipes = realloc(l->pipes, (size_t)pcap * sizeof(Pipeline));
            l->ops = realloc(l->ops, (size_t)pcap * sizeof(ListOp));
        }
        l->pipes[l->npipes++] = pl;

        TokenType ty = cur(&p)->type;
        if (ty == TOK_DAND || ty == TOK_DPIPE || ty == TOK_SEMI) {
            l->ops[l->nops++] = (ty == TOK_DAND) ? OP_AND : (ty == TOK_DPIPE) ? OP_OR : OP_SEQ;
            p.i++;
            if (cur(&p)->type == TOK_EOF) {
                if (ty == TOK_SEMI) break; /* trailing ';' is fine */
                perr(&p, "unexpected end of line");
                goto fail;
            }
            if (cur(&p)->type == TOK_ERR) {
                perr(&p, cur(&p)->value);
                goto fail;
            }
            continue;
        }
        if (ty == TOK_EOF) break;
        perr(&p, "unexpected token");
        goto fail;
    }

    return l;

fail:
    *err_out = p.err ? p.err : strdup("parse error");
    cmdlist_free(l);
    return NULL;
}
