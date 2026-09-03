/* Phase 4 tests — the executor (builtins, forks, pipes, redirects, $?). */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>

#include "executor.h"
#include "expander.h"
#include "state.h"

static int fails = 0;
#define CHECK(cond, msg)                                       \
    do {                                                       \
        if (!(cond)) { printf("  FAIL: %s\n", msg); fails++; } \
        else printf("  ok: %s\n", msg);                        \
    } while (0)

static ShellState st;

static char *cap(const char *line) {
    exec_line(&st, line); /* set up state (cd, exports) in-process */
    return exec_capture(&st, "true"); /* placeholder, unused */
}

int main(void) {
    state_init(&st, 0);
    (void)cap;

    printf("[executor] simple command + capture\n");
    {
        char *o = exec_capture(&st, "echo hi");
        CHECK(o && !strcmp(o, "hi"), "echo hi -> 'hi'");
        free(o);
    }

    printf("[executor] pipelines\n");
    {
        char *o = exec_capture(&st, "echo hello | cat");
        CHECK(o && !strcmp(o, "hello"), "echo | cat passes data through a real pipe");
        free(o);

        o = exec_capture(&st, "echo abc | tr a-z A-Z | tr B X");
        CHECK(o && !strcmp(o, "AXC"), "3-stage pipeline");
        free(o);
    }

    printf("[executor] conditionals\n");
    {
        char *o = exec_capture(&st, "true && echo yes");
        CHECK(o && !strcmp(o, "yes"), "true && runs next");
        free(o);
        o = exec_capture(&st, "false && echo hidden");
        CHECK(o && !strcmp(o, ""), "false && short-circuits");
        free(o);
        o = exec_capture(&st, "false || echo or");
        CHECK(o && !strcmp(o, "or"), "false || runs next");
        free(o);
        o = exec_capture(&st, "true || echo hidden");
        CHECK(o && !strcmp(o, ""), "true || short-circuits");
        free(o);
        o = exec_capture(&st, "echo a ; echo b");
        CHECK(o && !strcmp(o, "a\nb"), "; runs both");
        free(o);
    }

    printf("[executor] builtins mutate shell state in-process\n");
    {
        exec_line(&st, "cd /tmp");
        char *o = exec_capture(&st, "pwd");
        CHECK(o && !strcmp(o, "/tmp"), "cd /tmp persisted into the shell itself");
        free(o);

        exec_line(&st, "export FOO_EXEC=works");
        o = exec_capture(&st, "sh -c 'echo $FOO_EXEC'");
        CHECK(o && !strcmp(o, "works"), "export is visible to child processes");
        free(o);

        exec_line(&st, "unset FOO_EXEC");
        o = exec_capture(&st, "sh -c 'echo x$FOO_EXEC'");
        CHECK(o && !strcmp(o, "x"), "unset removes it");
        free(o);
    }

    printf("[executor] redirection\n");
    {
        const char *path = "/tmp/nhzsh-exec-test.txt";
        unlink(path);
        exec_line(&st, "echo one > /tmp/nhzsh-exec-test.txt");
        exec_line(&st, "echo two >> /tmp/nhzsh-exec-test.txt");
        FILE *f = fopen(path, "r");
        char buf[128] = { 0 };
        if (f) {
            size_t got = fread(buf, 1, sizeof buf - 1, f);
            buf[got] = 0;
            fclose(f);
        }
        CHECK(!strcmp(buf, "one\ntwo\n"), "> creates, >> appends");
        unlink(path);

        char *o = exec_capture(&st, "echo x > /tmp/nhzsh-exec-test.txt");
        CHECK(o && !strcmp(o, ""), "redirected output not captured on stdout");
        free(o);
        unlink(path);
    }

    printf("[executor] exit codes / $?\n");
    {
        exec_line(&st, "sh -c 'exit 3'");
        CHECK(st.last_status == 3, "exit code 3 propagates");
        char *o = exec_capture(&st, "echo $?");
        CHECK(o && !strcmp(o, "3"), "$? expands to 3");
        free(o);

        exec_line(&st, "definitely_not_a_command_xyz");
        CHECK(st.last_status == 127, "missing command -> 127");

        exec_line(&st, "false | true");
        CHECK(st.last_status == 0, "pipeline status is the LAST stage's");
        exec_line(&st, "true | false");
        CHECK(st.last_status == 1, "true | false -> 1");
    }

    printf("[executor] aliases\n");
    {
        exec_line(&st, "alias greet='echo hello-alias'");
        char *o = exec_capture(&st, "greet");
        CHECK(o && !strcmp(o, "hello-alias"), "alias expands and runs");
        free(o);
    }

    printf("[executor] background & returns immediately\n");
    {
        struct timeval a, b;
        gettimeofday(&a, NULL);
        exec_line(&st, "sleep 0.4 &");
        gettimeofday(&b, NULL);
        long ms = (b.tv_sec - a.tv_sec) * 1000 + (b.tv_usec - a.tv_usec) / 1000;
        CHECK(ms < 250, "'sleep 0.4 &' returns without waiting");
        CHECK(st.last_status == 0, "backgrounded pipeline sets $? to 0");
    }

    state_destroy(&st);
    printf(fails ? "EXECUTOR TESTS: FAILED (%d)\n" : "EXECUTOR TESTS: PASSED\n", fails);
    return fails ? 1 : 0;
}
