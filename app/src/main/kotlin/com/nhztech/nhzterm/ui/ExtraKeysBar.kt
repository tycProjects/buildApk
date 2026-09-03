package com.nhztech.nhzterm.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * Bottom extra-keys bar — concept doc §10.6, EXACTLY as specified:
 *
 *   Row 1 (8 keys): ≡ ESC TAB CTRL ALT HOME ↑ END
 *   Row 2 (7 keys): / - PGUP PGDN ← ↓ →
 *
 * Both rows must render their full set — a row silently missing a key is
 * a bug, not a style variation (this exact bug shipped once already).
 * Keys are equal-weight blocks across the full width (Material Button
 * minimums zeroed — they otherwise smear into one strip).
 * The ≡ key is WIRED both ways: tap opens the session side panel
 * (§10.4), long-press toggles the extra-keys rows — it must never be an
 * inert icon.
 */
class ExtraKeysBar(
    context: Context,
    private val cb: Callback
) : LinearLayout(context) {

    interface Callback {
        fun sendBytes(vararg b: Int)
        fun sendText(s: String)
        fun onCtrlToggle(active: Boolean)
        fun onAltToggle(active: Boolean)
        fun onToggleExtraKeys()
        fun onOpenSessionPanel()
    }

    var ctrlActive = false
        private set
    var altActive = false
        private set

    /** False = rows hidden (≡ toggled them away); the ≡ key itself stays. */
    var rowsVisible = true
        private set

    private val row1 = LinearLayout(context)
    private val row2 = LinearLayout(context)
    private val hamburger = makeKey("≡")

    // Declared BEFORE init — Kotlin initializes properties in declaration
    // order, and the init block references this strip.
    private val soloStrip = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setBackgroundColor(Color.parseColor("#101014"))
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#101014"))

        row1.orientation = HORIZONTAL
        row2.orientation = HORIZONTAL

        // Row 1 — the ≡ key is real and wired (see class doc)
        row1.addView(hamburger, keyParams())
        row1.addView(makeKey("ESC") { cb.sendBytes(0x1b) }, keyParams())
        row1.addView(makeKey("TAB") { cb.sendBytes(0x09) }, keyParams())
        val ctrl = makeKey("CTRL") {
            ctrlActive = !ctrlActive
            if (ctrlActive) altActive = false
            refreshModifierVisuals()
            cb.onCtrlToggle(ctrlActive)
        }
        val alt = makeKey("ALT") {
            altActive = !altActive
            if (altActive) ctrlActive = false
            refreshModifierVisuals()
            cb.onAltToggle(altActive)
        }
        row1.addView(ctrl, keyParams())
        row1.addView(alt, keyParams())
        row1.addView(makeKey("HOME") { cb.sendBytes(0x1b, 0x5b, 0x48) }, keyParams())
        row1.addView(makeKey("↑") { cb.sendBytes(0x1b, 0x5b, 0x41) }, keyParams())
        row1.addView(makeKey("END") { cb.sendBytes(0x1b, 0x5b, 0x46) }, keyParams())

        // Row 2
        row2.addView(makeKey("/") { cb.sendText("/") }, keyParams())
        row2.addView(makeKey("-") { cb.sendText("-") }, keyParams())
        row2.addView(makeKey("PGUP") { cb.sendBytes(0x1b, 0x5b, 0x35, 0x7e) }, keyParams())
        row2.addView(makeKey("PGDN") { cb.sendBytes(0x1b, 0x5b, 0x36, 0x7e) }, keyParams())
        row2.addView(makeKey("←") { cb.sendBytes(0x1b, 0x5b, 0x44) }, keyParams())
        row2.addView(makeKey("↓") { cb.sendBytes(0x1b, 0x5b, 0x42) }, keyParams())
        row2.addView(makeKey("→") { cb.sendBytes(0x1b, 0x5b, 0x43) }, keyParams())

        addView(row1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(row2, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // ≡ is WIRED both ways (§10.6 — it must never be inert):
        //   tap      = open/close the session side panel (§10.4)
        //   long-press = toggle the extra-keys rows (conventional Termux)
        hamburger.setOnClickListener {
            cb.onOpenSessionPanel()
        }
        hamburger.setOnLongClickListener {
            toggleRowsInternal()
            true
        }

        soloStrip.addView(makeKey("≡") { toggleRowsInternal() }, keyParams())
        addView(soloStrip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        soloStrip.visibility = GONE

        // hard sanity check of the spec — a missing key is a build-time bug
        check(row1.childCount == 8) { "Row 1 must have exactly 8 keys (concept §10.6)" }
        check(row2.childCount == 7) { "Row 2 must have exactly 7 keys (concept §10.6)" }
    }

    private fun keyParams(): LayoutParams {
        val p = LayoutParams(0, dp(38), 1f)
        p.setMargins(dp(2), dp(2), dp(2), dp(2))
        return p
    }

    private fun makeKey(label: String, action: (() -> Unit)? = null): Button {
        val b = Button(context)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12f
        b.setTypeface(Typeface.MONOSPACE)
        b.setTextColor(Color.parseColor("#d8d8d8"))
        b.setBackgroundColor(Color.parseColor("#1d1d24"))
        b.gravity = Gravity.CENTER
        b.setPadding(0, 0, 0, 0)
        // Material Buttons default to minWidth=88dp / minHeight=48dp —
        // 8 of those blow past any phone's width and smear the row into
        // one unaligned strip (confirmed on a real device). Zero them so
        // the weight=1 layout params are the ONLY thing sizing each key:
        // equal per-key blocks, perfectly aligned.
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.stateListAnimator = null
        if (action != null) b.setOnClickListener { action() }
        return b
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun refreshModifierVisuals() {
        // Row 1 order: 0:≡ 1:ESC 2:TAB 3:CTRL 4:ALT 5:HOME 6:↑ 7:END
        val ctrl = row1.getChildAt(3) as Button
        val alt = row1.getChildAt(4) as Button
        ctrl.setBackgroundColor(if (ctrlActive) Color.parseColor("#3d5afe") else Color.parseColor("#1d1d24"))
        alt.setBackgroundColor(if (altActive) Color.parseColor("#3d5afe") else Color.parseColor("#1d1d24"))
    }

    /** Clear both modifier toggles (after the shell consumed them). */
    fun clearModifiers() {
        ctrlActive = false
        altActive = false
        refreshModifierVisuals()
    }

    /** Rows toggle — long-press ≡, solo-strip tap, Vol Up+Q/K (§10.7). */
    fun toggleRows() {
        toggleRowsInternal()
    }

    private fun toggleRowsInternal() {
        rowsVisible = !rowsVisible
        row1.visibility = if (rowsVisible) VISIBLE else GONE
        row2.visibility = if (rowsVisible) VISIBLE else GONE
        // when the rows are hidden, keep a minimal strip with just ≡ so
        // they can always be brought back — never a dead end
        soloStrip.visibility = if (rowsVisible) GONE else VISIBLE
        cb.onToggleExtraKeys()
    }
}
