/*
 * libptyhelper — nhzterm concept doc §4, probe position #4:
 *
 *   "Compiled native helper (last resort only). A small native library
 *    (.so, built with the NDK) whose only job is forkpty() followed by
 *    exec(...), loaded directly via JNI from the Kotlin service — no
 *    external process required for this path."
 *
 * Small and honest: fork a child onto a fresh PTY, exec the command,
 * hand the master fd back to Kotlin. Plus the minimal ops the daemon
 * needs on that fd/pid: read, write, TIOCSWINSZ resize, kill, waitpid,
 * close. Nothing else lives here.
 *
 * Class: com.nhztech.nhzterm.daemon.PtyHelper (JNI names below must
 * match it exactly — there is no javah step in this build).
 */
#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

#define JNI_FN(name) Java_com_nhztech_nhzterm_daemon_PtyHelper_##name

/* Copy a jstring into a malloc'd UTF-8 buffer the caller frees. */
static char *jstr(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    if (!c) return NULL;
    char *dup = strdup(c);
    (*env)->ReleaseStringUTFChars(env, s, c);
    return dup;
}

/*
 * forkpty() + execv(). Returns the child pid (>0) or -1.
 * outMaster[0] receives the PTY master fd on success.
 */
JNIEXPORT jint JNICALL JNI_FN(nativeForkPty)(
        JNIEnv *env, jclass clazz,
        jstring cmd, jobjectArray args, jstring cwd,
        jint rows, jint cols, jintArray outMaster) {
    (void) clazz;
    char *cmdc = jstr(env, cmd);
    char *cwdc = jstr(env, cwd);
    if (!cmdc) return -1;

    jsize n = args ? (*env)->GetArrayLength(env, args) : 0;
    char **argv = (char **) calloc((size_t) n + 2, sizeof(char *));
    if (!argv) { free(cmdc); free(cwdc); return -1; }
    argv[0] = cmdc;
    for (jsize i = 0; i < n; i++) {
        jstring a = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        argv[i + 1] = jstr(env, a);
        if (a) (*env)->DeleteLocalRef(env, a);
    }
    argv[n + 1] = NULL;

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);

    int master = -1;
    pid_t pid = forkpty(&master, NULL, NULL, &ws);
    if (pid < 0) {
        for (jsize i = 0; i <= n; i++) free(argv[i]);
        free(argv);
        free(cwdc);
        return -1;
    }
    if (pid == 0) {
        /* child — forkpty already did setsid() + TIOCSCTTY */
        if (cwdc && cwdc[0]) {
            if (chdir(cwdc) != 0) _exit(126);
        }
        execv(cmdc, argv);
        _exit(127); /* exec failed — surface as exit code, like a shell */
    }

    /* parent */
    jint m = (jint) master;
    (*env)->SetIntArrayRegion(env, outMaster, 0, 1, &m);
    for (jsize i = 0; i <= n; i++) free(argv[i]);
    free(argv);
    free(cwdc);
    return (jint) pid;
}

/* read() on the master. Returns bytes read, 0 on EOF, -1 on error. */
JNIEXPORT jint JNICALL JNI_FN(nativeRead)(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buf) {
    (void) clazz;
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    if (!b) return -1;
    jsize len = (*env)->GetArrayLength(env, buf);
    ssize_t r = read(fd, b, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, 0);
    if (r < 0) return -1; /* includes EIO: all slave fds closed = session gone */
    return (jint) r;
}

/* write() to the master. Returns bytes written or -1. */
JNIEXPORT jint JNICALL JNI_FN(nativeWrite)(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray data, jint off, jint len) {
    (void) clazz;
    jbyte *b = (*env)->GetByteArrayElements(env, data, NULL);
    if (!b) return -1;
    ssize_t w = write(fd, b + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, data, b, JNI_ABORT);
    return (w < 0) ? -1 : (jint) w;
}

/* TIOCSWINSZ — the resize the tmux-less paths could never do. */
JNIEXPORT jint JNICALL JNI_FN(nativeResize)(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void) env; (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    return ioctl(fd, TIOCSWINSZ, &ws) == 0 ? 0 : -1;
}

/* kill(pid, sig). sig=0 probes liveness (0 alive, -1 gone). */
JNIEXPORT jint JNICALL JNI_FN(nativeKill)(
        JNIEnv *env, jclass clazz, jint pid, jint sig) {
    (void) env; (void) clazz;
    return kill((pid_t) pid, sig) == 0 ? 0 : -1;
}

/* Blocking reap. Returns the exit code (128+sig if signaled), -1 on error. */
JNIEXPORT jint JNICALL JNI_FN(nativeWaitpid)(
        JNIEnv *env, jclass clazz, jint pid) {
    (void) env; (void) clazz;
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeClose)(
        JNIEnv *env, jclass clazz, jint fd) {
    (void) env; (void) clazz;
    return close(fd) == 0 ? 0 : -1;
}
