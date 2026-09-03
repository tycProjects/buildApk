#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "builtins.h"
#include "load.h"

static const char *BUILTINS[] = {
    "cd", "pwd", "exit", "export", "unset", "alias", "unalias",
    "history", "true", "false", "load", "unload", "list-libs", NULL
};

int is_builtin(const char *name) {
    for (int i = 0; BUILTINS[i]; i++)
        if (!strcmp(name, BUILTINS[i])) return 1;
    return 0;
}

static int b_cd(ShellState *st, int argc, char **argv) {
    (void)st;
    const char *target;
    char *old = getcwd(NULL, 0);

    if (argc == 1) {
        target = getenv("HOME");
        if (!target) {
            fprintf(stderr, "nhzsh: cd: HOME not set\n");
            free(old);
            return 1;
        }
    } else if (argc == 2 && !strcmp(argv[1], "-")) {
        target = getenv("OLDPWD");
        if (!target) {
            fprintf(stderr, "nhzsh: cd: OLDPWD not set\n");
            free(old);
            return 1;
        }
        printf("%s\n", target);
    } else if (argc == 2) {
        target = argv[1];
    } else {
        fprintf(stderr, "nhzsh: cd: too many arguments\n");
        free(old);
        return 1;
    }

    if (chdir(target) < 0) {
        fprintf(stderr, "nhzsh: cd: %s: %s\n", target, strerror(errno));
        free(old);
        return 1;
    }
    if (old) {
        setenv("OLDPWD", old, 1);
        free(old);
    }
    char *now = getcwd(NULL, 0);
    if (now) {
        setenv("PWD", now, 1);
        free(now);
    }
    return 0;
}

static int b_pwd(void) {
    char *d = getcwd(NULL, 0);
    if (!d) {
        fprintf(stderr, "nhzsh: pwd: %s\n", strerror(errno));
        return 1;
    }
    printf("%s\n", d);
    free(d);
    return 0;
}

static int b_export(int argc, char **argv) {
    for (int i = 1; i < argc; i++) {
        char *eq = strchr(argv[i], '=');
        if (eq) {
            *eq = 0;
            setenv(argv[i], eq + 1, 1);
        }
        /* bare `export NAME`: environment is shared with children via
         * setenv/getenv already, so there is nothing further to mark. */
    }
    return 0;
}

static int b_unset(int argc, char **argv) {
    for (int i = 1; i < argc; i++) unsetenv(argv[i]);
    return 0;
}

/* Strip one layer of matching surrounding quotes. */
static char *unquote(const char *v) {
    size_t len = strlen(v);
    if (len >= 2 && ((v[0] == '\'' && v[len - 1] == '\'') ||
                     (v[0] == '"' && v[len - 1] == '"'))) {
        char *r = malloc(len - 1);
        memcpy(r, v + 1, len - 2);
        r[len - 2] = 0;
        return r;
    }
    return strdup(v);
}

static int b_alias(ShellState *st, int argc, char **argv) {
    if (argc == 1) {
        for (Alias *a = st->aliases; a; a = a->next)
            printf("alias %s='%s'\n", a->name, a->value);
        return 0;
    }
    for (int i = 1; i < argc; i++) {
        char *eq = strchr(argv[i], '=');
        if (eq) {
            *eq = 0;
            char *val = unquote(eq + 1);
            alias_set(st, argv[i], val);
            free(val);
        } else {
            Alias *a = alias_get(st, argv[i]);
            if (!a) {
                fprintf(stderr, "nhzsh: alias: %s: not found\n", argv[i]);
                return 1;
            }
            printf("alias %s='%s'\n", a->name, a->value);
        }
    }
    return 0;
}

static int b_unalias(ShellState *st, int argc, char **argv) {
    int rc = 0;
    for (int i = 1; i < argc; i++) {
        if (!alias_get(st, argv[i])) {
            fprintf(stderr, "nhzsh: unalias: %s: not found\n", argv[i]);
            rc = 1;
            continue;
        }
        alias_del(st, argv[i]);
    }
    return rc;
}

static int b_history(ShellState *st) {
    for (int i = 0; i < st->nhistory; i++)
        printf("%5d  %s\n", i + 1, st->history[i]);
    return 0;
}

int builtin_exec(ShellState *st, int argc, char **argv) {
    const char *name = argv[0];

    if (!strcmp(name, "cd")) return b_cd(st, argc, argv);
    if (!strcmp(name, "pwd")) return b_pwd();
    if (!strcmp(name, "exit")) {
        st->running = 0;
        return (argc > 1) ? atoi(argv[1]) : st->last_status;
    }
    if (!strcmp(name, "export")) return b_export(argc, argv);
    if (!strcmp(name, "unset")) return b_unset(argc, argv);
    if (!strcmp(name, "alias")) return b_alias(st, argc, argv);
    if (!strcmp(name, "unalias")) return b_unalias(st, argc, argv);
    if (!strcmp(name, "history")) return b_history(st);
    if (!strcmp(name, "true")) return 0;
    if (!strcmp(name, "false")) return 1;
    if (!strcmp(name, "load")) return builtin_load(st, argc, argv);
    if (!strcmp(name, "unload")) return builtin_unload(st, argc, argv);
    if (!strcmp(name, "list-libs")) return builtin_list_libs(st);

    fprintf(stderr, "nhzsh: %s: builtin not implemented\n", name);
    return 1;
}
