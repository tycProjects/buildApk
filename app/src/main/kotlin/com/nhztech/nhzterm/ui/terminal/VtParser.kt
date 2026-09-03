package com.nhztech.nhzterm.ui.terminal

/**
 * ANSI/VT100/xterm escape-code parser — concept doc §10.1: the renderer
 * must handle cursor movement, color (16 + 256 + truecolor), the
 * alternate screen buffer, and Unicode/wide characters. Pure Kotlin, no
 * Android imports: unit-testable in isolation, like nhzsh's stages.
 *
 * Feed it raw PTY bytes as they arrive; it mutates a TerminalScreen.
 * Sequences requiring a reply (CPR cursor-position report) go out via
 * onResponse — the UI forwards them through session.write.
 */
class VtParser(
    private val screen: TerminalScreen,
    private val onResponse: (ByteArray) -> Unit = {}
) {
    private val ground = 0
    private val esc = 1
    private val csi = 2
    private val osc = 3
    private val charset = 4

    private var state = ground

    // CSI accumulation
    private val params = ArrayList<Int>(8)
    private var currentParam = -1
    private var privateMarker = 0
    private var csiIntermediate = 0

    // OSC accumulation
    private val oscBuf = StringBuilder()

    // UTF-8 accumulation
    private var utf8Buf = ByteArray(4)
    private var utf8Len = 0
    private var utf8Need = 0

    // pending flag so ESC \ (ST) after OSC is consumed correctly
    private var oscEscape = false

    /** Title set via OSC 0/2 — the UI may show it; parsing never fails on it. */
    var onTitle: ((String) -> Unit)? = null

    fun feed(data: ByteArray, length: Int = data.size) {
        // Byte-at-a-time: every state consumes exactly one byte per step
        // (UTF-8 sequences accumulate across steps). This keeps the state
        // machine simple and impossible to desync on chunk boundaries.
        for (i in 0 until length) step(data[i].toInt() and 0xff)
    }

    private fun step(b: Int) {
        when (state) {
            ground -> groundByte(b)
            esc -> escByte(b)
            csi -> csiByte(b)
            osc -> oscByte(b)
            charset -> state = ground
        }
    }

    // ------------------------------------------------------------------
    // GROUND
    // ------------------------------------------------------------------

    private fun groundByte(b: Int) {
        when {
            b == 0x1b -> state = esc
            b == 0x07 -> { /* BEL — silent */ }
            b == 0x08 || b == 0x7f -> screen.backspace() // BS and DEL
            b == 0x09 -> screen.tab()
            b == 0x0a || b == 0x0b || b == 0x0c -> screen.linefeed()
            b == 0x0d -> screen.carriageReturn()
            b in 0x20..0x7f -> screen.print(b)
            b < 0x20 -> { /* other C0: ignored */ }
            else -> {
                // UTF-8 multibyte sequence
                if (utf8Need > 0) {
                    if (b and 0xc0 == 0x80) {
                        utf8Buf[utf8Len++] = b.toByte()
                        utf8Need--
                        if (utf8Need == 0) {
                            val cp = decodeUtf8()
                            if (cp >= 0) screen.print(cp)
                        }
                        return
                    }
                    utf8Need = 0 // invalid continuation — retry this byte fresh
                }
                when {
                    b and 0xe0 == 0xc0 -> { utf8Buf[0] = b.toByte(); utf8Len = 1; utf8Need = 1 }
                    b and 0xf0 == 0xe0 -> { utf8Buf[0] = b.toByte(); utf8Len = 1; utf8Need = 2 }
                    b and 0xf8 == 0xf0 -> { utf8Buf[0] = b.toByte(); utf8Len = 1; utf8Need = 3 }
                    else -> { /* stray continuation byte — drop */ }
                }
            }
        }
    }

    private fun decodeUtf8(): Int {
        return try {
            val s = String(utf8Buf, 0, utf8Len, Charsets.UTF_8)
            if (s.isEmpty()) -1 else s.codePointAt(0)
        } catch (e: Exception) {
            -1
        }
    }

    // ------------------------------------------------------------------
    // ESC
    // ------------------------------------------------------------------

    private fun escByte(b: Int) {
        state = ground
        when (b.toChar()) {
            '[' -> {
                state = csi
                params.clear()
                currentParam = -1
                privateMarker = 0
                csiIntermediate = 0
            }
            ']' -> {
                state = osc
                oscBuf.setLength(0)
                oscEscape = false
            }
            '(', ')' -> state = charset // consume one charset-designator byte
            '7' -> screen.saveCursor()
            '8' -> screen.restoreCursor()
            'D' -> screen.linefeed()          // IND
            'M' -> screen.reverseIndex()      // RI
            'E' -> screen.nextLine()          // NEL
            'c' -> screen.reset()             // RIS
            '=', '>' -> { /* keypad modes: ignored in v1 */ }
        }
    }

    // ------------------------------------------------------------------
    // CSI
    // ------------------------------------------------------------------

    private fun csiByte(b: Int) {
        when {
            b in 0x30..0x39 -> { // digit
                currentParam = (if (currentParam < 0) 0 else currentParam) * 10 + (b - 0x30)
            }
            b == 0x3b || b == 0x3a -> { // ';' or ':' (sub-params flattened)
                params.add(if (currentParam < 0) 0 else currentParam)
                currentParam = -1
            }
            b in 0x3c..0x3f -> privateMarker = b // < = > ?
            b in 0x20..0x2f -> csiIntermediate = b
            b in 0x40..0x7e -> {
                if (currentParam >= 0) params.add(currentParam)
                currentParam = -1
                dispatchCsi(b.toChar())
                state = ground
            }
            else -> { /* invalid: stay in csi until a final byte */ }
        }
    }

    /** CSI params treat 0 as "default" for most commands. */
    private fun p(index: Int, default: Int): Int {
        if (index >= params.size) return default
        val v = params[index]
        return if (v == 0) default else v
    }

    private fun dispatchCsi(final: Char) {
        val privateQ = privateMarker == '?'.code
        when (final) {
            'A' -> screen.cursorUp(p(0, 1))
            'B' -> screen.cursorDown(p(0, 1))
            'C' -> screen.cursorForward(p(0, 1))
            'D' -> screen.cursorBack(p(0, 1))
            'E' -> { screen.cursorDown(p(0, 1)); screen.carriageReturn() }
            'F' -> { screen.cursorUp(p(0, 1)); screen.carriageReturn() }
            'G' -> screen.setCursorCol(p(0, 1))
            'H', 'f' -> screen.setCursorPosition(p(0, 1), p(1, 1))
            'J' -> screen.eraseInDisplay(if (params.isEmpty()) 0 else params[0])
            'K' -> screen.eraseInLine(if (params.isEmpty()) 0 else params[0])
            'L' -> screen.insertLines(p(0, 1))
            'M' -> screen.deleteLines(p(0, 1))
            'P' -> screen.deleteChars(p(0, 1))
            '@' -> screen.insertChars(p(0, 1))
            'X' -> screen.eraseChars(p(0, 1))
            'S' -> screen.scrollUp(p(0, 1))
            'T' -> screen.scrollDown(p(0, 1))
            'd' -> screen.setCursorRow(p(0, 1))
            'r' -> screen.setScrollRegion(if (params.isEmpty()) 0 else params[0], if (params.size < 2) 0 else params[1])
            's' -> screen.saveCursor()
            'u' -> screen.restoreCursor()
            'm' -> sgr()
            'n' -> {
                if (p(0, 0) == 6) {
                    // CPR: report cursor position (1-based) back to the app
                    val reply = "\u001b[" + (screen.cursorY + 1) + ";" + (screen.cursorX + 1) + "R"
                    onResponse(reply.toByteArray(Charsets.UTF_8))
                }
            }
            'h', 'l' -> {
                val set = final == 'h'
                if (privateQ) {
                    for (m in params) {
                        when (m) {
                            1049, 1047, 47 -> if (set) screen.enterAltScreen() else screen.exitAltScreen()
                            25 -> screen.cursorVisible = set
                            7 -> screen.autoWrap = set
                            2004 -> { /* bracketed paste: accepted, no-op in v1 */ }
                        }
                    }
                }
            }
            // DA, window manipulation, cursor style, etc.: parsed and ignored
        }
    }

    // ------------------------------------------------------------------
    // SGR — colors & attributes
    // ------------------------------------------------------------------

    private fun sgr() {
        if (params.isEmpty()) { resetSgr(); return }
        var i = 0
        while (i < params.size) {
            when (val v = params[i]) {
                0 -> resetSgr()
                1 -> screen.curBold = true
                3 -> screen.curItalic = true
                4 -> screen.curUnderline = true
                7 -> screen.curInverse = true
                22 -> screen.curBold = false
                23 -> screen.curItalic = false
                24 -> screen.curUnderline = false
                27 -> screen.curInverse = false
                in 30..37 -> screen.curFg = ansiColor(v - 30, false)
                in 90..97 -> screen.curFg = ansiColor(v - 90, true)
                39 -> screen.curFg = -1
                in 40..47 -> screen.curBg = ansiColor(v - 40, false)
                in 100..107 -> screen.curBg = ansiColor(v - 100, true)
                49 -> screen.curBg = -1
                38 -> {
                    val used = extendedColor(i)
                    if (used > 0) { screen.curFg = extendedValue; i += used }
                }
                48 -> {
                    val used = extendedColor(i)
                    if (used > 0) { screen.curBg = extendedValue; i += used }
                }
            }
            i++
        }
    }

    private var extendedValue = -1

    /** Parses 38;5;n / 38;2;r;g;b starting at index i; returns extra params consumed. */
    private fun extendedColor(i: Int): Int {
        if (i + 1 >= params.size) return 0
        return when (params[i + 1]) {
            5 -> {
                if (i + 2 >= params.size) return 0
                extendedValue = color256(params[i + 2].coerceIn(0, 255))
                2
            }
            2 -> {
                if (i + 4 >= params.size) return 0
                val r = params[i + 2].coerceIn(0, 255)
                val g = params[i + 3].coerceIn(0, 255)
                val b = params[i + 4].coerceIn(0, 255)
                extendedValue = (r shl 16) or (g shl 8) or b
                4
            }
            else -> 0
        }
    }

    private fun resetSgr() {
        screen.curFg = -1
        screen.curBg = -1
        screen.curBold = false
        screen.curItalic = false
        screen.curUnderline = false
        screen.curInverse = false
    }

    // ------------------------------------------------------------------
    // OSC — titles (0;/2;), everything else tolerated and dropped
    // ------------------------------------------------------------------

    private fun oscByte(b: Int) {
        if (oscEscape) {
            oscEscape = false
            if (b == 0x5c) { // ESC \ = ST terminator
                finishOsc()
                state = ground
            } else {
                // Not ST — the pending ESC starts a fresh escape sequence;
                // replay this byte through the ESC handler (e.g. ESC [ ).
                state = esc
                escByte(b)
            }
            return
        }
        when (b) {
            0x07 -> { // BEL terminates OSC
                finishOsc()
                state = ground
            }
            0x1b -> oscEscape = true
            else -> if (oscBuf.length < 4096) oscBuf.append(b.toChar())
        }
    }

    private fun finishOsc() {
        val s = oscBuf.toString()
        if (s.startsWith("0;") || s.startsWith("2;")) {
            onTitle?.invoke(s.substring(2))
        }
        oscBuf.setLength(0)
    }

    companion object {
        /** Standard xterm 256-color table entry -> 0xRRGGBB. */
        fun color256(n: Int): Int {
            if (n < 16) return ANSI_BASE[n]
            if (n < 232) {
                val v = n - 16
                val r = (v / 36) % 6
                val g = (v / 6) % 6
                val b = v % 6
                val map = intArrayOf(0, 95, 135, 175, 215, 255)
                return (map[r] shl 16) or (map[g] shl 8) or map[b]
            }
            val gray = 8 + (n - 232) * 10
            return (gray shl 16) or (gray shl 8) or gray
        }

        /** Bright flag maps to the 8 bright entries of the 256 table (8-15). */
        fun ansiColor(index: Int, bright: Boolean): Int =
            color256(index + if (bright) 8 else 0)

        /** The classic 16 (theme overlays replace these at draw time). */
        val ANSI_BASE = intArrayOf(
            0x000000, 0xcd0000, 0x00cd00, 0xcdcd00,
            0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5,
            0x7f7f7f, 0xff0000, 0x00ff00, 0xffff00,
            0x5c5cff, 0xff00ff, 0x00ffff, 0xffffff
        )
    }
}
