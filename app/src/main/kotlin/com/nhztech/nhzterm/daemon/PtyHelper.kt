package com.nhztech.nhzterm.daemon

import android.util.Log

/**
 * JNI bindings for libptyhelper.so — concept doc §4, probe position #4
 * (the compiled native helper). The library is OPTIONAL at build time:
 * when it isn't packaged for the device's ABI, [available] is false and
 * the probe chain simply never selects this backend.
 *
 * Native implementations live in app/src/main/jni/ptyhelper.c — the
 * function names there are hard-wired to this class's fully-qualified
 * name (no javah step in the build).
 */
object PtyHelper {

    private const val TAG = "nhztermd.pty"

    val available: Boolean

    init {
        available = try {
            System.loadLibrary("ptyhelper")
            Log.i(TAG, "libptyhelper loaded — native forkpty backend available")
            true
        } catch (t: Throwable) {
            Log.i(TAG, "libptyhelper not available (" + t.message + ")")
            false
        }
    }

    /** forkpty + execv. Returns child pid (>0) or -1; outMaster[0] = master fd. */
    external fun nativeForkPty(
        cmd: String, args: Array<String>, cwd: String,
        rows: Int, cols: Int, outMaster: IntArray
    ): Int

    /** read() on the master: bytes read, 0 = EOF, -1 = error/EIO. */
    external fun nativeRead(fd: Int, buf: ByteArray): Int

    /** write() to the master at [off]..[off]+[len]; returns written or -1. */
    external fun nativeWrite(fd: Int, data: ByteArray, off: Int, len: Int): Int

    /** TIOCSWINSZ resize; 0 on success. */
    external fun nativeResize(fd: Int, rows: Int, cols: Int): Int

    /** kill(pid, sig); sig=0 probes liveness. 0 = ok/alive, -1 = error/gone. */
    external fun nativeKill(pid: Int, sig: Int): Int

    /** Blocking reap; returns exit code (128+sig if signaled), -1 on error. */
    external fun nativeWaitpid(pid: Int): Int

    /** close(fd). */
    external fun nativeClose(fd: Int): Int
}
