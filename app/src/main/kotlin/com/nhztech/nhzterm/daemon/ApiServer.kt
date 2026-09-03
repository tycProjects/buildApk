package com.nhztech.nhzterm.daemon

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Base64
import android.util.Log
import com.nhztech.nhzterm.api.Protocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * nhzterm-api server — concept doc §6, build plan Part 1 Phases 2-3-4.
 *
 * Every client (in-app UI, Valence Studio, anything) gets the identical
 * treatment: connect to the abstract LocalSocket, send `hello` with the
 * token as the very first frame, then speak requests. No fast lanes (§3).
 */
class ApiServer(
    private val token: String,
    private val sessions: SessionManager,
    private val processes: ProcessManager
) {
    @Volatile
    private var server: LocalServerSocket? = null

    private val clients = LinkedHashMap<Int, ApiClient>()
    private val clientsLock = Any()
    private var clientSeq = 1

    fun start(): Boolean {
        return try {
            val s = LocalServerSocket(Protocol.DAEMON_SOCKET)
            server = s
            val t = Thread {
                while (true) {
                    val sock: LocalSocket = try {
                        s.accept()
                    } catch (e: Exception) {
                        break
                    }
                    val ct = Thread { handleClient(sock) }
                    ct.isDaemon = true
                    ct.start()
                }
            }
            t.isDaemon = true
            t.start()
            Log.i(TAG, "api server listening: @" + Protocol.DAEMON_SOCKET)
            true
        } catch (e: Exception) {
            Log.e(TAG, "api server failed to bind", e)
            false
        }
    }

    fun stop() {
        try { server?.close() } catch (ignored: Exception) {}
        server = null
        synchronized(clientsLock) {
            for (c in clients.values) c.close()
            clients.clear()
        }
    }

    /** Push an event (§6.5) to every handshaked client. */
    fun broadcast(json: JSONObject) {
        val snapshot = synchronized(clientsLock) { clients.values.toList() }
        for (c in snapshot) c.send(json)
    }

    private fun nextClientId(): Int = synchronized(clientsLock) { clientSeq++ }

    private fun handleClient(sock: LocalSocket) {
        val client = ApiClient(nextClientId(), sock)
        var registered = false
        try {
            val input = sock.inputStream

            // ---- handshake (§6.2): hello MUST be the first frame ----
            val hello = Protocol.readFrame(input)
            if (hello == null || hello.optString("type") != "hello") {
                sendAck(client, false, "bad_token")
                return
            }
            if (hello.optInt("protocol_version", -1) != Protocol.PROTOCOL_VERSION) {
                val ack = JSONObject()
                ack.put("type", "hello_ack")
                ack.put("protocol_version", Protocol.PROTOCOL_VERSION)
                ack.put("accepted", false)
                ack.put("reason", "protocol_mismatch")
                client.send(ack)
                return
            }
            if (!AuthToken.equals(hello.optString("token", ""), token)) {
                sendAck(client, false, "bad_token")
                return
            }
            sendAck(client, true, null)

            synchronized(clientsLock) { clients[client.id] = client }
            registered = true

            while (true) {
                val msg = Protocol.readFrame(input) ?: break
                if (msg.optString("type") == "request") handleRequest(client, msg)
            }
        } catch (ignored: Exception) {
            // connection dropped mid-session
        } finally {
            if (registered) {
                synchronized(clientsLock) { clients.remove(client.id) }
                sessions.detachClient(client)
            }
            client.close()
        }
    }

    private fun sendAck(client: ApiClient, accepted: Boolean, reason: String?) {
        val ack = JSONObject()
        ack.put("type", "hello_ack")
        ack.put("protocol_version", Protocol.PROTOCOL_VERSION)
        ack.put("accepted", accepted)
        if (reason != null) ack.put("reason", reason)
        client.send(ack)
    }

    // ------------------------------------------------------------------
    // request dispatch — method names/params/returns per §6.3/§6.4 tables
    // ------------------------------------------------------------------

    private fun handleRequest(client: ApiClient, msg: JSONObject) {
        val id = msg.optInt("id", -1)
        val method = msg.optString("method")
        val params = msg.optJSONObject("params") ?: JSONObject()

        try {
            when (method) {
                "session.create" -> {
                    val cols = params.optInt("cols", DaemonConfig.DEFAULT_COLS)
                    val rows = params.optInt("rows", DaemonConfig.DEFAULT_ROWS)
                    val (session, err) = sessions.create(
                        optString(params, "shell"),
                        optString(params, "cwd"),
                        if (cols > 0) cols else DaemonConfig.DEFAULT_COLS,
                        if (rows > 0) rows else DaemonConfig.DEFAULT_ROWS
                    )
                    if (session == null) {
                        respondError(client, id, err ?: Protocol.ERR_INTERNAL_ERROR, "session.create failed")
                    } else {
                        val r = JSONObject()
                        r.put("session_id", session.id)
                        r.put("pty_cols", session.cols)
                        r.put("pty_rows", session.rows)
                        respondOk(client, id, r)
                    }
                }

                "session.attach" -> {
                    val sid = params.optString("session_id")
                    val res = sessions.attach(sid, client)
                    if (res == null) {
                        respondError(client, id, Protocol.ERR_SESSION_NOT_FOUND, "no session $sid")
                    } else {
                        val r = JSONObject()
                        r.put("scrollback", Base64.encodeToString(res.first.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                        r.put("status", res.second)
                        respondOk(client, id, r)
                    }
                }

                "session.list" -> {
                    val arr = JSONArray()
                    for (s in sessions.list()) {
                        val j = JSONObject()
                        j.put("session_id", s.id)
                        j.put("name", s.name)
                        j.put("status", s.status)
                        j.put("created_at", s.createdAt)
                        arr.put(j)
                    }
                    respondOk(client, id, JSONObject().put("sessions", arr))
                }

                "session.kill" -> {
                    val sid = params.optString("session_id")
                    if (sessions.kill(sid)) respondOk(client, id, JSONObject().put("ok", true))
                    else respondError(client, id, Protocol.ERR_SESSION_NOT_FOUND, "no session $sid")
                }

                "session.write" -> {
                    val sid = params.optString("session_id")
                    val data = Base64.decode(params.optString("data", ""), Base64.DEFAULT)
                    if (sessions.write(sid, data)) respondOk(client, id, JSONObject())
                    else respondError(client, id, Protocol.ERR_SESSION_NOT_FOUND, "no session $sid")
                }

                "session.resize" -> {
                    val sid = params.optString("session_id")
                    if (sessions.resize(sid, params.optInt("cols"), params.optInt("rows")))
                        respondOk(client, id, JSONObject().put("ok", true))
                    else respondError(client, id, Protocol.ERR_SESSION_NOT_FOUND, "no session $sid")
                }

                "session.rename" -> {
                    val sid = params.optString("session_id")
                    if (sessions.rename(sid, params.optString("name")))
                        respondOk(client, id, JSONObject().put("ok", true))
                    else respondError(client, id, Protocol.ERR_SESSION_NOT_FOUND, "no session $sid")
                }

                "process.spawn" -> {
                    val cmd = params.optString("cmd")
                    val argsArr = params.optJSONArray("args")
                    val args = mutableListOf<String>()
                    if (argsArr != null) for (i in 0 until argsArr.length()) args.add(argsArr.optString(i))
                    val res = processes.spawn(cmd, args, optString(params, "cwd"))
                    if (res.processId == null) {
                        respondError(client, id, Protocol.ERR_INTERNAL_ERROR, res.error ?: "spawn failed")
                    } else {
                        val r = JSONObject()
                        r.put("process_id", res.processId)
                        r.put("pid", res.pid)
                        respondOk(client, id, r)
                    }
                }

                "process.status" -> {
                    val r = processes.status(params.optString("process_id"))
                    if (r == null) respondError(client, id, Protocol.ERR_PROCESS_NOT_FOUND, "unknown process_id")
                    else respondOk(client, id, r)
                }

                "process.stop" -> {
                    if (processes.stop(params.optString("process_id")))
                        respondOk(client, id, JSONObject().put("ok", true))
                    else respondError(client, id, Protocol.ERR_PROCESS_NOT_FOUND, "unknown process_id")
                }

                "process.list" -> {
                    respondOk(client, id, JSONObject().put("processes", processes.list()))
                }

                "process.kill" -> {
                    val err = processes.kill(params.optInt("pid", -1))
                    if (err == null) respondOk(client, id, JSONObject().put("ok", true))
                    else respondError(client, id, err, "pid not owned by nhztermd or nothing running")
                }

                else -> respondError(client, id, Protocol.ERR_INTERNAL_ERROR, "unknown method: $method")
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed: $method", e)
            respondError(client, id, Protocol.ERR_INTERNAL_ERROR, e.message ?: "internal error")
        }
    }

    private fun optString(params: JSONObject, key: String): String? {
        val v = params.opt(key) ?: return null
        if (v == JSONObject.NULL) return null
        val s = v.toString()
        return if (s.isEmpty()) null else s
    }

    private fun respondOk(client: ApiClient, id: Int, result: JSONObject) {
        val resp = JSONObject()
        resp.put("type", "response")
        resp.put("id", id)
        resp.put("ok", true)
        resp.put("result", result)
        client.send(resp)
    }

    private fun respondError(client: ApiClient, id: Int, code: String, message: String) {
        val err = JSONObject()
        err.put("code", code)
        err.put("message", message)
        val resp = JSONObject()
        resp.put("type", "response")
        resp.put("id", id)
        resp.put("ok", false)
        resp.put("error", err)
        client.send(resp)
    }

    companion object {
        private const val TAG = "nhztermd.api"
    }
}
