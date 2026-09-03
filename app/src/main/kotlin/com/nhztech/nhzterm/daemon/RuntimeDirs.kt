package com.nhztech.nhzterm.daemon

import android.content.Context
import java.io.File

/**
 * Runtime state layout under app-private storage — concept doc §12.2,
 * created and verified at every daemon start (§12.3 checklist).
 *
 * <filesDir>/
 *   system/bin/nhzsh      staged, exec-permitted shell binary
 *   etc/                  daemon config, nhzshrc, live theme copies
 *   sessions/<id>/meta.json   persisted session METADATA only
 *   var/log, var/cache    daemon log + scratch (socat links, screen dumps)
 *   run/                  auth.token + per-session env scripts
 *   home/                 default cwd for automation-spawned work
 */
class RuntimeDirs private constructor(val files: File) {

    val systemBin = File(files, "system/bin")
    val etc = File(files, "etc")
    val themesDir = File(etc, "themes")
    val sessionsDir = File(files, "sessions")
    val logDir = File(files, "var/log")
    val varCache = File(files, "var/cache")
    val runDir = File(files, "run")
    val home = File(files, "home")

    val logFile: File get() = File(logDir, "nhztermd.log")
    val configFile: File get() = File(etc, "nhztermd.json")
    val tokenFile: File get() = File(runDir, "auth.token")
    val stagedShell: File get() = File(systemBin, "nhzsh")

    companion object {
        fun ensure(context: Context): RuntimeDirs {
            val d = RuntimeDirs(context.filesDir)
            for (dir in listOf(d.systemBin, d.etc, d.themesDir, d.sessionsDir, d.logDir, d.varCache, d.runDir, d.home)) {
                dir.mkdirs()
            }
            d.writeDefaultConfig()
            d.copyThemesIfFirstRun(context)
            return d
        }
    }

    private fun writeDefaultConfig() {
        if (!configFile.exists()) {
            configFile.writeText(
                "{\n" +
                    "  \"max_sessions\": ${DaemonConfig.MAX_SESSIONS},\n" +
                    "  \"scrollback_lines\": ${DaemonConfig.SCROLLBACK_LINES},\n" +
                    "  \"wake_lock\": ${DaemonConfig.WAKE_LOCK_DEFAULT}\n" +
                    "}\n"
            )
        }
    }

    /**
     * §12.2: assets/themes/ is the untouched, restorable original;
     * etc/themes/ holds active/user-modified copies. Copy once, never
     * clobber user edits afterwards.
     */
    private fun copyThemesIfFirstRun(context: Context) {
        val existing = themesDir.listFiles()
        if (existing != null && existing.isNotEmpty()) return
        try {
            val names = context.assets.list("themes") ?: return
            for (n in names) {
                context.assets.open("themes/$n").use { input ->
                    File(themesDir, n).outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (ignored: Exception) {
            // themes are cosmetic; a failed copy must never block the daemon
        }
    }
}
