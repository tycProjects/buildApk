package com.nhztech.nhzterm.daemon

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import com.nhztech.nhzterm.api.Protocol

/**
 * Daemon control side-channel — concept doc §9.
 *
 * A second LocalSocket, distinct from the client API socket, that nhzsh
 * connects to (via NHZSH_CONTROL_SOCKET=@<name> in its session env) to
 * self-report foreground PIDs. Frames use the same §6.1 codec:
 *
 *   {"type":"foreground_pid","session_id":"...","pid":1234}   on exec
 *   {"type":"foreground_pid","session_id":"...","pid":null}   on completion
 *
 * nhzsh treats this as fire-and-forget; the daemon never writes back on
 * this channel. Verified end-to-end against the real shell by
 * nhzsh/tests/test_daemon_link.c.
 */
class ControlServer(private val onForegroundPid: (String, Int?) -> Unit) {

    @Volatile
    private var server: LocalServerSocket? = null

    fun start(): Boolean {
        return try {
            val s = LocalServerSocket(Protocol.CONTROL_SOCKET)
            server = s
            val t = Thread {
                while (true) {
                    val sock: LocalSocket = try {
                        s.accept()
                    } catch (e: Exception) {
                        break // server closed
                    }
                    val ct = Thread { handleConn(sock) }
                    ct.isDaemon = true
                    ct.start()
                }
            }
            t.isDaemon = true
            t.start()
            Log.i(TAG, "control channel listening: @" + Protocol.CONTROL_SOCKET)
            true
        } catch (e: Exception) {
            Log.e(TAG, "control server failed to bind", e)
            false
        }
    }

    private fun handleConn(sock: LocalSocket) {
        try {
            val input = sock.inputStream
            while (true) {
                val msg = Protocol.readFrame(input) ?: break
                if (msg.optString("type") == "foreground_pid") {
                    val sid = msg.optString("session_id")
                    val pid: Int? = if (msg.isNull("pid")) null else msg.optInt("pid")
                    if (sid.isNotEmpty()) onForegroundPid(sid, pid)
                }
            }
        } catch (ignored: Exception) {
            // shell exited or connection dropped — nothing to do
        } finally {
            try { sock.close() } catch (ignored: Exception) {}
        }
    }

    fun stop() {
        try { server?.close() } catch (ignored: Exception) {}
        server = null
    }

    companion object {
        private const val TAG = "nhztermd.control"
    }
}
