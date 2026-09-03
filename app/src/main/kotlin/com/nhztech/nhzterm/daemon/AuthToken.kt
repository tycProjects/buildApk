package com.nhztech.nhzterm.daemon

import android.system.Os
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * LocalSocket auth token — concept doc §8/§13, build plan Part 1 Phase 2.
 *
 * Generated the first time the daemon ever starts, stored owner-read-only
 * in run/auth.token. App-private storage is already inaccessible to other
 * apps by default on modern Android — the file permissions are
 * defense-in-depth, not the sole boundary.
 */
object AuthToken {

    fun ensure(tokenFile: File): String {
        if (tokenFile.exists()) {
            val t = tokenFile.readText().trim()
            if (t.isNotEmpty()) {
                restrict(tokenFile)
                return t
            }
        }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        val hex = sb.toString()
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText(hex)
        restrict(tokenFile)
        return hex
    }

    /** 0600 — owner read/write only. */
    private fun restrict(f: File) {
        try {
            Os.chmod(f.absolutePath, 384) // 0600
        } catch (ignored: Throwable) {
            f.setReadable(false, false)
            f.setReadable(true, true)
            f.setWritable(false, false)
            f.setWritable(true, true)
        }
    }

    /**
     * Constant-time comparison — never leaks which character mismatched
     * (build plan Part 1 Phase 2 explicitly requires this).
     */
    fun equals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
