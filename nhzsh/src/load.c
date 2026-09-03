#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "executor.h"
#include "load.h"
#include "state.h"

static int lib_already(ShellState *st, const char *name) {
    for (Lib *l = st->libs; l; l = l->next)
        if (!strcmp(l->name, name)) return 1;
    return 0;
}

static char *path_join3(const char *dir, const char *name, const char *ext) {
    size_t n = strlen(dir) + strlen(name) + strlen(ext) + 3;
    char *r = malloc(n);
    snprintf(r, n, "%s/%s%s", dir, name, ext);
    return r;
}

/* Search the three library locations in priority order; returns a
 * malloc'd path or NULL. */
static char *find_lib(ShellState *st, const char *name) {
    char *cands[3];

    if (st->script_dir) {
        size_t n = strlen(st->script_dir) + 8;
        char *base = malloc(n);
        snprintf(base, n, "%s/lib", st->script_dir);
        cands[0] = path_join3(base, name, ".sh");
        free(base);
    } else {
        char *cwd = getcwd(NULL, 0);
        size_t n = (cwd ? strlen(cwd) : 1) + 8;
        char *base = malloc(n);
        snprintf(base, n, "%s/lib", cwd ? cwd : ".");
        free(cwd);
        cands[0] = path_join3(base, name, ".sh");
        free(base);
    }

    const char *user = getenv("NHZSH_USER_LIB");
    char userbuf[4096];
    if (!user) {
        const char *h = getenv("HOME");
        snprintf(userbuf, sizeof userbuf, "%s/.nhzsh/lib", h ? h : "");
        user = userbuf;
    }
    cands[1] = path_join3(user, name, ".sh");

    const char *sys = getenv("NHZSH_SYS_LIB");
    if (!sys) sys = "/usr/local/share/nhzsh/lib";
    cands[2] = path_join3(sys, name, ".sh");

    char *found = NULL;
    for (int i = 0; i < 3; i++) {
        if (!found && access(cands[i], R_OK) == 0) found = strdup(cands[i]);
        free(cands[i]);
    }
    return found;
}

int builtin_load(ShellState *st, int argc, char **argv) {
    if (argc < 2 || (argc != 2 && !(argc == 4 && !strcmp(argv[2], "as")))) {
        fprintf(stderr, "nhzsh: load: usage: load <name> [as <alias>]\n");
        return 1;
    }
    const char *name = argv[1];
    const char *alias = (argc == 4) ? argv[3] : NULL;

    /* Load tracking: a second load of the same library is a no-op,
     * not a silent re-source. */
    if (lib_already(st, name)) {
        printf("nhzsh: library '%s' is already loaded\n", name);
        return 0;
    }

    char *path = find_lib(st, name);
    if (!path) {
        fprintf(stderr, "nhzsh: library '%s' not found in search path\n", name);
        return 1;
    }

    FILE *f = fopen(path, "r");
    if (!f) {
        fprintf(stderr, "nhzsh: load: %s: %s\n", path, strerror(errno));
        free(path);
        return 1;
    }
    int rc = exec_stream(st, f);
    fclose(f);

    Lib *l = calloc(1, sizeof *l);
    l->name = strdup(name);
    l->path = path; /* ownership transferred */
    l->alias = alias ? strdup(alias) : NULL;
    l->next = st->libs;
    st->libs = l;

    if (alias) {
        /* Wrapper: <alias> forwards to the library's conventional
         * entry point <name>_main, so callers never reference the
         * full internal name (concept doc §5.4). */
        size_t n = strlen(name) + sizeof "_main";
        char *entry = malloc(n);
        snprintf(entry, n, "%s_main", name);
        alias_set(st, alias, entry);
        free(entry);
    }
    return rc;
}

int builtin_unload(ShellState *st, int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "nhzsh: unload: usage: unload <name>\n");
        return 1;
    }
    Lib **pp = &st->libs;
    while (*pp) {
        if (!strcmp((*pp)->name, argv[1])) {
            Lib *gone = *pp;
            *pp = gone->next;
            if (gone->alias) alias_del(st, gone->alias);
            free(gone->name);
            free(gone->path);
            free(gone->alias);
            free(gone);
            return 0;
        }
        pp = &(*pp)->next;
    }
    fprintf(stderr, "nhzsh: library '%s' is not loaded\n", argv[1]);
    return 1;
}

int builtin_list_libs(ShellState *st) {
    if (!st->libs) {
        printf("no libraries loaded\n");
        return 0;
    }
    for (Lib *l = st->libs; l; l = l->next)
        printf("%s\talias=%s\t%s\n", l->name, l->alias ? l->alias : "-", l->path);
    return 0;
}
