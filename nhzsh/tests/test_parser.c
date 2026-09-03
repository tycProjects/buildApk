/* Phase 2 tests — the parser / AST shape. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lexer.h"
#include "parser.h"

static int fails = 0;
#define CHECK(cond, msg)                                       \
    do {                                                       \
        if (!(cond)) { printf("  FAIL: %s\n", msg); fails++; } \
        else printf("  ok: %s\n", msg);                        \
    } while (0)

static CmdList *parse(const char *line, char **err) {
    int n;
    Token *t = lexer_tokenize(line, &n);
    CmdList *l = parser_parse(t, n, err);
    lexer_free(t, n);
    return l;
}

int main(void) {
    printf("[parser] full mixed line\n");
    {
        char *err = NULL;
        CmdList *l = parse("a | b > f && c ; d &", &err);
        CHECK(l && !err, "parses cleanly");
        CHECK(l && l->npipes == 3, "3 pipelines");
        CHECK(l && l->nops == 2 && l->ops[0] == OP_AND && l->ops[1] == OP_SEQ,
              "operators: && then ;");
        CHECK(l && l->pipes[0].ncmds == 2, "first pipeline has 2 stages");
        CHECK(l && !strcmp(l->pipes[0].cmds[1].argv[0], "b"), "stage 2 is 'b'");
        CHECK(l && l->pipes[0].cmds[1].redirs &&
                  l->pipes[0].cmds[1].redirs->type == REDIR_OUT &&
                  !strcmp(l->pipes[0].cmds[1].redirs->target, "f"),
              "redirect > f attached to stage 2");
        CHECK(l && l->pipes[2].background, "trailing & marks background");
        CHECK(l && !l->pipes[0].background && !l->pipes[1].background,
              "other pipelines not background");
        cmdlist_free(l);
    }

    printf("[parser] conditional chain\n");
    {
        char *err = NULL;
        CmdList *l = parse("make && make install || echo failed", &err);
        CHECK(l && l->npipes == 3 && l->ops[0] == OP_AND && l->ops[1] == OP_OR,
              "&& / || chain shape");
        cmdlist_free(l);
    }

    printf("[parser] redirects of every kind\n");
    {
        char *err = NULL;
        CmdList *l = parse("cmd < in > out >> log", &err);
        CHECK(l && l->pipes[0].cmds[0].argc == 1, "one word");
        int in = 0, out = 0, app = 0;
        for (Redirect *r = l ? l->pipes[0].cmds[0].redirs : NULL; r; r = r->next) {
            if (r->type == REDIR_IN && !strcmp(r->target, "in")) in = 1;
            if (r->type == REDIR_OUT && !strcmp(r->target, "out")) out = 1;
            if (r->type == REDIR_APPEND && !strcmp(r->target, "log")) app = 1;
        }
        CHECK(in && out && app, "<, >, >> all captured");
        cmdlist_free(l);
    }

    printf("[parser] redirect-only command\n");
    {
        char *err = NULL;
        CmdList *l = parse("> touchfile", &err);
        CHECK(l && l->pipes[0].cmds[0].argc == 0 && l->pipes[0].cmds[0].redirs,
              "'> file' with no words is valid");
        cmdlist_free(l);
    }

    printf("[parser] empty & comment-only lines\n");
    {
        char *err = NULL;
        CHECK(parse("", &err) == NULL && !err, "empty line -> NULL, no error");
        CHECK(parse("# just a comment", &err) == NULL && !err,
              "comment-only line -> NULL, no error");
        CHECK(parse("echo ok;", &err) != NULL, "trailing ';' is fine");
    }

    printf("[parser] error cases\n");
    {
        char *err = NULL;
        CHECK(parse("a |", &err) == NULL && err, "dangling pipe is an error");
        free(err); err = NULL;
        CHECK(parse("&& a", &err) == NULL && err, "leading && is an error");
        free(err); err = NULL;
        CHECK(parse("a &&", &err) == NULL && err, "trailing && is an error");
        free(err); err = NULL;
        CHECK(parse("a > ", &err) == NULL && err, "redirect without target is an error");
        free(err); err = NULL;
        CHECK(parse("echo 'unterminated", &err) == NULL && err,
              "lexer error surfaces as parse error");
        free(err);
    }

    printf(fails ? "PARSER TESTS: FAILED (%d)\n" : "PARSER TESTS: PASSED\n", fails);
    return fails ? 1 : 0;
}
