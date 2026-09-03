#include <ctype.h>
#include <glob.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "expander.h"
#include "executor.h"
#include "lexer.h"

/* Growable buffer carrying per-character flags alongside the text. */
typedef struct {
    char *s;
    unsigned char *f;
    size_t len, cap;
} SBuf;

static void sb_init(SBuf *b) {
    b->cap = 64;
    b->s = malloc(b->cap);
    b->f = malloc(b->cap);
    b->len = 0;
    b->s[0] = 0;
}

static void sb_reserve(SBuf *b, size_t extra) {
    if (b->len + extra + 1 > b->cap) {
        while (b->len + extra + 1 > b->cap) b->cap *= 2;
        b->s = realloc(b->s, b->cap);
        b->f = realloc(b->f, b->cap);
    }
}

static void sb_push(SBuf *b, char c, unsigned char fl) {
    sb_reserve(b, 1);
    b->s[b->len] = c;
    b->f[b->len] = fl;
    b->len++;
    b->s[b->len] = 0;
}

static void sb_append(SBuf *b, const char *s, size_t n, unsigned char fl) {
    sb_reserve(b, n);
    for (size_t i = 0; i < n; i++) sb_push(b, s[i], fl);
}

static void sb_free(SBuf *b) {
    free(b->s);
    free(b->f);
    b->s = NULL;
    b->f = NULL;
    b->len = b->cap = 0;
}

/* ---- pass 1: tilde expansion ---- */
static void pass_tilde(SBuf *in, SBuf *out) {
    if (in->len > 0 && in->s[0] == '~' && !(in->f[0] & FL_NOEXPAND) &&
        (in->len == 1 || in->s[1] == '/')) {
        const char *home = getenv("HOME");
        if (home) {
            sb_append(out, home, strlen(home), (unsigned char)(in->f[0] & FL_NOGLOB));
            for (size_t i = 1; i < in->len; i++) sb_push(out, in->s[i], in->f[i]);
            return;
        }
    }
    for (size_t i = 0; i < in->len; i++) sb_push(out, in->s[i], in->f[i]);
}

/* ---- pass 2: $VAR / ${VAR} / $? / $$ ---- */
static void pass_vars(ShellState *st, SBuf *in, SBuf *out) {
    size_t i = 0;
    while (i < in->len) {
        if (in->s[i] != '$' || (in->f[i] & FL_NOEXPAND)) {
            sb_push(out, in->s[i], in->f[i]);
            i++;
            continue;
        }
        unsigned char vfl = (unsigned char)((in->f[i] & FL_NOGLOB) | FL_EXPANDED);

        if (i + 1 < in->len && in->s[i + 1] == '?') {
            char b[16];
            int n = snprintf(b, sizeof b, "%d", st->last_status);
            sb_append(out, b, (size_t)n, vfl);
            i += 2;
            continue;
        }
        if (i + 1 < in->len && in->s[i + 1] == '$') {
            char b[24];
            int n = snprintf(b, sizeof b, "%d", (int)getpid());
            sb_append(out, b, (size_t)n, vfl);
            i += 2;
            continue;
        }
        if (i + 1 < in->len && in->s[i + 1] == '{') {
            size_t j = i + 2;
            while (j < in->len && in->s[j] != '}') j++;
            if (j < in->len && j - (i + 2) < 256) {
                char name[256];
                size_t nl = j - (i + 2);
                memcpy(name, in->s + i + 2, nl);
                name[nl] = 0;
                const char *v = getenv(name);
                if (v) sb_append(out, v, strlen(v), vfl);
                i = j + 1;
                continue;
            }
            /* malformed ${ : fall through as literal '$' */
        }
        if (i + 1 < in->len &&
            (isalpha((unsigned char)in->s[i + 1]) || in->s[i + 1] == '_')) {
            size_t j = i + 1;
            while (j < in->len &&
                   (isalnum((unsigned char)in->s[j]) || in->s[j] == '_'))
                j++;
            if (j - (i + 1) < 256) {
                char name[256];
                size_t nl = j - (i + 1);
                memcpy(name, in->s + i + 1, nl);
                name[nl] = 0;
                const char *v = getenv(name);
                if (v) sb_append(out, v, strlen(v), vfl);
                i = j;
                continue;
            }
        }
        sb_push(out, in->s[i], in->f[i]);
        i++;
    }
}

/* ---- pass 3: $(...) command substitution ---- */
static void pass_cmdsub(ShellState *st, SBuf *in, SBuf *out) {
    size_t i = 0;
    while (i < in->len) {
        if (in->s[i] == '$' && !(in->f[i] & FL_NOEXPAND) && i + 1 < in->len &&
            in->s[i + 1] == '(') {
            int depth = 1;
            size_t j = i + 2;
            while (j < in->len && depth > 0) {
                if (in->s[j] == '(') depth++;
                else if (in->s[j] == ')') depth--;
                if (depth > 0) j++;
            }
            if (depth == 0) { /* j sits on the matching ')' */
                size_t ilen = j - (i + 2);
                char *inner = malloc(ilen + 1);
                memcpy(inner, in->s + i + 2, ilen);
                inner[ilen] = 0;
                char *res = exec_capture(st, inner);
                free(inner);
                unsigned char vfl = (unsigned char)((in->f[i] & FL_NOGLOB) | FL_EXPANDED);
                sb_append(out, res, strlen(res), vfl);
                free(res);
                i = j + 1;
                continue;
            }
            /* unmatched '(' : literal */
        }
        sb_push(out, in->s[i], in->f[i]);
        i++;
    }
}

/* ---- pass 4: word splitting on expansion-produced IFS whitespace ---- */
typedef struct {
    char *s;
    unsigned char *f;
    size_t len;
} Frag;

static int is_split_char(SBuf *b, size_t i) {
    char c = b->s[i];
    return (c == ' ' || c == '\t' || c == '\n') && (b->f[i] & FL_EXPANDED) &&
           !(b->f[i] & FL_NOGLOB);
}

static int pass_split(SBuf *in, Frag **out) {
    Frag *frs = malloc(sizeof(Frag) * (in->len + 1));
    int nf = 0;
    size_t start = 0;
    int have = 0;
    for (size_t i = 0; i <= in->len; i++) {
        int boundary = (i == in->len) || is_split_char(in, i);
        if (boundary) {
            if (have) {
                size_t len = i - start;
                frs[nf].len = len;
                frs[nf].s = malloc(len + 1);
                memcpy(frs[nf].s, in->s + start, len);
                frs[nf].s[len] = 0;
                frs[nf].f = malloc(len + 1);
                memcpy(frs[nf].f, in->f + start, len);
                frs[nf].f[len] = 0;
                nf++;
            }
            have = 0;
            start = i + 1;
        } else {
            have = 1;
        }
    }
    *out = frs;
    return nf;
}

/* ---- pass 5: glob expansion ---- */
static char **pass_glob(Frag *frs, int nf, int *out_n) {
    int cap = nf + 8, cnt = 0;
    char **res = malloc((size_t)cap * sizeof(char *));

#define ADD(str)                                        \
    do {                                                \
        if (cnt + 1 > cap) {                            \
            cap *= 2;                                   \
            res = realloc(res, (size_t)cap * sizeof(char *)); \
        }                                               \
        res[cnt++] = (str);                             \
    } while (0)

    for (int i = 0; i < nf; i++) {
        int wild = 0;
        for (size_t k = 0; k < frs[i].len; k++)
            if (!(frs[i].f[k] & FL_NOGLOB) && strchr("*?[", frs[i].s[k])) {
                wild = 1;
                break;
            }
        if (!wild) {
            ADD(strdup(frs[i].s));
            continue;
        }
        glob_t g;
        int rc = glob(frs[i].s, 0, NULL, &g);
        if (rc == 0) {
            for (size_t m = 0; m < g.gl_pathc; m++) ADD(strdup(g.gl_pathv[m]));
            globfree(&g);
        } else {
            ADD(strdup(frs[i].s)); /* no match: keep the pattern literally (bash default) */
        }
    }
#undef ADD

    *out_n = cnt;
    return res;
}

char **expand_word(ShellState *st, const char *w, const unsigned char *flags,
                   size_t wlen, int *out_n) {
    /* A zero-length word can only come from "" or '' — preserved as one
     * empty argv entry, never dropped. */
    if (wlen == 0) {
        char **r = malloc(sizeof(char *));
        r[0] = strdup("");
        *out_n = 1;
        return r;
    }

    SBuf a, b;
    sb_init(&a);
    sb_reserve(&a, wlen);
    for (size_t i = 0; i < wlen; i++) sb_push(&a, w[i], flags ? flags[i] : 0);

    sb_init(&b);
    pass_tilde(&a, &b);
    sb_free(&a);

    sb_init(&a);
    pass_vars(st, &b, &a);
    sb_free(&b);

    sb_init(&b);
    pass_cmdsub(st, &a, &b);
    sb_free(&a);

    Frag *frs;
    int nf = pass_split(&b, &frs);
    sb_free(&b);

    char **res = pass_glob(frs, nf, out_n);

    for (int i = 0; i < nf; i++) {
        free(frs[i].s);
        free(frs[i].f);
    }
    free(frs);
    return res;
}

void free_str_array(char **a, int n) {
    if (!a) return;
    for (int i = 0; i < n; i++) free(a[i]);
    free(a);
}
