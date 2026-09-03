package com.nhztech.nhzterm.api

import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * nhzterm-api wire protocol — concept doc §6.
 *
 * Framing (§6.1): a 4-byte big-endian length header followed by a UTF-8
 * JSON body, over an Android LocalSocket bound in the abstract namespace
 * (no filesystem path anywhere). The exact same framing is reused by
 * nhzsh's daemon control side-channel (concept §9), so one codec serves
 * both the client API and the shell's foreground-PID reports.
 *
 * This object is the ONLY place the wire format is defined; the daemon
 * (app module) and every client (this module) both go through it.
 */
object Protocol {

    /** Exchanged during the handshake; a mismatch is a clean explicit error (§8). */
    const val PROTOCOL_VERSION = 1

    /** Abstract-namespace LocalSocket names. */
    const val DAEMON_SOCKET = "com.nhztech.nhzterm.nhztermd"
    const val CONTROL_SOCKET = "com.nhztech.nhzterm.control"

    /** Sanity bound so a corrupt length header can't OOM the daemon. */
    const val MAX_FRAME_BYTES = 16 * 1024 * 1024

    // ---- error codes (§6.6) ----
    const val ERR_AUTH_FAILED = "AUTH_FAILED"
    const val ERR_PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH"
    const val ERR_SESSION_LIMIT_REACHED = "SESSION_LIMIT_REACHED"
    const val ERR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val ERR_PROCESS_NOT_FOUND = "PROCESS_NOT_FOUND"
    const val ERR_INTERNAL_ERROR = "INTERNAL_ERROR"

    @Throws(IOException::class)
    fun writeFrame(out: OutputStream, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val len = body.size
        val hdr = byteArrayOf(
            ((len ushr 24) and 0xff).toByte(),
            ((len ushr 16) and 0xff).toByte(),
            ((len ushr 8) and 0xff).toByte(),
            (len and 0xff).toByte()
        )
        out.write(hdr)
        out.write(body)
        out.flush()
    }

    /**
     * Read one frame. Returns null on a clean EOF (connection closed
     * before any byte of the header); throws on truncation/garbage —
     * fail loudly, never misparse silently (concept §2, principle 7).
     */
    @Throws(IOException::class)
    fun readFrame(input: InputStream): JSONObject? {
        val hdr = ByteArray(4)
        if (!readFully(input, hdr)) return null
        val len = ((hdr[0].toInt() and 0xff) shl 24) or
            ((hdr[1].toInt() and 0xff) shl 16) or
            ((hdr[2].toInt() and 0xff) shl 8) or
            (hdr[3].toInt() and 0xff)
        if (len <= 0 || len > MAX_FRAME_BYTES) throw IOException("bad frame length: $len")
        val body = ByteArray(len)
        readFully(input, body)
        return JSONObject(String(body, Charsets.UTF_8))
    }

    /** Fill buf completely; false only when EOF arrives before byte 0. */
    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) {
                if (off == 0) return false
                throw IOException("unexpected end of stream (partial frame)")
            }
            off += r
        }
        return true
    }
}
