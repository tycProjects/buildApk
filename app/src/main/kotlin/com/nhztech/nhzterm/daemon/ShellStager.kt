package com.nhztech.nhzterm.daemon

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * nhzsh staging — concept doc §12.2, build plan Part 1 Phase 1.
 *
 * libnhzsh.so ships inside the APK's native library directory (the one
 * location Android's installer guarantees exec permission for). But a PTY
 * session is a genuinely separate child process: it must exec() a real
 * file at a real, stable path — never the .so directly (that path can be
 * a symlink into the APK and isn't guaranteed independently executable).
 *
 * So at daemon startup the binary is staged to system/bin/nhzsh:
 *  - content-aware: an unchanged binary is NOT re-copied on routine
 *    restarts, but a genuinely different one (after an app upgrade) IS
 *  - atomic: written to a temp file first, then renamed into place — an
 *    interrupted copy can never leave a truncated, still-"executable"
 *    binary behind
 *  - safe while in use: renaming over an open file is fine on Linux;
 *    running sessions keep their old inode until they exit naturally
 */
object ShellStager {

    private const val TAG = "nhztermd.stager"

    /** @return true when system/bin/nhzsh exists and is executable afterwards. */
    fun stage(context: Context, dirs: RuntimeDirs): Boolean {
        val source = File(context.applicationInfo.nativeLibraryDir, "libnhzsh.so")
        if (!source.exists()) {
            Log.w(TAG, "libnhzsh.so not present for this ABI — sessions will use the fallback sh")
            return false
        }
        val target = dirs.stagedShell
        try {
            if (target.exists() && target.length() == source.length() && sameContent(source, target)) {
                ensureExec(target)
                Log.i(TAG, "nhzsh already staged and current: ${target.absolutePath}")
                return target.canExecute()
            }
            val tmp = File(dirs.systemBin, "nhzsh.tmp-" + System.currentTimeMillis())
            try {
                source.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                ensureExec(tmp)
                if (!atomicRename(tmp, target)) {
                    tmp.delete()
                    Log.e(TAG, "rename into place failed")
                    return false
                }
                Log.i(TAG, "nhzsh staged: ${target.absolutePath} (${target.length()} bytes)")
                return target.canExecute()
            } catch (e: Exception) {
                tmp.delete()
                Log.e(TAG, "staging failed", e)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "staging failed", e)
            return false
        }
    }

    private fun sameContent(a: File, b: File): Boolean {
        return try {
            digest(a).contentEquals(digest(b))
        } catch (e: Exception) {
            false
        }
    }

    private fun digest(f: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { fis ->
            val buf = ByteArray(65536)
            while (true) {
                val r = fis.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest()
    }

    private fun ensureExec(f: File) {
        try {
            Os.chmod(f.absolutePath, 493) // 0755
        } catch (t: Throwable) {
            f.setExecutable(true, false)
        }
    }

    private fun atomicRename(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        return try {
            Os.rename(from.absolutePath, to.absolutePath)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
