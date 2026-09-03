package com.nhztech.nhzterm.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * nhzterm-api client — the documented way ANY application talks to
 * nhztermd (concept doc §6.3/§6.4/§6.5). The in-app UI uses this exact
 * class; there is no fast lane, no JavascriptInterface, no special
 * bridge (concept §3). Valence Studio or any future client speaks the
 * same protocol with the same token handshake.
 *
 * Usage:
 *   val api = NhztermApi(context)
 *   api.listener = myListener
 *   api.connect(onReady = { ... }, onError = { msg -> ... })
 *   api.sessionCreate(null, null) { ok, result, err -> ... }
 *
 * All callbacks are delivered on the main thread. Socket work happens on
 * a background thread; the class is safe to call from the UI.
 */
class NhztermApi(private val context: Context) {

    interface Listener {
        /** Raw PTY output bytes for an attached session (§6.5). */
        fun onOutput(sessionId: String, data: ByteArray) {}

        /** running | idle | finished; pid > 0 when a foreground command is live. */
        fun onSessionStatusChanged(sessionId: String, status: String, pid: Int) {}

        /** Unsolicited protocol-level error (§6.5). */
        fun onServerError(code: String, message: String) {}

        fun onDisconnected() {}
    }

    /** Result callback shape for every request method. */
    fun interface ResultCallback {
        fun onResult(ok: Boolean, result: JSONObject?, error: String?)
    }

    private val main = Handler(Looper.getMainLooper())
    private val nextId = AtomicInteger(1)
    private val pending = HashMap<Int, ResultCallback>()
    private val lock = Any()

    @Volatile private var socket: LocalSocket? = null
    @Volatile private var connected = false
    private var readerThread: Thread? = null

    var listener: Listener? = null

    // ------------------------------------------------------------------
    // connection lifecycle (§7.1 autostart: nobody ever starts the daemon
    // by hand — the client starts it, then handshakes)
    // ------------------------------------------------------------------

    fun connect(onReady: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                startDaemonIfNeeded()
                val s = connectWithRetry()
                handshake(s)
                socket = s
                connected = true
                startReader(s)
                main.post(onReady)
            } catch (e: Exception) {
                connected = false
                main.post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }.apply { isDaemon = true }.start()
    }

    fun disconnect() {
        connected = false
        try { socket?.close() } catch (ignored: Exception) {}
        socket = null
    }

    fun isConnected(): Boolean = connected

    private fun startDaemonIfNeeded() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                context.packageName,
                "com.nhztech.nhzterm.daemon.NhztermdService"
            )
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (ignored: Exception) {
            // Already running, or an external starter owns it — either way
            // the connect retry loop below is the real gate.
        }
    }

    private fun connectWithRetry(): LocalSocket {
        var last: Exception? = null
        for (attempt in 0 until 100) { // up to ~5 s of service cold-start
            try {
                val s = LocalSocket()
                s.connect(
                    LocalSocketAddress(
                        Protocol.DAEMON_SOCKET,
                        LocalSocketAddress.Namespace.ABSTRACT
                    )
                )
                return s
            } catch (e: Exception) {
                last = e
                try { Thread.sleep(50) } catch (ignored: InterruptedException) {}
            }
        }
        throw last ?: IOException("could not reach nhztermd")
    }

    private fun handshake(s: LocalSocket) {
        val hello = JSONObject()
        hello.put("type", "hello")
        hello.put("protocol_version", Protocol.PROTOCOL_VERSION)
        hello.put("token", readToken())
        Protocol.writeFrame(s.outputStream, hello)

        val ack = Protocol.readFrame(s.inputStream) ?: throw IOException("no hello_ack from daemon")
        if (ack.optString("type") != "hello_ack") throw IOException("expected hello_ack, got " + ack.optString("type"))
        if (!ack.optBoolean("accepted", false)) {
            throw IOException("handshake refused: " + ack.optString("reason", "unknown"))
        }
    }

    /**
     * Token source (§8/§13). In-app clients read the app-private
     * run/auth.token directly. A fully external app cannot (sandbox) —
     * cross-app token distribution is an open integration decision
     * tracked in docs/STATUS.md; the handshake itself is identical.
     */
    private fun readToken(): String =
        File(context.filesDir, "run/auth.token").readText().trim()

    private fun startReader(s: LocalSocket) {
        readerThread = Thread {
            try {
                while (true) {
                    val msg = Protocol.readFrame(s.inputStream) ?: break
                    dispatch(msg)
                }
            } catch (ignored: Exception) {
                // socket closed / daemon restarted
            } finally {
                connected = false
                main.post { listener?.onDisconnected() }
            }
        }.apply { isDaemon = true }
        readerThread?.start()
    }

    private fun dispatch(msg: JSONObject) {
        when (msg.optString("type")) {
            "response" -> {
                val id = msg.optInt("id", -1)
                val cb = synchronized(lock) { pending.remove(id) }
                if (cb != null) {
                    main.post {
                        if (msg.optBoolean("ok", false)) {
                            cb.onResult(true, msg.optJSONObject("result"), null)
                        } else {
                            val err = msg.optJSONObject("error")
                            val code = err?.optString("code") ?: Protocol.ERR_INTERNAL_ERROR
                            val text = err?.optString("message") ?: ""
                            cb.onResult(false, null, "$code: $text")
                        }
                    }
                }
            }
            "output" -> {
                val sid = msg.optString("session_id")
                val data = Base64.decode(msg.optString("data", ""), Base64.DEFAULT)
                main.post { listener?.onOutput(sid, data) }
            }
            "session_status_changed" -> {
                val sid = msg.optString("session_id")
                val status = msg.optString("status")
                val pid = msg.optInt("pid", -1)
                main.post { listener?.onSessionStatusChanged(sid, status, pid) }
            }
            "error" -> {
                val code = msg.optString("code", Protocol.ERR_INTERNAL_ERROR)
                val text = msg.optString("message", "")
                main.post { listener?.onServerError(code, text) }
            }
        }
    }

    // ------------------------------------------------------------------
    // request plumbing
    // ------------------------------------------------------------------

    private fun request(method: String, params: JSONObject, cb: ResultCallback) {
        if (!connected) {
            main.post { cb.onResult(false, null, "not connected") }
            return
        }
        val id = nextId.getAndIncrement()
        val msg = JSONObject()
        msg.put("type", "request")
        msg.put("id", id)
        msg.put("method", method)
        msg.put("params", params)
        synchronized(lock) { pending[id] = cb }
        try {
            val s = socket ?: throw IOException("not connected")
            synchronized(s) { Protocol.writeFrame(s.outputStream, msg) }
        } catch (e: Exception) {
            synchronized(lock) { pending.remove(id) }
            main.post { cb.onResult(false, null, e.message ?: "write failed") }
        }
    }

    // ------------------------------------------------------------------
    // session control (§6.3) — names/params/returns match the table exactly
    // ------------------------------------------------------------------

    /** -> { session_id, pty_cols, pty_rows } or SESSION_LIMIT_REACHED (§8 cap 15). */
    fun sessionCreate(shell: String?, cwd: String?, cb: ResultCallback) {
        val p = JSONObject()
        if (shell != null) p.put("shell", shell)
        if (cwd != null) p.put("cwd", cwd)
        request("session.create", p, cb)
    }

    /**
     * -> { scrollback: base64 string, status } — then live `output` events
     * stream for this session until disconnect. base64 keeps the channel
     * binary-safe for raw PTY bytes.
     */
    fun sessionAttach(sessionId: String, cb: ResultCallback) {
        request("session.attach", JSONObject().put("session_id", sessionId), cb)
    }

    /** -> { sessions: [{ session_id, name, status, created_at }] } */
    fun sessionList(cb: ResultCallback) {
        request("session.list", JSONObject(), cb)
    }

    fun sessionKill(sessionId: String, cb: ResultCallback) {
        request("session.kill", JSONObject().put("session_id", sessionId), cb)
    }

    /** Fire-and-forget (§6.3): the bytes echo back via the output stream. */
    fun sessionWrite(sessionId: String, data: ByteArray) {
        val p = JSONObject()
        p.put("session_id", sessionId)
        p.put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        request("session.write", p) { _, _, _ -> }
    }

    fun sessionResize(sessionId: String, cols: Int, rows: Int, cb: ResultCallback) {
        val p = JSONObject()
        p.put("session_id", sessionId)
        p.put("cols", cols)
        p.put("rows", rows)
        request("session.resize", p, cb)
    }

    fun sessionRename(sessionId: String, name: String, cb: ResultCallback) {
        val p = JSONObject()
        p.put("session_id", sessionId)
        p.put("name", name)
        request("session.rename", p, cb)
    }

    // ------------------------------------------------------------------
    // process control (§6.4)
    // ------------------------------------------------------------------

    /** -> { process_id, pid } */
    fun processSpawn(cmd: String, args: List<String>?, cwd: String?, cb: ResultCallback) {
        val p = JSONObject()
        p.put("cmd", cmd)
        if (args != null) p.put("args", JSONArray(args))
        if (cwd != null) p.put("cwd", cwd)
        request("process.spawn", p, cb)
    }

    /** -> { status: running|exited, exit_code? } */
    fun processStatus(processId: String, cb: ResultCallback) {
        request("process.status", JSONObject().put("process_id", processId), cb)
    }

    fun processStop(processId: String, cb: ResultCallback) {
        request("process.stop", JSONObject().put("process_id", processId), cb)
    }

    /** -> { processes: [...] } */
    fun processList(cb: ResultCallback) {
        request("process.list", JSONObject(), cb)
    }

    /**
     * Kill a specific PID (§6.4/§9) — the UI's "Kill Process" action calls
     * this against the session's currently reported foreground PID. An
     * unknown/null PID yields PROCESS_NOT_FOUND so the UI can show an
     * honest "nothing is currently running" state instead of a dead tap.
     */
    fun processKill(pid: Int, cb: ResultCallback) {
        request("process.kill", JSONObject().put("pid", pid), cb)
    }
}
