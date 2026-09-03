#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lexer.h"

static void *xmalloc(size_t n) {
    void *p = malloc(n ? n : 1);
    if (!p) {
        fprintf(stderr, "nhzsh: out of memory\n");
        exit(1);
    }
    return p;
}

static void *xrealloc(void *p, size_t n) {
    void *r = realloc(p, n ? n : 1);
    if (!r) {
        fprintf(stderr, "nhzsh: out of memory\n");
        exit(1);
    }
    return r;
}

/* Characters that terminate a word. '#' is NOT here: like POSIX shells,
 * it only starts a comment at the beginning of a word (handled in the
 * main loop), so `echo a#b` passes `a#b` through as one word. */
static const char *WORD_TERMS = " \t\n\r|&;<>";

/* If *p begins an unquoted-context command substitution "$(", consume the
 * whole $(...) — including any spaces, pipes, and nested parens inside —
 * so it survives lexing as ONE word and the expander can run it later.
 * Quotes and backslashes inside the substitution are respected so parens
 * within them don't confuse the nesting count.
 * Returns the pointer just past the closing ')', or NULL if the
 * substitution is unterminated (caller then treats '$' as literal). */
static const char *scan_cmdsub(const char *p) {
    const char *q = p + 2;
    int depth = 1;
    while (*q && depth > 0) {
        if (*q == '\'') {
            q++;
            while (*q && *q != '\'') q++;
            if (*q) q++;
        } else if (*q == '"') {
            q++;
            while (*q && *q != '"') {
                if (*q == '\\' && q[1]) q++;
                q++;
            }
            if (*q) q++;
        } else if (*q == '\\' && q[1]) {
            q += 2;
        } else if (*q == '(') {
            depth++;
            q++;
        } else if (*q == ')') {
            depth--;
            q++;
        } else {
            q++;
        }
    }
    return depth == 0 ? q : NULL;
}

Token *lexer_tokenize(const char *line, int *count_out) {
    int cap = 16, n = 0;
    Token *toks = xmalloc((size_t)cap * sizeof(Token));
    const char *p = line;

#define PUSH_OP(ty, adv)                          \
    do {                                          \
        if (n + 2 > cap) {                        \
            cap *= 2;                             \
            toks = xrealloc(toks, (size_t)cap * sizeof(Token)); \
        }                                         \
        toks[n].type = (ty);                      \
        toks[n].value = NULL;                     \
        toks[n].flags = NULL;                     \
        n++;                                      \
        p += (adv);                               \
    } while (0)

    while (*p) {
        if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r') {
            p++;
            continue;
        }
        if (*p == '#') { /* comment: skip to end of line */
            while (*p && *p != '\n') p++;
            continue;
        }

        switch (*p) {
        case '|':
            if (p[1] == '|') PUSH_OP(TOK_DPIPE, 2); else PUSH_OP(TOK_PIPE, 1);
            continue;
        case '&':
            if (p[1] == '&') PUSH_OP(TOK_DAND, 2); else PUSH_OP(TOK_AMP, 1);
            continue;
        case ';':
            PUSH_OP(TOK_SEMI, 1);
            continue;
        case '>':
            if (p[1] == '>') PUSH_OP(TOK_DGT, 2); else PUSH_OP(TOK_GT, 1);
            continue;
        case '<':
            PUSH_OP(TOK_LT, 1);
            continue;
        default:
            break;
        }

        /* ---- word ---- */
        size_t bcap = 32, blen = 0;
        char *buf = xmalloc(bcap);
        unsigned char *fl = xmalloc(bcap);
        int err = 0;

#define PUSH_CH(c, f)                                    \
    do {                                                 \
        if (blen + 2 > bcap) {                           \
            bcap *= 2;                                   \
            buf = xrealloc(buf, bcap);                   \
            fl = xrealloc(fl, bcap);                     \
        }                                                \
        buf[blen] = (c);                                 \
        fl[blen] = (unsigned char)(f);                   \
        blen++;                                          \
    } while (0)

        while (*p && !strchr(WORD_TERMS, *p)) {
            if (*p == '\'') { /* single quotes: fully literal */
                p++;
                while (*p && *p != '\'') {
                    PUSH_CH(*p, FL_NOEXPAND | FL_NOGLOB);
                    p++;
                }
                if (!*p) { err = 1; break; }
                p++;
            } else if (*p == '"') { /* double quotes: expandable, but no glob/split */
                p++;
                while (*p && *p != '"') {
                    if (*p == '\\' && p[1] && strchr("$`\"\\\n", p[1])) {
                        p++;
                        if (*p == '\n') { p++; continue; } /* line continuation */
                        PUSH_CH(*p, FL_NOEXPAND | FL_NOGLOB);
                        p++;
                    } else if (*p == '$' && p[1] == '(') {
                        /* $(...) inside double quotes: one atomic unit */
                        const char *end = scan_cmdsub(p);
                        if (end) {
                            while (p < end) {
                                PUSH_CH(*p, FL_NOGLOB);
                                p++;
                            }
                        } else {
                            PUSH_CH(*p, FL_NOGLOB);
                            p++;
                        }
                    } else {
                        PUSH_CH(*p, FL_NOGLOB);
                        p++;
                    }
                }
                if (!*p) { err = 1; break; }
                p++;
            } else if (*p == '\\' && p[1]) { /* unquoted backslash escape */
                p++;
                PUSH_CH(*p, FL_NOEXPAND | FL_NOGLOB);
                p++;
            } else if (*p == '$' && p[1] == '(') {
                /* unquoted $(...): one atomic unit, spaces/pipes included */
                const char *end = scan_cmdsub(p);
                if (end) {
                    while (p < end) {
                        PUSH_CH(*p, 0);
                        p++;
                    }
                } else {
                    PUSH_CH(*p, 0);
                    p++;
                }
            } else {
                PUSH_CH(*p, 0);
                p++;
            }
        }
#undef PUSH_CH

        if (n + 2 > cap) {
            cap *= 2;
            toks = xrealloc(toks, (size_t)cap * sizeof(Token));
        }

        if (err) {
            free(buf);
            free(fl);
            toks[n].type = TOK_ERR;
            toks[n].value = strdup("unterminated quote");
            toks[n].flags = NULL;
            n++;
            break; /* stop scanning; caller sees the error */
        }

        buf[blen] = 0;
        fl[blen] = 0;
        toks[n].type = TOK_WORD;
        toks[n].value = buf;
        toks[n].flags = fl;
        n++;
    }

#undef PUSH_OP

    if (n + 1 > cap) {
        cap += 1;
        toks = xrealloc(toks, (size_t)cap * sizeof(Token));
    }
    toks[n].type = TOK_EOF;
    toks[n].value = NULL;
    toks[n].flags = NULL;
    n++;

    *count_out = n;
    return toks;
}

void lexer_free(Token *toks, int count) {
    if (!toks) return;
    for (int i = 0; i < count; i++) {
        free(toks[i].value);
        free(toks[i].flags);
    }
    free(toks);
}
