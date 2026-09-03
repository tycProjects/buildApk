package com.nhztech.nhzterm.ui.terminal

/**
 * Terminal screen model — the grid the VT parser mutates and the renderer
 * draws. Pure Kotlin, no Android imports: fully unit-testable, exactly
 * like nhzsh's own core stages.
 *
 * Implements the surface concept doc §10.1 requires: cursor movement,
 * erase, scroll regions, alternate screen buffer (§1049), save/restore,
 * 16/256/truecolor attributes, wide characters.
 */
class TerminalScreen(initialCols: Int, initialRows: Int) {

    class Cell {
        var ch: Int = ' '.code   // 0 = wide-char continuation cell
        var fg: Int = -1         // -1 = theme default, else 0xRRGGBB
        var bg: Int = -1
        var bold = false
        var italic = false
        var underline = false
        var inverse = false

        fun clear() {
            ch = ' '.code
            fg = -1
            bg = -1
            bold = false
            italic = false
            underline = false
            inverse = false
        }
    }

    var cols = initialCols
        private set
    var rows = initialRows
        private set

    private var main: Array<Array<Cell>> = newGrid(initialCols, initialRows)
    private var alt: Array<Array<Cell>>? = null
    private var inAltScreen = false

    private val grid: Array<Array<Cell>> get() = if (inAltScreen) alt!! else main

    var cursorX = 0
        private set
    var cursorY = 0
        private set
    var cursorVisible = true
    var autoWrap = true

    private var savedX = 0
    private var savedY = 0
    private var savedFg = -1
    private var savedBg = -1
    private var savedBold = false

    private var scrollTop = 0
    private var scrollBottom = initialRows - 1 // inclusive

    // current SGR attributes (set by parser before print)
    var curFg = -1
    var curBg = -1
    var curBold = false
    var curItalic = false
    var curUnderline = false
    var curInverse = false

    /** UI-side scrollback of lines that scrolled off the top (main screen
     *  only — the alternate screen never feeds scrollback, per xterm). */
    val scrollback = ArrayDeque<String>()
    var scrollbackOffset = 0 // lines scrolled up in the view; 0 = live

    /** Called whenever content changed; the view posts an invalidate. */
    var onChanged: (() -> Unit)? = null

    private fun newGrid(c: Int, r: Int): Array<Array<Cell>> {
        val g = Array(r) { Array(c) { Cell() } }
        return g
    }

    private fun notifyChanged() { onChanged?.invoke() }

    // ------------------------------------------------------------------
    // basic output
    // ------------------------------------------------------------------

    fun print(cp: Int) {
        val w = charWidth(cp)
        if (w == 0) return // combining marks: v1 renders base char only
        if (cursorX + w > cols) {
            if (autoWrap) {
                cursorX = 0
                linefeed()
            } else {
                cursorX = cols - w
            }
        }
        val row = grid[cursorY]
        row[cursorX].clear()
        row[cursorX].ch = cp
        row[cursorX].fg = curFg
        row[cursorX].bg = curBg
        row[cursorX].bold = curBold
        row[cursorX].italic = curItalic
        row[cursorX].underline = curUnderline
        row[cursorX].inverse = curInverse
        if (w == 2 && cursorX + 1 < cols) {
            row[cursorX + 1].clear()
            row[cursorX + 1].ch = 0 // continuation cell
            row[cursorX + 1].bg = curBg
        }
        cursorX += w
        if (cursorX > cols) cursorX = cols
        notifyChanged()
    }

    fun carriageReturn() {
        cursorX = 0
    }

    fun linefeed() {
        if (cursorY == scrollBottom) {
            scrollUp(1)
        } else if (cursorY < rows - 1) {
            cursorY++
        }
        notifyChanged()
    }

    fun reverseIndex() {
        if (cursorY == scrollTop) scrollDown(1)
        else if (cursorY > 0) cursorY--
        notifyChanged()
    }

    fun nextLine() {
        carriageReturn()
        linefeed()
    }

    fun backspace() {
        if (cursorX > 0) cursorX--
    }

    fun tab() {
        val next = ((cursorX / 8) + 1) * 8
        cursorX = if (next < cols) next else cols - 1
        if (cursorX < 0) cursorX = 0
    }

    // ------------------------------------------------------------------
    // cursor
    // ------------------------------------------------------------------

    fun cursorUp(n: Int) { cursorY = (cursorY - n).coerceAtLeast(scrollTop); notifyChanged() }
    fun cursorDown(n: Int) { cursorY = (cursorY + n).coerceAtMost(scrollBottom); notifyChanged() }
    fun cursorForward(n: Int) { cursorX = (cursorX + n).coerceAtMost(cols - 1); notifyChanged() }
    fun cursorBack(n: Int) { cursorX = (cursorX - n).coerceAtLeast(0); notifyChanged() }

    /** CUP/HVP — 1-based row;col, clamped like real terminals. */
    fun setCursorPosition(row: Int, col: Int) {
        cursorY = (row - 1).coerceIn(0, rows - 1)
        cursorX = (col - 1).coerceIn(0, cols - 1)
        notifyChanged()
    }

    fun setCursorRow(row: Int) {
        cursorY = (row - 1).coerceIn(0, rows - 1)
        notifyChanged()
    }

    fun setCursorCol(col: Int) {
        cursorX = (col - 1).coerceIn(0, cols - 1)
        notifyChanged()
    }

    fun saveCursor() {
        savedX = cursorX
        savedY = cursorY
        savedFg = curFg
        savedBg = curBg
        savedBold = curBold
    }

    fun restoreCursor() {
        cursorX = savedX.coerceIn(0, cols - 1)
        cursorY = savedY.coerceIn(0, rows - 1)
        curFg = savedFg
        curBg = savedBg
        curBold = savedBold
        notifyChanged()
    }

    // ------------------------------------------------------------------
    // erase / insert / delete
    // ------------------------------------------------------------------

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // cursor -> end of screen
                eraseInLineInternal(0)
                for (y in cursorY + 1 until rows) clearRow(y)
            }
            1 -> { // start of screen -> cursor
                eraseInLineInternal(1)
                for (y in 0 until cursorY) clearRow(y)
            }
            2 -> { // whole screen
                for (y in 0 until rows) clearRow(y)
            }
            3 -> { // whole screen + scrollback (xterm extension)
                for (y in 0 until rows) clearRow(y)
                scrollback.clear()
                scrollbackOffset = 0
            }
        }
        notifyChanged()
    }

    fun eraseInLine(mode: Int) {
        eraseInLineInternal(mode)
        notifyChanged()
    }

    private fun eraseInLineInternal(mode: Int) {
        val row = grid[cursorY]
        when (mode) {
            0 -> for (x in cursorX until cols) row[x].clear()
            1 -> for (x in 0..cursorX.coerceAtMost(cols - 1)) row[x].clear()
            2 -> for (x in 0 until cols) row[x].clear()
        }
    }

    private fun clearRow(y: Int) {
        for (x in 0 until cols) grid[y][x].clear()
    }

    fun insertLines(n: Int) {
        if (cursorY !in scrollTop..scrollBottom) return
        for (i in 0 until n) {
            pushScrollbackIfMainTop()
            for (y in scrollBottom downTo cursorY + 1) grid[y] = grid[y - 1]
            grid[cursorY] = Array(cols) { Cell() }
        }
        cursorX = 0
        notifyChanged()
    }

    fun deleteLines(n: Int) {
        if (cursorY !in scrollTop..scrollBottom) return
        for (i in 0 until n) {
            for (y in cursorY until scrollBottom) grid[y] = grid[y + 1]
            grid[scrollBottom] = Array(cols) { Cell() }
        }
        cursorX = 0
        notifyChanged()
    }

    fun insertChars(n: Int) {
        val row = grid[cursorY]
        for (x in cols - 1 downTo cursorX + n) row[x] = row[x - n]
        for (x in cursorX until (cursorX + n).coerceAtMost(cols)) row[x] = Cell()
        notifyChanged()
    }

    fun deleteChars(n: Int) {
        val row = grid[cursorY]
        for (x in cursorX until cols) {
            row[x] = if (x + n < cols) row[x + n] else Cell()
        }
        notifyChanged()
    }

    fun eraseChars(n: Int) {
        val row = grid[cursorY]
        for (x in cursorX until (cursorX + n).coerceAtMost(cols)) row[x].clear()
        notifyChanged()
    }

    // ------------------------------------------------------------------
    // scrolling
    // ------------------------------------------------------------------

    fun scrollUp(n: Int) {
        for (i in 0 until n) {
            pushScrollbackIfMainTop()
            for (y in scrollTop until scrollBottom) grid[y] = grid[y + 1]
            grid[scrollBottom] = Array(cols) { Cell() }
        }
        notifyChanged()
    }

    fun scrollDown(n: Int) {
        for (i in 0 until n) {
            for (y in scrollBottom downTo scrollTop + 1) grid[y] = grid[y - 1]
            grid[scrollTop] = Array(cols) { Cell() }
        }
        notifyChanged()
    }

    /** Lines leaving the top of the MAIN screen join UI scrollback. */
    private fun pushScrollbackIfMainTop() {
        if (inAltScreen || scrollTop != 0) return
        val text = lineText(grid[0])
        scrollback.addLast(text)
        while (scrollback.size > SCROLLBACK_VIEW_LIMIT) scrollback.removeFirst()
    }

    fun setScrollRegion(top: Int, bottom: Int) {
        // params are 1-based inclusive; 0/absent means default
        val t = (if (top <= 0) 1 else top) - 1
        val b = (if (bottom <= 0 || bottom > rows) rows else bottom) - 1
        if (t < b) {
            scrollTop = t
            scrollBottom = b
            cursorX = 0
            cursorY = t
        } else {
            resetScrollRegion()
        }
        notifyChanged()
    }

    fun resetScrollRegion() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    // ------------------------------------------------------------------
    // alternate screen (vim, htop, less — the whole point of a real PTY)
    // ------------------------------------------------------------------

    fun enterAltScreen() {
        if (inAltScreen) return
        inAltScreen = true
        alt = newGrid(cols, rows)
        scrollbackOffset = 0
        notifyChanged()
    }

    fun exitAltScreen() {
        if (!inAltScreen) return
        inAltScreen = false
        alt = null
        scrollbackOffset = 0
        notifyChanged()
    }

    fun reset() {
        exitAltScreen()
        main = newGrid(cols, rows)
        scrollback.clear()
        scrollbackOffset = 0
        cursorX = 0
        cursorY = 0
        resetScrollRegion()
        curFg = -1
        curBg = -1
        curBold = false
        curItalic = false
        curUnderline = false
        curInverse = false
        notifyChanged()
    }

    // ------------------------------------------------------------------
    // resize (cols/rows from the renderer; daemon gets session.resize)
    // ------------------------------------------------------------------

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val old = main
        val oldRows = rows
        val oldCols = cols
        main = Array(newRows) { y ->
            Array(newCols) { x ->
                if (y < oldRows && x < oldCols) old[y][x] else Cell()
            }
        }
        if (inAltScreen) alt = newGrid(newCols, newRows)
        cols = newCols
        rows = newRows
        resetScrollRegion()
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
        notifyChanged()
    }

    // ------------------------------------------------------------------
    // read access for the renderer / selection / URL scan
    // ------------------------------------------------------------------

    fun cellAt(row: Int, col: Int): Cell? {
        if (row !in 0 until rows || col !in 0 until cols) return null
        return grid[row][col]
    }

    fun lineText(row: Int): String = lineText(grid[row])

    private fun lineText(cells: Array<Cell>): String {
        val sb = StringBuilder()
        for (c in cells) {
            if (c.ch == 0) continue // wide-char continuation
            sb.appendCodePoint(c.ch)
        }
        return sb.toString().trimEnd()
    }

    /** Every visible line, for URL scanning (Ctrl+Alt+U) and share. */
    fun allText(): String {
        val sb = StringBuilder()
        for (y in 0 until rows) {
            sb.append(lineText(y))
            sb.append('\n')
        }
        return sb.toString()
    }

    fun scrollbackLine(absoluteIndex: Int): String? =
        scrollback.elementAtOrNull(absoluteIndex)

    companion object {
        const val SCROLLBACK_VIEW_LIMIT = 2000

        /** Simplified East-Asian-Width: the common wide ranges get 2 cells. */
        fun charWidth(cp: Int): Int {
            if (cp in 0x0300..0x036F || cp in 0x20D0..0x20FF) return 0 // combining
            return if (
                cp in 0x1100..0x115F ||
                cp in 0x2E80..0x303E ||
                cp in 0x3041..0x33FF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x4E00..0x9FFF ||
                cp in 0xA000..0xA4CF ||
                cp in 0xAC00..0xD7A3 ||
                cp in 0xF900..0xFAFF ||
                cp in 0xFE30..0xFE6F ||
                cp in 0xFF00..0xFF60 ||
                cp in 0xFFE0..0xFFE6 ||
                cp in 0x1F300..0x1F64F ||
                cp in 0x1F900..0x1F9FF
            ) 2 else 1
        }
    }
}
