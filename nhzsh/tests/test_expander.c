/* Phase 3 tests — the expander. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "expander.h"
#include "lexer.h"
#include "state.h"

static int fails = 0;
#define CHECK(cond, msg)                                       \
    do {                                                       \
        if (!(cond)) { printf("  FAIL: %s\n", msg); fails++; } \
        else printf("  ok: %s\n", msg);                        \
    } while (0)

static ShellState st;

/* Expand using the real lexer's flags, exactly as the executor does. */
static char **ex(const char *word, int *n_out) {
    int ntok;
    Token *t = lexer_tokenize(word, &ntok);
    char **r = NULL;
    *n_out = 0;
    for (int i = 0; i < ntok; i++) {
        if (t[i].type == TOK_WORD) {
            r = expand_word(&st, t[i].value, t[i].flags, strlen(t[i].value), n_out);
            break;
        }
    }
    lexer_free(t, ntok);
    return r;
}

int main(void) {
    state_init(&st, 0);
    setenv("FOO", "bar", 1);
    setenv("SP", "a b", 1);
    setenv("HOME", "/tmp/nhzsh-fakehome", 1);

    printf("[expander] variables\n");
    {
        int n;
        char **r = ex("$FOO", &n);
        CHECK(n == 1 && !strcmp(r[0], "bar"), "$FOO -> bar");
        free_str_array(r, n);

        r = ex("${FOO}x", &n);
        CHECK(n == 1 && !strcmp(r[0], "barx"), "${FOO}x -> barx");
        free_str_array(r, n);

        r = ex("$NOPE_NOT_SET", &n);
        CHECK(n == 0, "unset variable expands to zero words");
        free_str_array(r, n);

        st.last_status = 42;
        r = ex("$?", &n);
        CHECK(n == 1 && !strcmp(r[0], "42"), "$? -> last exit status");
        free_str_array(r, n);
        st.last_status = 0;

        r = ex("pre${FOO}post", &n);
        CHECK(n == 1 && !strcmp(r[0], "prebarpost"), "embedded expansion");
        free_str_array(r, n);
    }

    printf("[expander] command substitution\n");
    {
        int n;
        char **r = ex("$(echo sub)", &n);
        CHECK(n == 1 && !strcmp(r[0], "sub"), "$(echo sub) -> sub");
        free_str_array(r, n);

        r = ex("x-$(echo y)-z", &n);
        CHECK(n == 1 && !strcmp(r[0], "x-y-z"), "embedded command substitution");
        free_str_array(r, n);

        r = ex("$(echo a | tr a b)", &n);
        CHECK(n == 1 && !strcmp(r[0], "b"), "pipeline inside $(...)");
        free_str_array(r, n);
    }

    printf("[expander] word splitting\n");
    {
        int n;
        char **r = ex("$SP", &n);
        CHECK(n == 2 && !strcmp(r[0], "a") && !strcmp(r[1], "b"),
              "unquoted $SP splits into 2 words");
        free_str_array(r, n);

        r = ex("\"$SP\"", &n);
        CHECK(n == 1 && !strcmp(r[0], "a b"), "quoted \"$SP\" stays one word");
        free_str_array(r, n);

        r = ex("\"\"", &n);
        CHECK(n == 1 && !strcmp(r[0], ""), "quoted empty word survives");
        free_str_array(r, n);
    }

    printf("[expander] globbing\n");
    {
        char tmpl[] = "/tmp/nhzsh-glob-XXXXXX";
        char *dir = mkdtemp(tmpl);
        char path[512];
        snprintf(path, sizeof path, "%s/f1.txt", dir);
        FILE *f = fopen(path, "w"); fputs("x", f); fclose(f);
        snprintf(path, sizeof path, "%s/f2.txt", dir);
        f = fopen(path, "w"); fputs("x", f); fclose(f);

        char saved_cwd[512];
        getcwd(saved_cwd, sizeof saved_cwd);
        chdir(dir);

        int n;
        char **r = ex("*.txt", &n);
        CHECK(n == 2, "*.txt matches 2 files");
        int has1 = 0, has2 = 0;
        for (int i = 0; i < n; i++) {
            if (strstr(r[i], "f1.txt")) has1 = 1;
            if (strstr(r[i], "f2.txt")) has2 = 1;
        }
        CHECK(has1 && has2, "glob results contain both files");
        free_str_array(r, n);

        r = ex("'*.txt'", &n);
        CHECK(n == 1 && !strcmp(r[0], "*.txt"), "quoted '*.txt' does NOT glob");
        free_str_array(r, n);

        r = ex("no-match-*.zzz", &n);
        CHECK(n == 1 && !strcmp(r[0], "no-match-*.zzz"),
              "unmatched pattern stays literal (bash default)");
        free_str_array(r, n);

        chdir(saved_cwd);
        snprintf(path, sizeof path, "rm -rf %s", dir);
        if (system(path) != 0) { /* test cleanup */ }
    }

    printf("[expander] tilde\n");
    {
        int n;
        char **r = ex("~/x", &n);
        CHECK(n == 1 && !strcmp(r[0], "/tmp/nhzsh-fakehome/x"), "~/x uses $HOME");
        free_str_array(r, n);
    }

    printf("[expander] combined: all four in one word\n");
    {
        setenv("PRE", "v", 1);
        int n;
        char **r = ex("${PRE}al-$(echo ue)", &n);
        CHECK(n == 1 && !strcmp(r[0], "val-ue"), "var + cmdsub in one word");
        free_str_array(r, n);
    }

    state_destroy(&st);
    printf(fails ? "EXPANDER TESTS: FAILED (%d)\n" : "EXPANDER TESTS: PASSED\n", fails);
    return fails ? 1 : 0;
}
