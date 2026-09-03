/* Phase 6 tests — the daemon integration hook.
 *
 * A stub listener stands in for nhztermd: it binds a unix socket, spawns
 * the real nhzsh binary with NHZSH_CONTROL_SOCKET/NHZSH_SESSION_ID set,
 * and asserts the two foreground_pid frames arrive in the right order
 * around a real command's actual lifetime (concept doc §9):
 *
 *   frame 1: {"type":"foreground_pid","session_id":"...","pid":<alive>}
 *   frame 2: {"type":"foreground_pid","session_id":"...","pid":null}
 */
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

static int fails = 0;
#define CHECK(cond, msg)                                       \
    do {                                                       \
        if (!(cond)) { printf("  FAIL: %s\n", msg); fails++; } \
        else printf("  ok: %s\n", msg);                        \
    } while (0)

static int read_full(int fd, void *buf, size_t n) {
    size_t got = 0;
    while (got < n) {
        ssize_t r = read(fd, (char *)buf + got, n - got);
        if (r <= 0) return -1;
        got += (size_t)r;
    }
    return 0;
}

static char *read_frame(int fd) {
    unsigned char hdr[4];
    if (read_full(fd, hdr, 4) < 0) return NULL;
    int len = (hdr[0] << 24) | (hdr[1] << 16) | (hdr[2] << 8) | hdr[3];
    if (len <= 0 || len > 65536) return NULL;
    char *body = malloc((size_t)len + 1);
    if (read_full(fd, body, (size_t)len) < 0) { free(body); return NULL; }
    body[len] = 0;
    return body;
}

int main(void) {
    char path[128];
    snprintf(path, sizeof path, "/tmp/nhzsh-dl-%d.sock", (int)getpid());
    unlink(path);

    int lfd = socket(AF_UNIX, SOCK_STREAM, 0);
    struct sockaddr_un a;
    memset(&a, 0, sizeof a);
    a.sun_family = AF_UNIX;
    strncpy(a.sun_path, path, sizeof(a.sun_path) - 1);
    if (bind(lfd, (struct sockaddr *)&a, sizeof a) < 0) {
        perror("bind");
        return 1;
    }
    if (listen(lfd, 1) < 0) {
        perror("listen");
        return 1;
    }

    pid_t child = fork();
    if (child == 0) {
        setenv("NHZSH_CONTROL_SOCKET", path, 1);
        setenv("NHZSH_SESSION_ID", "sess-test", 1);
        execl("./nhzsh", "nhzsh", "-c", "sleep 0.4", (char *)NULL);
        perror("execl ./nhzsh");
        _exit(1);
    }

    alarm(10); /* never hang the test suite */
    int cfd = accept(lfd, NULL, NULL);
    CHECK(cfd >= 0, "nhzsh connected to the stub daemon socket");
    if (cfd < 0) return 1;

    printf("[daemon_link] frame 1: pid reported on exec\n");
    char *f1 = read_frame(cfd);
    CHECK(f1 != NULL, "frame 1 received");
    if (f1) {
        printf("    %s\n", f1);
        CHECK(strstr(f1, "\"type\":\"foreground_pid\"") != NULL, "type is foreground_pid");
        CHECK(strstr(f1, "\"session_id\":\"sess-test\"") != NULL, "session_id forwarded");
        int pid = -1;
        const char *pp = strstr(f1, "\"pid\":");
        if (pp) sscanf(pp, "\"pid\":%d", &pid);
        CHECK(pid > 0, "pid is a real positive pid");
        if (pid > 0) {
            CHECK(kill(pid, 0) == 0, "reported pid is ALIVE while the command runs");
        }
        free(f1);
    }

    printf("[daemon_link] frame 2: pid null after completion\n");
    char *f2 = read_frame(cfd);
    CHECK(f2 != NULL, "frame 2 received");
    if (f2) {
        printf("    %s\n", f2);
        CHECK(strstr(f2, "\"pid\":null") != NULL, "second frame reports pid: null");
        free(f2);
    }

    int s;
    waitpid(child, &s, 0);
    close(cfd);
    close(lfd);
    unlink(path);

    printf(fails ? "DAEMON LINK TESTS: FAILED (%d)\n" : "DAEMON LINK TESTS: PASSED\n", fails);
    return fails ? 1 : 0;
}
