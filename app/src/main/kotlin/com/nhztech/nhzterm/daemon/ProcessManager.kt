package com.nhztech.nhzterm.daemon

import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.nhztech.nhzterm.api.Protocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Process control — concept doc §6.4, build plan Part 1 Phase 4.
 *
 * spawn/status/stop/list operate on daemon-spawned child processes;
 * kill(pid) targets a specific PID — in practice the session's current
 * foreground PID from nhzsh's self-reports (§9). Unknown PIDs get an
 * honest PROCESS_NOT_FOUND.
 *
 * Signal policy (open decision §14.3): SIGTERM first with a short grace
 * period, then SIGKILL — the conservative default until the owner rules.
 */
class ProcessManager(
    private val dirs: RuntimeDirs,
    private val sessions: SessionManager
) {
    class Entry(
        val processId: String,
        val process: Process,
        val pid: Int,
        val cmd: String
    )

    private val entries = LinkedHashMap<String, Entry>()
    private val lock = Any()
    private var seq = 1

    data class SpawnResult(val processId: String?, val pid: Int, val error: String?)

    fun spawn(cmd: String, args: List<String>, cwd: String?): SpawnResult {
        return try {
            val pb = ProcessBuilder(listOf(cmd) + args)
            pb.directory(File(cwd ?: dirs.home.absolutePath))
            pb.environment()["HOME"] = dirs.home.absolutePath
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] = dirs.systemBin.absolutePath + ":/system/bin:/system/xbin:/vendor/bin"
            val p = pb.start()
            val id = "p" + (seq++)
            val entry = Entry(id, p, pidOf(p), cmd)
            synchronized(lock) { entries[id] = entry }
            SpawnResult(id, entry.pid, null)
        } catch (e: Exception) {
            SpawnResult(null, -1, Protocol.ERR_INTERNAL_ERROR + ": " + (e.message ?: "spawn failed"))
        }
    }

    fun status(processId: String): JSONObject? {
        val e = synchronized(lock) { entries[processId] } ?: return null
        val json = JSONObject()
        if (e.process.isAlive) {
            json.put("status", "running")
        } else {
            json.put("status", "exited")
            try { json.put("exit_code", e.process.exitValue()) } catch (ignored: Exception) {}
        }
        return json
    }

    fun stop(processId: String): Boolean {
        val e = synchronized(lock) { entries[processId] } ?: return false
        return terminate(e)
    }

    fun list(): JSONArray {
        val arr = JSONArray()
        for (e in synchronized(lock) { entries.values.toList() }) {
            val json = JSONObject()
            json.put("process_id", e.processId)
            json.put("pid", e.pid)
            json.put("cmd", e.cmd)
            json.put("status", if (e.process.isAlive) "running" else "exited")
            arr.put(json)
        }
        return arr
    }

    /**
     * §6.4/§9 process.kill — used by the UI's Kill Process action against
     * the session's tracked foreground PID. Accepts PIDs we legitimately
     * own: daemon-spawned children, or a currently reported session
     * foreground PID. Anything else: PROCESS_NOT_FOUND (no silent no-op).
     */
    fun kill(pid: Int): String? {
        if (pid <= 0) return Protocol.ERR_PROCESS_NOT_FOUND

        val owned = synchronized(lock) { entries.values.any { it.pid == pid } }
        val isForeground = sessions.list().any { it.foregroundPid == pid }
        if (!owned && !isForeground) return Protocol.ERR_PROCESS_NOT_FOUND

        return try {
            Os.kill(pid, OsConstants.SIGTERM)
            // Grace period, then escalate (policy §14.3 — conservative default).
            val t = Thread {
                try {
                    Thread.sleep(1500)
                    if (alive(pid)) Os.kill(pid, OsConstants.SIGKILL)
                } catch (ignored: Exception) {
                }
            }
            t.isDaemon = true
            t.start()
            null
        } catch (e: Exception) {
            Log.w(TAG, "kill($pid) failed", e)
            Protocol.ERR_PROCESS_NOT_FOUND
        }
    }

    private fun terminate(e: Entry): Boolean {
        return try {
            e.process.destroy()
            val t = Thread {
                try {
                    Thread.sleep(1500)
                    if (e.process.isAlive) e.process.destroyForcibly()
                } catch (ignored: Exception) {
                }
            }
            t.isDaemon = true
            t.start()
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun alive(pid: Int): Boolean {
        return try {
            Os.kill(pid, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Android's java.lang.Process does NOT expose pid() — that method is
     * Java 9+, and Android's libcore Process implementation
     * (java.lang.ProcessManager.ProcessImpl) keeps the pid in a private
     * field instead. Read it reflectively — the same technique Termux
     * uses. Returns -1 if the platform changes and the field is gone,
     * which callers treat as "no pid known" (kill(pid<=0) already
     * rejects gracefully, §9).
     */
    private fun pidOf(p: Process): Int {
        return try {
            val f = p.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            f.getInt(p)
        } catch (e: Exception) {
            Log.w(TAG, "pid reflection failed: " + e.message)
            -1
        }
    }

    companion object {
        private const val TAG = "nhztermd.process"
    }
}
