package com.nhztech.nhzterm.daemon

/**
 * Per-session scrollback — concept doc §8: ~5,000 lines, ring buffer,
 * bounded so memory per session stays predictable. Replayed in full on
 * session.attach (§6.3), then live streaming continues after the replay.
 *
 * Lines are kept exactly as the PTY produced them (escape sequences and
 * all) — replaying through the client's terminal emulator reconstructs
 * the screen truthfully.
 */
class ScrollbackBuffer(private val capacity: Int) {

    private val lines = ArrayDeque<String>()
    private var partial = StringBuilder()

    @Synchronized
    fun append(text: String) {
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                partial.append(text, start, i)
                pushLine(partial.toString())
                partial = StringBuilder()
                start = i + 1
            }
        }
        if (start < text.length) partial.append(text, start, text.length)
    }

    private fun pushLine(line: String) {
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun replay(): String {
        val sb = StringBuilder()
        for (l in lines) {
            sb.append(l)
            sb.append('\n')
        }
        if (partial.isNotEmpty()) sb.append(partial)
        return sb.toString()
    }

    @Synchronized
    fun lineCount(): Int = lines.size + if (partial.isNotEmpty()) 1 else 0
}
