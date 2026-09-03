package com.nhztech.nhzterm.daemon

import android.content.Context
import android.system.Os
import android.util.Base64
import android.util.Log
import com.nhztech.nhzterm.api.Protocol
import org.json.JSONObject
import java.io.File

/**
 * Session control — concept doc §6.3, build plan Part 1 Phase 3.
 *
 * Owns the session table, the 15-session cap (§8), shell resolution
 * (staged nhzsh, §12.2), PTY spawning through the probed tool (§4),
 * scrollback capture, metadata persistence (§12.2 sessions/<id>/meta.json)
 * and the foreground-PID map fed by nhzsh's self-reports (§9).
 */
class SessionManager(
    private val context: Context,
    private val dirs: RuntimeDirs,
    /** Broadcast to ALL handshaked clients (status changes). Output events
     *  go only to the session's attached clients. */
    private val onBroadcast: (JSONObject) -> Unit
) {
    private val sessions = LinkedHashMap<String, Session>()
    private val lock = Any()

    @Volatile
    var tool: String? = null
        private set

    private var seq = 1

    fun start() {
        tool = PtyProbe.probe()
        loadPersisted()
    }

    // ------------------------------------------------------------------
    // §6.3 methods
    // ------------------------------------------------------------------

    /** @return the session, or null with an §6.6 error code. */
    fun create(shell: String?, cwd: String?, cols: Int, rows: Int): Pair<Session?, String?> {
        synchronized(lock) {
            if (sessions.size >= DaemonConfig.MAX_SESSIONS) {
                return Pair(null, Protocol.ERR_SESSION_LIMIT_REACHED)
            }
            val t = tool
            if (t == null) {
                return Pair(null, Protocol.ERR_INTERNAL_ERROR) // "no PTY tool (tmux/screen/socat) found"
            }
            val id = newId()
            val resolved = resolveShell(shell)
            val session = Session(
                id = id,
                name = "session-${seq++}",
                createdAt = System.currentTimeMillis(),
                cwd = cwd ?: dirs.home.absolutePath,
                shell = resolved,
                cols = cols,
                rows = rows
            )
            sessions[id] = session
            spawnPty(session)
            persistMeta(session)
            broadcastStatus(session)
            return Pair(session, null)
        }
    }

    fun get(sessionId: String): Session? = synchronized(lock) { sessions[sessionId] }

    fun list(): List<Session> = synchronized(lock) { sessions.values.toList() }

    fun rename(sessionId: String, name: String): Boolean {
        val s = get(sessionId) ?: return false
        s.name = name
        persistMeta(s)
        broadcastStatus(s)
        return true
    }

    fun kill(sessionId: String): Boolean {
        val s = synchronized(lock) { sessions.remove(sessionId) } ?: return false
        try { s.handle?.kill() } catch (ignored: Exception) {}
        s.handle = null
        s.status = "finished"
        s.foregroundPid = null
        broadcastStatus(s)
        val metaDir = File(dirs.sessionsDir, s.id)
        try { metaDir.deleteRecursively() } catch (ignored: Exception) {}
        return true
    }

    fun write(sessionId: String, data: ByteArray): Boolean {
        val s = get(sessionId) ?: return false
        if (s.status == "finished") return false
        s.handle?.write(data)
        return true
    }

    fun resize(sessionId: String, cols: Int, rows: Int): Boolean {
        val s = get(sessionId) ?: return false
        s.cols = cols
        s.rows = rows
        s.handle?.resize(cols, rows)
        return true
    }

    /** §6.3 attach: full scrollback replay + live streaming afterwards. */
    fun attach(sessionId: String, client: ApiClient): Pair<String, String>? {
        val s = get(sessionId) ?: return null
        synchronized(s.clientsLock) { s.clients.add(client) }
        return Pair(s.scrollback.replay(), s.status)
    }

    fun detachClient(client: ApiClient) {
        for (s in list()) {
            synchronized(s.clientsLock) { s.clients.remove(client) }
        }
    }

    // ------------------------------------------------------------------
    // shell resolution & spawning
    // ------------------------------------------------------------------

    /**
     * §12.2: sessions exec the real staged system/bin/nhzsh. Until nhzsh
     * is staged (or on ABIs without it), fall back honestly to the
     * placeholder sh — the terminal must never be a dead end.
     */
    fun resolveShell(requested: String?): String {
        val want = requested ?: DaemonConfig.DEFAULT_SHELL
        if (want == "nhzsh" || want.endsWith("/nhzsh")) {
            val staged = dirs.stagedShell
            if (staged.exists() && staged.canExecute()) return staged.absolutePath
            Log.w(TAG, "nhzsh not staged — falling back to " + DaemonConfig.FALLBACK_SHELL)
            return DaemonConfig.FALLBACK_SHELL
        }
        return want
    }

    private fun spawnPty(session: Session) {
        val t = tool ?: return
        val envScript = writeEnvScript(session)
        val spec = PtySpec(t, "nhz-" + session.id, envScript, dirs.varCache)
        val handle = PtyProbe.open(
            spec, session.cols, session.rows,
            onOutput = { bytes -> onSessionOutput(session, bytes) },
            onExit = { onShellExit(session) }
        )
        if (handle == null) {
            session.status = "finished"
            return
        }
        session.handle = handle
    }

    private fun writeEnvScript(session: Session): File {
        val f = File(dirs.runDir, "env-${session.id}.sh")
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val path = nativeLibDir + ":" + dirs.systemBin.absolutePath +
            ":/system/bin:/system/xbin:/vendor/bin"
        f.writeText(
            "#!/system/bin/sh\n" +
                "# nhztermd session env — generated, do not edit\n" +
                "export HOME=" + q(dirs.home.absolutePath) + "\n" +
                "export TERM=xterm-256color\n" +
                "export COLORTERM=truecolor\n" +
                "export PATH=" + q(path) + "\n" +
                "export NHZSH_SESSION_ID=" + q(session.id) + "\n" +
                "export NHZSH_CONTROL_SOCKET=@" + Protocol.CONTROL_SOCKET + "\n" +
                "cd " + q(session.cwd) + " || cd " + q(dirs.home.absolutePath) + "\n" +
                "exec " + q(session.shell) + "\n"
        )
        try { Os.chmod(f.absolutePath, 493) /* 0755 */ } catch (ignored: Throwable) {}
        return f
    }

    private fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ------------------------------------------------------------------
    // output / status pipeline
    // ------------------------------------------------------------------

    private fun onSessionOutput(session: Session, bytes: ByteArray) {
        // NOTE: chunk-boundary-split UTF-8 sequences can produce a stray
        // replacement char in the *scrollback replay* only; the live
        // stream forwards raw bytes and is always exact.
        session.scrollback.append(String(bytes, Charsets.UTF_8))
        val ev = JSONObject()
        ev.put("type", "output")
        ev.put("session_id", session.id)
        ev.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
        session.broadcast(ev)
    }

    private fun onShellExit(session: Session) {
        if (get(session.id) == null) return // already killed & removed
        session.status = "finished"
        session.foregroundPid = null
        broadcastStatus(session)
        persistMeta(session)
    }

    /**
     * §9: nhzsh reports its foreground PID over the control channel; this
     * is the ONLY source of the session -> foreground PID map. null means
     * "nothing in the foreground" — Kill Process must then surface
     * PROCESS_NOT_FOUND, never a silent no-op.
     */
    fun onForegroundPid(sessionId: String, pid: Int?) {
        val s = get(sessionId) ?: return
        s.foregroundPid = pid
        s.status = if (pid != null) "running" else "idle"
        broadcastStatus(s)
        onWakeLockRelevantChange?.invoke()
    }

    /** Hook for the service to re-evaluate the opt-in wake lock. */
    var onWakeLockRelevantChange: (() -> Unit)? = null

    fun anyForegroundRunning(): Boolean {
        for (s in list()) if (s.foregroundPid != null) return true
        return false
    }

    private fun broadcastStatus(session: Session) {
        val ev = JSONObject()
        ev.put("type", "session_status_changed")
        ev.put("session_id", session.id)
        ev.put("status", session.status)
        ev.put("pid", session.foregroundPid ?: JSONObject.NULL)
        onBroadcast(ev)
    }

    // ------------------------------------------------------------------
    // metadata persistence (§12.2: sessions/<id>/meta.json)
    // ------------------------------------------------------------------

    private fun persistMeta(session: Session) {
        try {
            val meta = JSONObject()
            meta.put("name", session.name)
            meta.put("status", session.status)
            meta.put("created_at", session.createdAt)
            meta.put("cwd", session.cwd)
            meta.put("shell", session.shell)
            val f = session.metaFile(dirs.sessionsDir)
            f.parentFile?.mkdirs()
            f.writeText(meta.toString(2))
        } catch (ignored: Exception) {
        }
    }

    /**
     * After a daemon restart: metadata comes back; PTYs are reattached
     * when the tool's session survived (tmux/screen servers outlive the
     * daemon — tmux's whole point, §4). socat sessions are marked
     * finished honestly rather than faked alive.
     */
    private fun loadPersisted() {
        val t = tool
        val dirsList = dirs.sessionsDir.listFiles() ?: return
        for (d in dirsList) {
            if (!d.isDirectory) continue
            val metaFile = File(d, "meta.json")
            if (!metaFile.exists()) continue
            try {
                val meta = JSONObject(metaFile.readText())
                val id = d.name
                val session = Session(
                    id = id,
                    name = meta.optString("name", id),
                    createdAt = meta.optLong("created_at", System.currentTimeMillis()),
                    cwd = meta.optString("cwd", dirs.home.absolutePath),
                    shell = meta.optString("shell", DaemonConfig.FALLBACK_SHELL),
                    cols = DaemonConfig.DEFAULT_COLS,
                    rows = DaemonConfig.DEFAULT_ROWS
                )
                sessions[id] = session
                if (t != null && session.status != "finished") {
                    val envScript = writeEnvScript(session)
                    val spec = PtySpec(t, "nhz-" + id, envScript, dirs.varCache)
                    val handle = PtyProbe.reattach(
                        spec,
                        onOutput = { bytes -> onSessionOutput(session, bytes) },
                        onExit = { onShellExit(session) }
                    )
                    if (handle != null) {
                        session.handle = handle
                        session.status = "idle"
                    } else {
                        session.status = "finished"
                    }
                } else {
                    session.status = "finished"
                }
                persistMeta(session)
            } catch (e: Exception) {
                Log.w(TAG, "could not restore session " + d.name, e)
            }
        }
    }

    private fun newId(): String {
        val bytes = ByteArray(6)
        java.security.SecureRandom().nextBytes(bytes)
        val sb = StringBuilder(12)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "nhztermd.sessions"
    }
}
