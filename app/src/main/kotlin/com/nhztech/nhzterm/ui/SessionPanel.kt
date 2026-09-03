package com.nhztech.nhzterm.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Session side panel — concept doc §10.4. NOT optional polish: its
 * absence once produced a confirmed real-device dead-end (blank terminal,
 * no way to create a session). Requirements honored here:
 *
 *  - persistent, always reachable "+ / New Session" action
 *  - live status per session (running / idle / finished) — sessions live
 *    headlessly and may have changed state while the UI was closed
 *  - rename per session (long-press)
 *  - auto-create-on-first-launch lives in MainActivity (client side)
 *
 * Built from plain framework views: zero AndroidX, consistent with the
 * rest of the app.
 */
class SessionPanel(
    context: Context,
    private val cb: Callback
) : LinearLayout(context) {

    interface Callback {
        fun onNewSession()
        fun onSelect(sessionId: String)
        fun onRename(sessionId: String, currentName: String)
        fun onKillSession(sessionId: String)
    }

    data class Info(val id: String, val name: String, val status: String)

    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private var currentId: String? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#14141a"))
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)

        val title = TextView(context).apply {
            text = "SESSIONS"
            setTextColor(Color.parseColor("#8a8a9a"))
            textSize = 12f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val newBtn = Button(context).apply {
            text = "+  New Session"
            isAllCaps = false
            setTextColor(Color.parseColor("#e8e8f0"))
            setBackgroundColor(Color.parseColor("#23232e"))
            setTypeface(Typeface.MONOSPACE)
            setOnClickListener { cb.onNewSession() }
        }
        val btnLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        btnLp.topMargin = dp(6)
        btnLp.bottomMargin = dp(10)
        addView(newBtn, btnLp)

        val scroll = ScrollView(context)
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setCurrent(id: String?) {
        currentId = id
    }

    fun update(sessions: List<Info>) {
        list.removeAllViews()
        for (s in sessions) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setBackgroundColor(
                    if (s.id == currentId) Color.parseColor("#26263a") else Color.TRANSPARENT
                )
            }

            val dot = TextView(context).apply {
                text = "●"
                textSize = 10f
                setTextColor(
                    when (s.status) {
                        "running" -> Color.parseColor("#4caf50")
                        "idle" -> Color.parseColor("#ffb300")
                        else -> Color.parseColor("#616161")
                    }
                )
            }
            row.addView(dot, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

            val label = TextView(context).apply {
                text = "  " + s.name + "\n  " + s.status
                setTextColor(Color.parseColor("#d8d8e0"))
                textSize = 13f
                setTypeface(Typeface.MONOSPACE)
            }
            row.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            row.setOnClickListener { cb.onSelect(s.id) }
            row.setOnLongClickListener {
                cb.onRename(s.id, s.name)
                true
            }

            val kill = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#ef5350"))
                textSize = 14f
                setPadding(dp(10), dp(4), dp(4), dp(4))
                setOnClickListener { cb.onKillSession(s.id) }
            }
            row.addView(kill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(2)
            list.addView(row, lp)
        }
        if (sessions.isEmpty()) {
            val empty = TextView(context).apply {
                text = "no sessions — tap + to create one"
                setTextColor(Color.parseColor("#6a6a7a"))
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                setPadding(dp(8), dp(16), dp(8), dp(8))
            }
            list.addView(empty)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
