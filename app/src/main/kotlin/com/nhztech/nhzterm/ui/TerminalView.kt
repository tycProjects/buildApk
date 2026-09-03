package com.nhztech.nhzterm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.nhztech.nhzterm.ui.terminal.TerminalScreen
import com.nhztech.nhzterm.ui.terminal.VtParser

/**
 * The terminal renderer — concept doc §10.1: a custom native View drawing
 * terminal output directly to a hardware-accelerated Canvas. No WebView,
 * no JavaScript runtime, no browser engine anywhere in this stack.
 *
 * Responsibilities: glyph drawing with theme colors, cursor, touch
 * selection + long-press (§10.3 hook), scrollback panning, soft-keyboard
 * input connection (with CTRL/ALT modifiers from the extra-keys bar), and
 * resize reporting so the daemon can resize the real PTY.
 */
class TerminalView(context: Context) : android.view.View(context) {

    val screen = TerminalScreen(80, 24)

    /** Bytes to send to the session (escape sequences, text, control codes). */
    var onSendBytes: ((ByteArray) -> Unit)? = null

    /** Long-press fired with the touched cell (row, col) — the Activity
     *  opens the §10.3 context menu anchored at that point. */
    var onLongPress: ((x: Float, y: Float, row: Int, col: Int) -> Unit)? = null

    /** Fired when the layout gives us a new cols/rows geometry. */
    var onGeometryChanged: ((cols: Int, rows: Int) -> Unit)? = null

    /** Termux-style pinch-to-zoom: fired when a pinch ends, with the new
     *  zoom factor (textSizePx / baseTextSizePx) so the Activity can
     *  persist it. Ctrl+Alt +/- (§10.9) goes through the same pair. */
    var onZoomChanged: ((zoom: Float) -> Unit)? = null

    /** Zoom 1.0 reference size; pinch clamps to 0.4x..4.0x of this. */
    var baseTextSizePx: Float = dp(13f)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                textSizePx = (textSizePx * detector.scaleFactor)
                    .coerceIn(baseTextSizePx * 0.4f, baseTextSizePx * 4.0f)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                onZoomChanged?.invoke(textSizePx / baseTextSizePx)
            }
        }
    )

    var theme: Theme = Theme.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    var textSizePx: Float = dp(13f)
        set(value) {
            field = value
            textPaint.textSize = value
            boldPaint.textSize = value
            measureFont()
            requestLayout()
            invalidate()
        }

    var typeface: Typeface = Typeface.MONOSPACE
        set(value) {
            field = value
            textPaint.typeface = value
            boldPaint.typeface = Typeface.create(value, Typeface.BOLD)
            measureFont()
            requestLayout()
            invalidate()
        }

    /** Modifiers driven by the extra-keys bar's CTRL/ALT toggles. */
    var ctrlModifier = false
    var altModifier = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.MONOSPACE
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bgPaint = Paint()
    private val cursorPaint = Paint()

    private var charW = 1f
    private var charH = 1f
    private var baselineOffset = 0f

    // selection (cell coordinates, inclusive rect)
    private var selAnchorRow = -1
    private var selAnchorCol = -1
    private var selActiveRow = -1
    private var selActiveCol = -1

    private val handler = Handler(Looper.getMainLooper())
    private var longPressPending = false
    private val longPressRunnable = Runnable {
        longPressPending = false
        val pos = lastTouch
        if (pos != null) {
            val col = (pos.first / charW).toInt().coerceIn(0, screen.cols - 1)
            val row = (pos.second / charH).toInt().coerceIn(0, screen.rows - 1)
            startSelection(row, col)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke(pos.first, pos.second, row, col)
        }
    }
    private var lastTouch: Pair<Float, Float>? = null
    private var cursorVisible = true
    private val cursorBlink = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            handler.postDelayed(this, 530)
        }
    }

    val parser = VtParser(
        screen,
        onResponse = { bytes -> onSendBytes?.invoke(bytes) }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        measureFont()
        screen.onChanged = { postInvalidate() }
        handler.post(cursorBlink)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun measureFont() {
        charW = textPaint.measureText("M")
        if (charW <= 0f) charW = dp(8f)
        val fm = textPaint.fontMetrics
        charH = (fm.descent - fm.ascent) * 1.05f
        baselineOffset = -fm.ascent
    }

    /** Feed raw PTY bytes from the daemon into the emulator. */
    fun feed(data: ByteArray) {
        parser.feed(data)
    }

    fun clearAll() {
        screen.reset()
        invalidate()
    }

    // ------------------------------------------------------------------
    // layout & drawing
    // ------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = (w / charW).toInt().coerceAtLeast(2)
        val rows = (h / charH).toInt().coerceAtLeast(2)
        if (cols != screen.cols || rows != screen.rows) {
            screen.resize(cols, rows)
            onGeometryChanged?.invoke(cols, rows)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val t = theme
        canvas.drawColor(t.background)

        val offset = screen.scrollbackOffset
        if (offset == 0) {
            drawLiveScreen(canvas, t)
        } else {
            drawScrollback(canvas, t, offset)
        }
    }

    private fun drawLiveScreen(canvas: Canvas, t: Theme) {
        for (row in 0 until screen.rows) {
            val y = row * charH
            var col = 0
            while (col < screen.cols) {
                val cell = screen.cellAt(row, col) ?: break
                val cp = cell.ch
                val widthCells = if (cp != 0 && TerminalScreen.charWidth(cp) == 2) 2 else 1

                var fg = if (cell.fg >= 0) cell.fg else t.foreground
                var bg = if (cell.bg >= 0) cell.bg else t.background
                if (cell.inverse) {
                    val tmp = fg; fg = bg; bg = tmp
                }
                if (cell.bold && cell.fg in 0..7) {
                    // bold brightens the 8 base colors when the theme has no bold face
                    fg = t.brighten(fg)
                }

                if (bg != t.background || isInSelection(row, col)) {
                    bgPaint.color = 0xff000000.toInt() or (if (isInSelection(row, col)) t.selection else bg)
                    canvas.drawRect(col * charW, y, (col + widthCells) * charW, y + charH, bgPaint)
                }

                if (cp != 0 && cp != ' '.code) {
                    val paint = if (cell.bold) boldPaint else textPaint
                    paint.color = 0xff000000.toInt() or fg
                    val s = String(Character.toChars(cp))
                    canvas.drawText(s, col * charW, y + baselineOffset, paint)
                    if (cell.underline) {
                        canvas.drawLine(col * charW, y + charH - dp(2f), (col + widthCells) * charW, y + charH - dp(2f), paint)
                    }
                }
                col += widthCells
            }
        }

        if (screen.cursorVisible && cursorVisible) {
            cursorPaint.color = 0xff000000.toInt() or t.cursor
            val x = screen.cursorX * charW
            val y = screen.cursorY * charH
            canvas.drawRect(x, y, x + charW, y + charH, cursorPaint)
        }
    }

    /** Scrolled-up view: plain text from UI scrollback (styling is live-view only). */
    private fun drawScrollback(canvas: Canvas, t: Theme, offset: Int) {
        val total = screen.scrollback.size
        val start = (total - offset).coerceAtLeast(0)
        var drawRow = 0
        for (i in start until total) {
            if (drawRow >= screen.rows) break
            val line = screen.scrollback.elementAtOrNull(i) ?: continue
            textPaint.color = 0xff000000.toInt() or t.foreground
            val clipped = if (line.length > screen.cols) line.substring(0, screen.cols) else line
            canvas.drawText(clipped, 0f, drawRow * charH + baselineOffset, textPaint)
            drawRow++
        }
    }

    // ------------------------------------------------------------------
    // selection / touch
    // ------------------------------------------------------------------

    private fun startSelection(row: Int, col: Int) {
        selAnchorRow = row
        selAnchorCol = col
        selActiveRow = row
        selActiveCol = col
        screen.scrollbackOffset = 0
        invalidate()
    }

    private fun isInSelection(row: Int, col: Int): Boolean {
        if (selAnchorRow < 0) return false
        val r1 = minOf(selAnchorRow, selActiveRow)
        val r2 = maxOf(selAnchorRow, selActiveRow)
        if (row < r1 || row > r2) return false
        val c1: Int
        val c2: Int
        if (selAnchorRow == selActiveRow) {
            c1 = minOf(selAnchorCol, selActiveCol)
            c2 = maxOf(selAnchorCol, selActiveCol)
        } else if (row == r1) {
            c1 = if (selAnchorRow < selActiveRow) selAnchorCol else selActiveCol
            c2 = screen.cols - 1
        } else if (row == r2) {
            c1 = 0
            c2 = if (selAnchorRow < selActiveRow) selActiveCol else selAnchorCol
        } else {
            return true
        }
        return col in c1..c2
    }

    fun hasSelection(): Boolean = selAnchorRow >= 0

    fun selectedText(): String {
        if (!hasSelection()) return ""
        val sb = StringBuilder()
        val r1 = minOf(selAnchorRow, selActiveRow)
        val r2 = maxOf(selAnchorRow, selActiveRow)
        for (row in r1..r2) {
            val line = StringBuilder()
            for (col in 0 until screen.cols) {
                if (isInSelection(row, col)) {
                    val cell = screen.cellAt(row, col)
                    if (cell != null && cell.ch != 0) line.appendCodePoint(cell.ch)
                }
            }
            sb.append(line.toString().trimEnd())
            if (row != r2) sb.append('\n')
        }
        return sb.toString()
    }

    fun clearSelection() {
        selAnchorRow = -1
        invalidate()
    }

    fun copySelectionToClipboard() {
        val text = selectedText()
        if (text.isEmpty()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nhzterm", text))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Pinch-to-zoom wins over selection/long-press while in progress
        // (Termux behavior).
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            handler.removeCallbacks(longPressRunnable)
            longPressPending = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch = Pair(event.x, event.y)
                lastDownY = event.y
                longPressPending = true
                handler.postDelayed(longPressRunnable, 450)
                requestFocus()
                showSoftKeyboard()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // second finger = gesture, not a long-press
                handler.removeCallbacks(longPressRunnable)
                longPressPending = false
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouch = Pair(event.x, event.y)
                if (longPressPending) {
                    handler.removeCallbacks(longPressRunnable)
                    longPressPending = false
                }
                if (selAnchorRow >= 0) {
                    selActiveRow = (event.y / charH).toInt().coerceIn(0, screen.rows - 1)
                    selActiveCol = (event.x / charW).toInt().coerceIn(0, screen.cols - 1)
                    invalidate()
                } else if (screen.scrollback.size > 0) {
                    // one-finger vertical drag pans the scrollback
                    val delta = ((event.y - (lastDownY ?: event.y)) / charH).toInt()
                    if (delta != 0) {
                        screen.scrollbackOffset =
                            (screen.scrollbackOffset + delta).coerceIn(0, screen.scrollback.size)
                        lastDownY = event.y
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                longPressPending = false
                if (event.actionMasked == MotionEvent.ACTION_UP && screen.scrollbackOffset == 0) {
                    // plain tap returns to live view
                }
            }
        }
        return true
    }

    private var lastDownY: Float? = null

    // ------------------------------------------------------------------
    // input: soft keyboard + hardware keys → bytes to the session
    // ------------------------------------------------------------------

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return TerminalInputConnection(this)
    }

    /** Apply CTRL/ALT modifiers (extra-keys bar) and emit bytes. */
    fun sendText(text: String) {
        val bytes = mutableListOf<Byte>()
        for (cp in text.codePoints().toArray()) {
            var c = cp
            if (ctrlModifier && c in 'a'.code..'z'.code) {
                bytes.add((c - 'a'.code + 1).toByte())
            } else if (ctrlModifier && c in 'A'.code..'Z'.code) {
                bytes.add((c - 'A'.code + 1).toByte())
            } else {
                if (altModifier) bytes.add(0x1b)
                val enc = String(Character.toChars(c)).toByteArray(Charsets.UTF_8)
                for (b in enc) bytes.add(b)
            }
        }
        ctrlModifier = false
        altModifier = false
        onModifierConsumed?.invoke()
        if (bytes.isNotEmpty()) onSendBytes?.invoke(bytes.toByteArray())
    }

    fun sendBytes(vararg b: Int) {
        onSendBytes?.invoke(ByteArray(b.size) { b[it].toByte() })
    }

    /** Fired after CTRL/ALT were consumed so the bar can update visuals. */
    var onModifierConsumed: (() -> Unit)? = null

    fun showSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        // Hardware-key handling is centralized in MainActivity.dispatchKeyEvent.
        return super.onKeyPreIme(keyCode, event)
    }

    private class TerminalInputConnection(private val view: TerminalView) :
        BaseInputConnection(view, false) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!text.isNullOrEmpty()) view.sendText(text.toString())
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) view.sendBytes(0x7f)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> view.sendBytes(0x0d)
                    KeyEvent.KEYCODE_DEL -> view.sendBytes(0x7f)
                    KeyEvent.KEYCODE_TAB -> view.sendBytes(0x09)
                    KeyEvent.KEYCODE_DPAD_UP -> view.sendBytes(0x1b, 0x5b, 0x41)
                    KeyEvent.KEYCODE_DPAD_DOWN -> view.sendBytes(0x1b, 0x5b, 0x42)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> view.sendBytes(0x1b, 0x5b, 0x43)
                    KeyEvent.KEYCODE_DPAD_LEFT -> view.sendBytes(0x1b, 0x5b, 0x44)
                    else -> {
                        val c = event.unicodeChar
                        if (c != 0) view.sendText(String(Character.toChars(c)))
                    }
                }
            }
            return true
        }
    }

    /** A no-op EditText keeps IMEs from demanding a real editor field. */
    @Suppress("unused")
    private fun imeAnchor(): EditText = EditText(context)

    /** Reset helper used by the Refresh menu item (§10.3). */
    fun scrollToEnd() {
        screen.scrollbackOffset = 0
        invalidate()
    }

    /** Convenience for tests/tools: dump visible text. */
    fun dumpVisible(): String = screen.allText()

    /** Unused rect kept for future image-region clipping (§10.5). */
    @Suppress("unused")
    private val tmpRect = Rect()
}
