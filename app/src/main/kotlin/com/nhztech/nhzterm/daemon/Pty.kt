package com.nhztech.nhzterm.daemon

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * PTY acquisition — concept doc §4.
 *
 * Startup probe, first match wins: tmux -> screen -> socat -> native
 * helper (libptyhelper.so, forkpty via JNI — §4 position #4, "last
 * resort only", used when none of the three tools exist on the device,
 * which is the normal case on a stock phone).
 *
 * Every backend runs the session through a small env script that exports
 * the session environment (HOME, TERM, PATH, NHZSH_SESSION_ID,
 * NHZSH_CONTROL_SOCKET, ...) and then exec()s the resolved shell — so
 * all four paths get an identical session setup.
 */

interface PtyHandle {
    fun write(data: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun kill()
    fun isAlive(): Boolean
}

class PtySpec(
    val tool: String,          // "tmux" | "screen" | "socat"
    val sessionTag: String,    // tmux/screen session name, or link/dump suffix
    val envScript: File,       // sh script exporting env, then exec's the shell
    val workDir: File          // scratch: socat link / screen hardcopy files
)

object PtyProbe {
    private const val TAG = "nhztermd.pty"

    fun which(tool: String): String? {
        return try {
            val p = ProcessBuilder("/system/bin/sh", "-c", "command -v $tool")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor() == 0 && out.isNotEmpty()) out else null
        } catch (e: Exception) {
            null
        }
    }

    /** §4 order: tmux, then screen, then socat, then the native helper. */
    fun probe(): String? {
        for (tool in listOf("tmux", "screen", "socat")) {
            val path = which(tool)
            if (path != null) {
                Log.i(TAG, "PTY tool: $tool ($path)")
                return tool
            }
        }
        if (PtyHelper.available) {
            Log.i(TAG, "PTY tool: native (libptyhelper forkpty via JNI — §4 #4)")
            return "native"
        }
        Log.e(TAG, "NO PTY method available (tmux, screen, socat, native helper) — sessions cannot be created; package libptyhelper.so for this ABI")
        return null
    }

    fun open(
        spec: PtySpec,
        cols: Int,
        rows: Int,
        onOutput: (ByteArray) -> Unit,
        onExit: () -> Unit
    ): PtyHandle? {
        return when (spec.tool) {
            "tmux" -> TmuxPty.open(spec, cols, rows, onOutput, onExit)
            "screen" -> ScreenPty.open(spec, cols, rows, onOutput, onExit)
            "socat" -> SocatPty.open(spec, cols, rows, onOutput, onExit)
            "native" -> NativePty.open(spec, cols, rows, onOutput, onExit)
            else -> null
        }
    }

    /** Reattach after a daemon restart, when the tool's session survived. */
    fun reattach(
        spec: PtySpec,
        onOutput: (ByteArray) -> Unit,
        onExit: () -> Unit
    ): PtyHandle? {
        return when (spec.tool) {
            "tmux" -> if (TmuxPty.hasSession(spec.sessionTag)) TmuxPty.attach(spec, onOutput, onExit) else null
            "screen" -> if (ScreenPty.hasSession(spec.sessionTag)) ScreenPty.open(spec, 0, 0, onOutput, onExit) else null
            else -> null // socat/native children die with the daemon — nothing to reattach
        }
    }
}

// ---------------------------------------------------------------------
// tmux — preferred method (§4): battle-tested PTY handling and session
// persistence. Output flows through control mode (%output lines, octal
// escapes); input goes back as send-keys commands.
// ---------------------------------------------------------------------
class TmuxPty private constructor(
    private val ctl: Process,
    private val tag: String
) : PtyHandle {

    @Volatile private var alive = true

    companion object {
        fun open(
            spec: PtySpec, cols: Int, rows: Int,
            onOutput: (ByteArray) -> Unit, onExit: () -> Unit
        ): TmuxPty? {
            return try {
                val create = ProcessBuilder(
                    "tmux", "new-session", "-d", "-s", spec.sessionTag,
                    "-x", cols.toString(), "-y", rows.toString(),
                    "/system/bin/sh", spec.envScript.absolutePath
                ).redirectErrorStream(true).start()
                val rc = create.waitFor()
                if (rc != 0) {
                    Log.e("nhztermd.pty", "tmux new-session failed (rc=$rc)")
                    return null
                }
                attach(spec, onOutput, onExit)
            } catch (e: Exception) {
                Log.e("nhztermd.pty", "tmux open failed", e)
                null
            }
        }

        fun attach(spec: PtySpec, onOutput: (ByteArray) -> Unit, onExit: () -> Unit): TmuxPty? {
            return try {
                val ctl = ProcessBuilder("tmux", "-C", "attach", "-t", spec.sessionTag).start()
                val pty = TmuxPty(ctl, spec.sessionTag)
                val t = Thread {
                    try {
                        val reader = ctl.inputStream.bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("%output ")) {
                                val sp1 = line.indexOf(' ')
                                val sp2 = line.indexOf(' ', sp1 + 1)
                                if (sp2 > 0) onOutput(unescapeTmux(line.substring(sp2 + 1)))
                            } else if (line == "%exit") {
                                break
                            }
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        pty.alive = false
                        onExit()
                    }
                }
                t.isDaemon = true
                t.start()
                pty
            } catch (e: Exception) {
                Log.e("nhztermd.pty", "tmux attach failed", e)
                null
            }
        }

        fun hasSession(tag: String): Boolean {
            return try {
                val p = ProcessBuilder("tmux", "has-session", "-t", tag).start()
                p.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

        /** tmux control mode escapes non-printables as octal \nnn, plus \\ and \". */
        fun unescapeTmux(s: String): ByteArray {
            val out = ByteArrayOutputStream(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    val n = s[i + 1]
                    if (n in '0'..'7') {
                        var v = 0
                        var k = i + 1
                        var count = 0
                        while (k < s.length && count < 3 && s[k] in '0'..'7') {
                            v = v * 8 + (s[k] - '0')
                            k++
                            count++
                        }
                        out.write(v and 0xff)
                        i = k
                        continue
                    }
                    when (n) {
                        '\\' -> out.write('\\'.code)
                        '"' -> out.write('"'.code)
                        'n' -> out.write('\n'.code)
                        'r' -> out.write('\r'.code)
                        't' -> out.write('\t'.code)
                        else -> {
                            out.write('\\'.code)
                            out.write(n.code)
                        }
                    }
                    i += 2
                    continue
                }
                val b = c.toString().toByteArray(Charsets.UTF_8)
                out.write(b, 0, b.size)
                i++
            }
            return out.toByteArray()
        }
    }

    private fun sendCommand(cmd: String) {
        try {
            synchronized(this) {
                ctl.outputStream.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                ctl.outputStream.flush()
            }
        } catch (ignored: Exception) {
            alive = false
        }
    }

    override fun write(data: ByteArray) {
        // Control-mode input is tmux commands: printable runs go through
        // send-keys -l, control/escape bytes map to named keys. Bytes
        // outside this map are a known limitation of the tmux path; the
        // socat path is fully byte-transparent.
        var i = 0
        while (i < data.size) {
            val named = namedKey(data, i)
            if (named != null) {
                sendCommand("send-keys -t $tag " + named.first)
                i += named.second
                continue
            }
            val start = i
            while (i < data.size && namedKey(data, i) == null) i++
            val text = String(data, start, i - start, Charsets.UTF_8)
            if (text.isNotEmpty()) {
                sendCommand("send-keys -t $tag -l -- " + quoteArg(text))
            }
        }
    }

    /** Map one input position to a tmux named key; null = printable text. */
    private fun namedKey(data: ByteArray, i: Int): Pair<String, Int>? {
        val b = data[i].toInt() and 0xff
        if (b >= 0x20 && b != 0x7f) return null
        return when (b) {
            0x0d, 0x0a -> Pair("Enter", 1)
            0x09 -> Pair("Tab", 1)
            0x7f, 0x08 -> Pair("BSpace", 1)
            0x00 -> Pair("C-@", 1)
            in 0x01..0x1a -> Pair("C-" + ('a' + (b - 1)), 1)
            0x1c -> Pair("C-\\", 1)
            0x1d -> Pair("C-]", 1)
            0x1e -> Pair("C-^", 1)
            0x1f -> Pair("C-_", 1)
            0x1b -> escapeSequence(data, i)
            else -> null
        }
    }

    private fun escapeSequence(data: ByteArray, i: Int): Pair<String, Int>? {
        fun seq(vararg bytes: Int): Boolean {
            if (i + bytes.size > data.size) return false
            for (k in bytes.indices) if ((data[i + k].toInt() and 0xff) != bytes[k]) return false
            return true
        }
        return when {
            seq(0x1b, 0x5b, 0x41) -> Pair("Up", 3)
            seq(0x1b, 0x5b, 0x42) -> Pair("Down", 3)
            seq(0x1b, 0x5b, 0x43) -> Pair("Right", 3)
            seq(0x1b, 0x5b, 0x44) -> Pair("Left", 3)
            seq(0x1b, 0x5b, 0x48) -> Pair("Home", 3)
            seq(0x1b, 0x5b, 0x46) -> Pair("End", 3)
            seq(0x1b, 0x5b, 0x33, 0x7e) -> Pair("DC", 4)    // Delete
            seq(0x1b, 0x5b, 0x35, 0x7e) -> Pair("PPage", 4) // Page Up
            seq(0x1b, 0x5b, 0x36, 0x7e) -> Pair("NPage", 4) // Page Down
            else -> Pair("Escape", 1)
        }
    }

    private fun quoteArg(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    override fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        sendCommand("resize-window -t $tag -x $cols -y $rows")
        // older tmux: resize-window is unknown and %error is ignored by the
        // reader, so also try the pane-level resize as a fallback
        sendCommand("resize-pane -t $tag -x $cols -y $rows")
    }

    override fun kill() {
        alive = false
        try {
            ProcessBuilder("tmux", "kill-session", "-t", tag).start().waitFor()
        } catch (ignored: Exception) {
        }
        try { ctl.destroy() } catch (ignored: Exception) {}
    }

    override fun isAlive(): Boolean = alive && ctl.isAlive
}

// ---------------------------------------------------------------------
// screen — probe position #2 (§4). Honest limitation: screen cannot
// expose a raw byte stream, so this backend polls full scrollback dumps
// (hardcopy -h). It runs real shells fine for line-oriented work;
// full-screen apps render poorly on this path. tmux or socat are the
// fidelity paths — documented, not hidden.
// ---------------------------------------------------------------------
class ScreenPty private constructor(
    private val tag: String,
    private val dumpFile: File
) : PtyHandle {

    @Volatile private var alive = true
    private var lastLen = 0L

    companion object {
        fun open(
            spec: PtySpec, cols: Int, rows: Int,
            onOutput: (ByteArray) -> Unit, onExit: () -> Unit
        ): ScreenPty? {
            return try {
                if (!hasSession(spec.sessionTag)) {
                    val create = ProcessBuilder(
                        "screen", "-dmS", spec.sessionTag,
                        "/system/bin/sh", spec.envScript.absolutePath
                    ).redirectErrorStream(true).start()
                    create.waitFor()
                }
                val pty = ScreenPty(spec.sessionTag, File(spec.workDir, "hardcopy-" + spec.sessionTag))
                val t = Thread {
                    try {
                        while (pty.alive) {
                            run("screen", "-S", spec.sessionTag, "-X", "hardcopy", "-h", pty.dumpFile.absolutePath)
                            Thread.sleep(200)
                            if (pty.dumpFile.exists()) {
                                val len = pty.dumpFile.length()
                                if (len < pty.lastLen) pty.lastLen = 0
                                if (len > pty.lastLen) {
                                    val want = (len - pty.lastLen).toInt().coerceAtMost(65536)
                                    FileInputStream(pty.dumpFile).use { fis ->
                                        val skipped = fis.skip(pty.lastLen)
                                        if (skipped == pty.lastLen) {
                                            val buf = ByteArray(want)
                                            var off = 0
                                            while (off < buf.size) {
                                                val r = fis.read(buf, off, buf.size - off)
                                                if (r < 0) break
                                                off += r
                                            }
                                            if (off > 0) onOutput(buf.copyOf(off))
                                        }
                                    }
                                    pty.lastLen = len
                                }
                            }
                            if (!hasSession(spec.sessionTag)) break
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        pty.alive = false
                        onExit()
                    }
                }
                t.isDaemon = true
                t.start()
                pty
            } catch (e: Exception) {
                Log.e("nhztermd.pty", "screen open failed", e)
                null
            }
        }

        fun hasSession(tag: String): Boolean {
            return try {
                val p = ProcessBuilder("screen", "-ls").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out.contains(".$tag") || out.contains("\t$tag")
            } catch (e: Exception) {
                false
            }
        }

        private fun run(vararg cmd: String): Int {
            return try {
                val p = ProcessBuilder(*cmd).start()
                p.waitFor()
            } catch (e: Exception) {
                -1
            }
        }
    }

    override fun write(data: ByteArray) {
        // screen's "stuff" injects the literal characters into the session.
        val text = String(data, Charsets.UTF_8)
        try {
            ProcessBuilder("screen", "-S", tag, "-X", "stuff", text).start().waitFor()
        } catch (ignored: Exception) {
            alive = false
        }
    }

    override fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        run("screen", "-S", tag, "-X", "width", "-w", cols.toString())
        run("screen", "-S", tag, "-X", "height", "-w", rows.toString())
    }

    override fun kill() {
        alive = false
        run("screen", "-S", tag, "-X", "quit")
    }

    override fun isAlive(): Boolean = alive
}

// ---------------------------------------------------------------------
// socat — probe position #3 (§4): fully byte-transparent PTY bridge.
// socat owns the master; the daemon opens the linked slave for reading
// and writing. waitslave holds the shell's exec until we're attached, so
// early output is never lost. Resize needs a TIOCSWINSZ struct ioctl the
// platform doesn't expose — it arrives with the native PTY helper (post-v1).
// ---------------------------------------------------------------------
class SocatPty private constructor(
    private val proc: Process,
    private val link: File,
    private val input: FileInputStream,
    private val output: FileOutputStream
) : PtyHandle {

    @Volatile private var alive = true

    companion object {
        fun open(
            spec: PtySpec, cols: Int, rows: Int,
            onOutput: (ByteArray) -> Unit, onExit: () -> Unit
        ): SocatPty? {
            val link = File(spec.workDir, "pty-" + spec.sessionTag)
            link.delete()
            val cmd = "exec socat PTY,link=" + link.absolutePath +
                ",raw,echo=0,waitslave EXEC:'/system/bin/sh " +
                spec.envScript.absolutePath + "',pty,stderr,setsid"
            return try {
                val proc = ProcessBuilder("/system/bin/sh", "-c", cmd).start()
                var waited = 0
                while (!link.exists() && waited < 5000) {
                    Thread.sleep(50)
                    waited += 50
                }
                if (!link.exists()) {
                    proc.destroy()
                    Log.e("nhztermd.pty", "socat PTY link never appeared")
                    return null
                }
                val inp = FileInputStream(link)
                val outp = FileOutputStream(link)
                val pty = SocatPty(proc, link, inp, outp)
                val t = Thread {
                    try {
                        val buf = ByteArray(8192)
                        while (pty.alive) {
                            val r = inp.read(buf)
                            if (r < 0) break
                            if (r > 0) onOutput(buf.copyOf(r))
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        pty.alive = false
                        onExit()
                    }
                }
                t.isDaemon = true
                t.start()
                pty
            } catch (e: Exception) {
                Log.e("nhztermd.pty", "socat open failed", e)
                null
            }
        }
    }

    override fun write(data: ByteArray) {
        try {
            synchronized(output) {
                output.write(data)
                output.flush()
            }
        } catch (e: Exception) {
            alive = false
        }
    }

    override fun resize(cols: Int, rows: Int) {
        Log.w("nhztermd.pty", "resize unsupported on socat path until the native PTY helper lands (post-v1)")
    }

    override fun kill() {
        alive = false
        try { output.close() } catch (ignored: Exception) {}
        try { input.close() } catch (ignored: Exception) {}
        try { proc.destroy() } catch (ignored: Exception) {}
        link.delete()
    }

    override fun isAlive(): Boolean = alive && proc.isAlive
}

// ---------------------------------------------------------------------
// native — probe position #4 (§4): forkpty via JNI, no external process.
// The fidelity path on stock devices: byte-transparent, real
// TIOCSWINSZ resize, and exit detection through the master fd. Sessions
// live as children of the daemon, so (like socat) they don't survive a
// daemon restart — reattach is honestly unsupported, not faked.
// ---------------------------------------------------------------------
class NativePty private constructor(
    private val pid: Int,
    private val fd: Int
) : PtyHandle {

    @Volatile private var alive = true

    companion object {
        fun open(
            spec: PtySpec, cols: Int, rows: Int,
            onOutput: (ByteArray) -> Unit, onExit: () -> Unit
        ): NativePty? {
            if (!PtyHelper.available) return null
            val outMaster = IntArray(1)
            val pid = PtyHelper.nativeForkPty(
                "/system/bin/sh",
                arrayOf(spec.envScript.absolutePath),
                "",                       // env script cd's to HOME itself
                rows, cols, outMaster
            )
            if (pid <= 0) {
                Log.e("nhztermd.pty", "native forkpty failed")
                return null
            }
            val pty = NativePty(pid, outMaster[0])
            Log.i("nhztermd.pty", "native PTY: pid=$pid fd=${pty.fd} tag=${spec.sessionTag}")
            val t = Thread {
                try {
                    val buf = ByteArray(8192)
                    while (pty.alive) {
                        val r = PtyHelper.nativeRead(pty.fd, buf)
                        if (r <= 0) break          // 0/-1: EOF or EIO — shell exited
                        onOutput(buf.copyOf(r))
                    }
                } catch (ignored: Exception) {
                } finally {
                    PtyHelper.nativeWaitpid(pty.pid)   // reap — no zombies
                    PtyHelper.nativeClose(pty.fd)
                    pty.alive = false
                    onExit()
                }
            }
            t.isDaemon = true
            t.name = "native-pty-" + spec.sessionTag
            t.start()
            return pty
        }
    }

    override fun write(data: ByteArray) {
        try {
            var off = 0
            while (off < data.size && alive) {
                val w = PtyHelper.nativeWrite(fd, data, off, data.size - off)
                if (w <= 0) {
                    alive = false
                    return
                }
                off += w
            }
        } catch (e: Exception) {
            alive = false
        }
    }

    override fun resize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        PtyHelper.nativeResize(fd, rows, cols)
    }

    /** §9/§14 kill policy: SIGTERM, then SIGKILL after 1.5 s if still up. */
    override fun kill() {
        if (!alive) return
        PtyHelper.nativeKill(pid, 15 /* SIGTERM */)
        val t = Thread {
            try { Thread.sleep(1500) } catch (ignored: Exception) {}
            if (alive && PtyHelper.nativeKill(pid, 0) == 0) {
                PtyHelper.nativeKill(pid, 9 /* SIGKILL */)
            }
        }
        t.isDaemon = true
        t.start()
    }

    override fun isAlive(): Boolean = alive
}
