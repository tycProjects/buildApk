#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <unistd.h>

#include "daemon_link.h"

static int write_all(int fd, const void *buf, size_t n) {
    const char *p = buf;
    while (n) {
        ssize_t w = write(fd, p, n);
        if (w <= 0) {
            if (w < 0 && errno == EINTR) continue;
            return -1;
        }
        p += w;
        n -= (size_t)w;
    }
    return 0;
}

/* Lazily open the control connection. Returns fd >= 0 on success, -1 if
 * there is no daemon configured or it is unreachable (fire-and-forget:
 * callers silently continue). */
static int ensure_conn(ShellState *st) {
    if (st->control_fd >= 0) return st->control_fd;
    if (!st->control_socket || st->capture) return -1;

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    /* Never let a stuck daemon block the shell for long. */
    struct timeval tv = { 0, 500 * 1000 };
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof tv);

    struct sockaddr_un a;
    memset(&a, 0, sizeof a);
    a.sun_family = AF_UNIX;
    const char *path = st->control_socket;
    if (path[0] == '@') {
        /* Linux abstract namespace: leading NUL byte, name after it.
         * Android LocalSocket abstract names map onto exactly this. */
        a.sun_path[0] = 0;
        strncpy(a.sun_path + 1, path + 1, sizeof(a.sun_path) - 2);
    } else {
        strncpy(a.sun_path, path, sizeof(a.sun_path) - 1);
    }

    socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + strlen(a.sun_path) + 1);
    if (path[0] == '@')
        len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(path + 1));

    if (connect(fd, (struct sockaddr *)&a, len) < 0) {
        close(fd);
        return -1;
    }
    st->control_fd = fd;
    return fd;
}

void daemon_report_foreground(ShellState *st, int pid) {
    if (st->capture) return;              /* subshells never report */
    if (!st->control_socket) return;      /* no daemon configured */

    /* Minimal JSON string escaping for the session id. */
    char sid[128];
    size_t k = 0;
    if (st->session_id) {
        for (const char *p = st->session_id; *p && k < sizeof(sid) - 1; p++) {
            if (*p == '"' || *p == '\\') {
                if (k + 2 >= sizeof(sid) - 1) break;
                sid[k++] = '\\';
            }
            if ((unsigned char)*p >= 0x20) sid[k++] = *p;
        }
    }
    sid[k] = 0;

    char body[512];
    int n;
    if (pid > 0)
        n = snprintf(body, sizeof body,
                     "{\"type\":\"foreground_pid\",\"session_id\":\"%s\",\"pid\":%d}",
                     sid, pid);
    else
        n = snprintf(body, sizeof body,
                     "{\"type\":\"foreground_pid\",\"session_id\":\"%s\",\"pid\":null}",
                     sid);
    if (n < 0 || (size_t)n >= sizeof body) return;

    int fd = ensure_conn(st);
    if (fd < 0) return;

    unsigned char hdr[4] = {
        (unsigned char)((n >> 24) & 0xff), (unsigned char)((n >> 16) & 0xff),
        (unsigned char)((n >> 8) & 0xff), (unsigned char)(n & 0xff)
    };
    if (write_all(fd, hdr, 4) < 0 || write_all(fd, body, (size_t)n) < 0) {
        /* Broken pipe: drop the connection; next report retries. */
        close(fd);
        st->control_fd = -1;
    }
}

void daemon_close(ShellState *st) {
    if (st->control_fd >= 0) {
        close(st->control_fd);
        st->control_fd = -1;
    }
}
