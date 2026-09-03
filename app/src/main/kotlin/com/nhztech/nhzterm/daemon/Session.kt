package com.nhztech.nhzterm.daemon

import android.net.LocalSocket
import com.nhztech.nhzterm.api.Protocol
import org.json.JSONObject
import java.io.File

/**
 * One PTY session. Sessions outlive any attached client (headless-first,
 * concept §2/§3) — the same model tmux uses: closing the UI never kills
 * a running build.
 */
class Session(
    val id: String,
    var name: String,
    val createdAt: Long,
    var cwd: String,
    val shell: String,
    var cols: Int,
    var rows: Int
) {
    /** §6.3 statuses: running | idle | finished. */
    @Volatile
    var status: String = "idle"

    /**
     * Current foreground PID as self-reported by nhzsh (§9), or null when
     * nothing is in the foreground — process.kill against a session with
     * null here must yield PROCESS_NOT_FOUND, never a silent no-op.
     */
    @Volatile
    var foregroundPid: Int? = null

    @Volatile
    var handle: PtyHandle? = null

    val scrollback = ScrollbackBuffer(DaemonConfig.SCROLLBACK_LINES)

    /** Clients currently attached to THIS session (receive its output). */
    val clients = LinkedHashSet<ApiClient>()
    val clientsLock = Any()

    fun broadcast(json: JSONObject) {
        val snapshot = synchronized(clientsLock) { clients.toList() }
        for (c in snapshot) c.send(json)
    }

    fun metaFile(sessionsDir: File): File = File(File(sessionsDir, id), "meta.json")
}

/**
 * A handshaked API connection. Output writes are synchronized per
 * connection so concurrent event pushes can never interleave frames.
 */
class ApiClient(val id: Int, private val sock: LocalSocket) {
    private val out = sock.outputStream

    fun send(json: JSONObject) {
        try {
            synchronized(out) { Protocol.writeFrame(out, json) }
        } catch (e: Exception) {
            // Client went away; its reader thread will notice EOF and clean up.
        }
    }

    fun close() {
        try { sock.close() } catch (ignored: Exception) {}
    }
}
