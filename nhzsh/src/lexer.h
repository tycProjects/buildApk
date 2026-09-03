#ifndef NHZSH_LEXER_H
#define NHZSH_LEXER_H

/*
 * nhzsh lexer — Part 2, Phase 1 of the build plan.
 *
 * Per-character flags carried on word tokens. These let the expander
 * (Phase 3) know which characters are eligible for expansion, globbing,
 * and word splitting — e.g. text that came from inside single quotes must
 * never be expanded, and quoted text must never be globbed or split.
 */
#define FL_NOEXPAND 1u /* literal char: no variable/command-sub/tilde expansion */
#define FL_NOGLOB   2u /* char cannot glob, and does not trigger word splitting */
#define FL_EXPANDED 4u /* char was produced by an expansion pass (split candidate) */

typedef enum {
    TOK_WORD = 0,
    TOK_PIPE,  /* |  */
    TOK_DPIPE, /* || */
    TOK_GT,    /* >  */
    TOK_DGT,   /* >> */
    TOK_LT,    /* <  */
    TOK_DAND,  /* && */
    TOK_SEMI,  /* ;  */
    TOK_AMP,   /* &  */
    TOK_EOF,
    TOK_ERR
} TokenType;

typedef struct {
    TokenType type;
    char *value;          /* TOK_WORD: quote-stripped text. TOK_ERR: message. else NULL */
    unsigned char *flags; /* TOK_WORD: one flag byte per char of value. else NULL */
} Token;

/*
 * Tokenize one input line. The returned array always ends with TOK_EOF.
 * On a lexical error (e.g. unterminated quote) a TOK_ERR token is appended
 * and scanning stops there.
 */
Token *lexer_tokenize(const char *line, int *count_out);

void lexer_free(Token *toks, int count);

#endif /* NHZSH_LEXER_H */
