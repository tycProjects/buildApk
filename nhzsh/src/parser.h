#ifndef NHZSH_PARSER_H
#define NHZSH_PARSER_H

#include "lexer.h"

/*
 * nhzsh parser — Part 2, Phase 2 of the build plan.
 *
 * Grammar (concept doc §5.2, Stage 2):
 *   command_list := pipeline (( '&&' | '||' | ';' ) pipeline)*
 *   pipeline     := command ('|' command)*
 *   command      := word+ redirect*
 *   redirect     := ('>' | '>>' | '<') word
 *
 * A trailing '&' marks a pipeline as backgrounded and acts as a
 * terminator, same as ';'.
 */

typedef enum { REDIR_IN, REDIR_OUT, REDIR_APPEND } RedirType;

typedef struct Redirect {
    RedirType type;
    char *target;          /* filename, unexpanded */
    unsigned char *tflags; /* expansion flags for target (may be NULL) */
    struct Redirect *next;
} Redirect;

typedef struct Command {
    char **argv;
    unsigned char **aflags; /* per-word expansion flags (parallel to argv; NULL entries ok) */
    int argc;
    Redirect *redirs;
} Command;

typedef struct Pipeline {
    Command *cmds;
    int ncmds;
    int background; /* trailing '&' */
} Pipeline;

typedef enum { OP_SEQ, OP_AND, OP_OR } ListOp;

typedef struct CmdList {
    Pipeline *pipes;
    int npipes;
    ListOp *ops; /* ops[i] joins pipes[i] and pipes[i+1]; nops == npipes-1 */
    int nops;
} CmdList;

/*
 * Parse a token array (as produced by lexer_tokenize).
 * Returns NULL for an empty line (nothing to execute, *err_out untouched).
 * On a parse error returns NULL and sets *err_out (caller frees).
 */
CmdList *parser_parse(Token *toks, int ntok, char **err_out);

void cmdlist_free(CmdList *list);

#endif /* NHZSH_PARSER_H */
