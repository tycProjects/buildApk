package tech.nhz.nhzterm.util

import android.content.Context
import java.io.File

/**
 * Runtime directory layout under app-private storage (§12.2).
 *
 *   etc/       nhztermd.json, nhzshrc, active themes
 *   sessions/  per-session meta.json (metadata only; scrollback stays in RAM)
 *   var/log/   nhztermd.log
 *   var/cache/
 *   run/       auth.token
 *   home/      default cwd for automation-spawned sessions (§6.4)
 *
 * There is deliberately no bin/ — native binaries live in the APK's native
 * library directory, which Android's installer populates and which is
 * exec-permitted (§12.1). No install.sh, no first-run copy step.
 */
class Paths(context: Context) {

    private val root: File = context.filesDir

    val etc = File(root, "etc")
    val themes = File(etc, "themes")
    val sessions = File(root, "sessions")
    val varDir = File(root, "var")
    val log = File(varDir, "log")
    val cache = File(varDir, "cache")
    val run = File(root, "run")
    val home = File(root, "home")

    val daemonConfig = File(etc, "nhztermd.json")
    val nhzshrc = File(etc, "nhzshrc")
    val authToken = File(run, "auth.token")
    val daemonLog = File(log, "nhztermd.log")

    /** Native library dir — where libnhzsh.so actually lives and is executable. */
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir)

    fun sessionDir(sessionId: String) = File(sessions, sessionId)

    /**
     * Creates the tree. Idempotent — safe on every service start, which is
     * what makes a clean install "just work" with no bootstrap step (Phase 8).
     */
    fun ensure() {
        listOf(etc, themes, sessions, log, cache, run, home).forEach { dir ->
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IllegalStateException("cannot create runtime dir: $dir")
            }
        }
        // Defense in depth (§12.3). App-private storage is already sandboxed
        // per-app, but the token dir should not be group/world reachable even
        // in the shared-uid case.
        run.setReadable(false, false)
        run.setReadable(true, true)
        run.setExecutable(false, false)
        run.setExecutable(true, true)
    }
}
