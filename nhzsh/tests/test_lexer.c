/* Phase 1 tests — the lexer. */
#include <stdio.h>
#include <string.h>

#include "lexer.h"

static int fails = 0;
#define CHECK(cond, msg)                                        \
    do {                                                        \
        if (!(cond)) { printf("  FAIL: %s\n", msg); fails++; }  \
        else printf("  ok: %s\n", msg);                         \
    } while (0)

int main(void) {
    printf("[lexer] basic words\n");
    {
        int n;
        Token *t = lexer_tokenize("echo hello world", &n);
        CHECK(n == 4, "3 words + EOF");
        CHECK(t[0].type == TOK_WORD && !strcmp(t[0].value, "echo"), "word 'echo'");
        CHECK(t[1].type == TOK_WORD && !strcmp(t[1].value, "hello"), "word 'hello'");
        CHECK(t[2].type == TOK_WORD && !strcmp(t[2].value, "world"), "word 'world'");
        CHECK(t[3].type == TOK_EOF, "EOF terminator");
        lexer_free(t, n);
    }

    printf("[lexer] operators\n");
    {
        int n;
        Token *t = lexer_tokenize("ls -la | grep x && wc || tail ; rm &", &n);
        TokenType want[] = { TOK_WORD, TOK_WORD, TOK_PIPE, TOK_WORD, TOK_WORD,
                             TOK_DAND, TOK_WORD, TOK_DPIPE, TOK_WORD, TOK_SEMI,
                             TOK_WORD, TOK_AMP, TOK_EOF };
        CHECK((size_t)n == sizeof(want) / sizeof(want[0]), "13 tokens");
        int ok = 1;
        for (int i = 0; i < n && (size_t)i < sizeof(want) / sizeof(want[0]); i++)
            if (t[i].type != want[i]) ok = 0;
        CHECK(ok, "all operator types in order (| && || ; &)");
        lexer_free(t, n);
    }

    printf("[lexer] redirections\n");
    {
        int n;
        Token *t = lexer_tokenize("a > b >> c < d", &n);
        TokenType want[] = { TOK_WORD, TOK_GT, TOK_WORD, TOK_DGT, TOK_WORD,
                             TOK_LT, TOK_WORD, TOK_EOF };
        CHECK((size_t)n == sizeof(want) / sizeof(want[0]), "8 tokens");
        int ok = 1;
        for (int i = 0; i < n && (size_t)i < sizeof(want) / sizeof(want[0]); i++)
            if (t[i].type != want[i]) ok = 0;
        CHECK(ok, "> >> < token types");
        lexer_free(t, n);
    }

    printf("[lexer] quoting\n");
    {
        int n;
        Token *t = lexer_tokenize("echo \"hi $X\" 'lit $X'", &n);
        CHECK(n == 4, "3 tokens + EOF");
        CHECK(!strcmp(t[1].value, "hi $X"), "double quotes stripped");
        int dq_ok = 1;
        for (const char *p = t[1].value; *p; p++) {
            unsigned char f = t[1].flags[p - t[1].value];
            if (!(f & FL_NOGLOB) || (f & FL_NOEXPAND)) dq_ok = 0;
        }
        CHECK(dq_ok, "double-quoted chars: NOGLOB set, expandable");
        CHECK(!strcmp(t[2].value, "lit $X"), "single quotes stripped");
        int sq_ok = 1;
        for (const char *p = t[2].value; *p; p++) {
            unsigned char f = t[2].flags[p - t[2].value];
            if ((f & (FL_NOEXPAND | FL_NOGLOB)) != (FL_NOEXPAND | FL_NOGLOB)) sq_ok = 0;
        }
        CHECK(sq_ok, "single-quoted chars: fully literal");
        lexer_free(t, n);
    }

    printf("[lexer] comments\n");
    {
        int n;
        Token *t = lexer_tokenize("echo hi # this is gone", &n);
        CHECK(n == 3, "comment stops the line (2 words + EOF)");
        lexer_free(t, n);
        t = lexer_tokenize("echo a#b", &n);
        CHECK(n == 3 && !strcmp(t[1].value, "a#b"), "# inside a word is literal");
        lexer_free(t, n);
    }

    printf("[lexer] backslash escapes\n");
    {
        int n;
        Token *t = lexer_tokenize("echo a\\ b", &n);
        CHECK(n == 3, "escaped space does not split");
        CHECK(!strcmp(t[1].value, "a b"), "value is 'a b'");
        CHECK((t[1].flags[1] & (FL_NOEXPAND | FL_NOGLOB)) == (FL_NOEXPAND | FL_NOGLOB),
              "escaped char is literal");
        lexer_free(t, n);
    }

    printf("[lexer] errors & edge cases\n");
    {
        int n;
        Token *t = lexer_tokenize("echo 'oops", &n);
        int saw_err = 0;
        for (int i = 0; i < n; i++)
            if (t[i].type == TOK_ERR) saw_err = 1;
        CHECK(saw_err, "unterminated quote -> TOK_ERR");
        lexer_free(t, n);

        t = lexer_tokenize("echo \"\"", &n);
        CHECK(n == 3 && !strcmp(t[1].value, ""), "empty quoted word survives as \"\"");
        lexer_free(t, n);

        t = lexer_tokenize("   ", &n);
        CHECK(n == 1 && t[0].type == TOK_EOF, "whitespace-only line -> just EOF");
        lexer_free(t, n);
    }

    printf(fails ? "LEXER TESTS: FAILED (%d)\n" : "LEXER TESTS: PASSED\n", fails);
    return fails ? 1 : 0;
}
