package com.nhztech.nhzterm.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.nhztech.nhzterm.api.NhztermApi

/**
 * nhzterm reference UI — concept doc §10. One Activity, plain framework
 * views, no fragments/libraries: terminal renderer + session drawer +
 * extra-keys bar, talking to nhztermd exclusively through nhzterm-api
 * (same LocalSocket + token path as any external client — §3, no fast
 * lane).
 */
class MainActivity : Activity() {

    private lateinit var api: NhztermApi
    private lateinit var settings: SettingsStore
    private lateinit var root: FrameLayout
    private lateinit var terminal: TerminalView
    private lateinit var extraKeys: ExtraKeysBar
    private lateinit var panel: SessionPanel
    private lateinit var content: android.widget.LinearLayout

    private var currentSessionId: String? = null
    private val sessionStatuses = HashMap<String, Pair<String, Int>>() // id -> (status, pid)
    private val sessionNames = HashMap<String, String>()

    // volume-key modifier state (§10.7)
    private var volDownCtrl = false
    private var volUpActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = SettingsStore(this)
        api = NhztermApi(this)

        terminal = TerminalView(this)
        extraKeys = ExtraKeysBar(this, extraKeysCallback())
        panel = SessionPanel(this, panelCallback())

        content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        content.addView(
            terminal,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        content.addView(
            extraKeys,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val panelLp = FrameLayout.LayoutParams(dp(260), FrameLayout.LayoutParams.MATCH_PARENT)
        panelLp.gravity = Gravity.START
        panel.translationX = -dp(260).toFloat()
        root.addView(panel, panelLp)
        setContentView(root)

        applyAppearance()
        applyKeepScreenOn()

        terminal.onSendBytes = { bytes -> currentSessionId?.let { api.sessionWrite(it, bytes) } }
        terminal.onLongPress = { x, y, _, _ -> showContextMenu(x, y) }
        terminal.onGeometryChanged = { cols, rows ->
            currentSessionId?.let { api.sessionResize(it, cols, rows, { _, _, _ -> }) }
        }
        terminal.onModifierConsumed = { extraKeys.clearModifiers() }
        // Termux-style pinch-to-zoom: persist the factor the gesture landed on
        terminal.onZoomChanged = { z -> settings.textZoom = z.coerceIn(0.4f, 4.0f) }

        api.listener = apiListener()
        requestNotificationPermissionIfNeeded()
        connect()
    }

    // ------------------------------------------------------------------
    // daemon connection + session lifecycle
    // ------------------------------------------------------------------

    private fun connect() {
        api.connect(
            onReady = { onApiReady() },
            onError = { msg ->
                toast("nhztermd unreachable: $msg")
                // keep trying — the daemon may still be cold-starting
                terminal.postDelayed({ connect() }, 2000)
            }
        )
    }

    private fun onApiReady() {
        // §10.4: auto-create on first launch — an empty state must never
        // be a dead end. Zero sessions -> create one and attach.
        api.sessionList { ok, result, _ ->
            if (!ok || result == null) return@sessionList
            val arr = result.optJSONArray("sessions")
            refreshPanelFromList(arr)
            if (arr == null || arr.length() == 0) {
                createAndAttach()
            } else {
                val first = arr.getJSONObject(0).optString("session_id")
                attachTo(first)
            }
        }
    }

    private fun createAndAttach() {
        api.sessionCreate(null, null) { ok, result, err ->
            if (ok && result != null) {
                attachTo(result.optString("session_id"))
            } else {
                toast("session.create failed: $err")
            }
        }
    }

    private fun attachTo(sessionId: String) {
        api.sessionAttach(sessionId) { ok, result, err ->
            if (!ok || result == null) {
                toast("attach failed: $err")
                return@sessionAttach
            }
            currentSessionId = sessionId
            panel.setCurrent(sessionId)
            terminal.clearAll()
            val scrollback = result.optString("scrollback", "")
            if (scrollback.isNotEmpty()) {
                terminal.feed(Base64.decode(scrollback, Base64.DEFAULT))
            }
            refreshPanel()
            terminal.requestFocus()
            terminal.showSoftKeyboard()
        }
    }

    private fun apiListener(): NhztermApi.Listener = object : NhztermApi.Listener {
        override fun onOutput(sessionId: String, data: ByteArray) {
            if (sessionId == currentSessionId) terminal.feed(data)
        }

        override fun onSessionStatusChanged(sessionId: String, status: String, pid: Int) {
            sessionStatuses[sessionId] = Pair(status, pid)
            refreshPanel()
        }

        override fun onServerError(code: String, message: String) {
            toast("daemon: $code $message")
        }

        override fun onDisconnected() {
            toast("disconnected from nhztermd — reconnecting")
            terminal.postDelayed({ connect() }, 1500)
        }
    }

    private fun refreshPanel() {
        api.sessionList { ok, result, _ ->
            if (ok && result != null) refreshPanelFromList(result.optJSONArray("sessions"))
        }
    }

    private fun refreshPanelFromList(arr: org.json.JSONArray?) {
        val infos = mutableListOf<SessionPanel.Info>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("session_id")
                val name = o.optString("name", id)
                sessionNames[id] = name
                val live = sessionStatuses[id]
                infos.add(SessionPanel.Info(id, name, live?.first ?: o.optString("status", "idle")))
            }
        }
        panel.update(infos)
    }

    // ------------------------------------------------------------------
    // callbacks: extra keys + session panel
    // ------------------------------------------------------------------

    private fun extraKeysCallback(): ExtraKeysBar.Callback = object : ExtraKeysBar.Callback {
        override fun sendBytes(vararg b: Int) {
            applyModifiersAndSend(b)
        }

        override fun sendText(s: String) {
            applyModifiersAndSend(s.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff }.toIntArray())
        }

        override fun onCtrlToggle(active: Boolean) {
            terminal.ctrlModifier = active // IME text path uses this flag
        }

        override fun onAltToggle(active: Boolean) {
            terminal.altModifier = active
        }

        override fun onToggleExtraKeys() {}
        override fun onOpenSessionPanel() { togglePanel() }
    }

    private fun applyModifiersAndSend(bytes: IntArray) {
        val out = mutableListOf<Int>()
        if (extraKeys.altActive) out.add(0x1b)
        for (b in bytes) {
            if (extraKeys.ctrlActive && b in 'a'.code..'z'.code) out.add(b - 'a'.code + 1)
            else if (extraKeys.ctrlActive && b in 'A'.code..'Z'.code) out.add(b - 'A'.code + 1)
            else out.add(b)
        }
        if (extraKeys.ctrlActive || extraKeys.altActive) {
            extraKeys.clearModifiers()
            terminal.ctrlModifier = false
            terminal.altModifier = false
        }
        terminal.sendBytes(*out.toIntArray())
    }

    private fun panelCallback(): SessionPanel.Callback = object : SessionPanel.Callback {
        override fun onNewSession() {
            createAndAttach()
            togglePanel(forceClose = true)
        }

        override fun onSelect(sessionId: String) {
            attachTo(sessionId)
            togglePanel(forceClose = true)
        }

        override fun onRename(sessionId: String, currentName: String) {
            promptRename(sessionId, currentName)
        }

        override fun onKillSession(sessionId: String) {
            api.sessionKill(sessionId) { ok, _, _ ->
                if (ok) {
                    if (sessionId == currentSessionId) {
                        currentSessionId = null
                        terminal.clearAll()
                        // §10.4: never a dead end — re-list, auto-create if empty
                        onApiReady()
                    }
                    refreshPanel()
                }
            }
        }
    }

    private fun togglePanel(forceClose: Boolean = false) {
        val closed = panel.translationX < 0
        if (forceClose && !closed) {
            panel.animate().translationX(-dp(260).toFloat()).setDuration(150).start()
        } else {
            panel.animate()
                .translationX(if (closed) 0f else -dp(260).toFloat())
                .setDuration(150)
                .start()
        }
        if (closed) refreshPanel()
    }

    /** Back closes the side panel first; only then leaves the app. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (panel.translationX >= 0f) togglePanel(forceClose = true)
        else super.onBackPressed()
    }

    private fun promptRename(sessionId: String, currentName: String) {
        val input = EditText(this)
        input.setText(currentName)
        input.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(this)
            .setTitle("Rename session")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    api.sessionRename(sessionId, name) { _, _, _ -> refreshPanel() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------
    // long-press context menu — §10.3, exact item list
    // ------------------------------------------------------------------

    private fun showContextMenu(x: Float, y: Float) {
        val anchor = terminal
        val menu = PopupMenu(this, anchor, Gravity.TOP or Gravity.START)
        val m = menu.menu
        m.add(0, 1, 0, "Copy")
        m.add(0, 2, 0, "Paste")
        val selected = terminal.selectedText()
        val url = extractUrl(selected)
        if (url != null) m.add(0, 3, 0, "Open") // conditional per §10.3
        val more = m.addSubMenu(0, 100, 0, "More →")
        if (url != null) more.add(0, 101, 0, "Open URL")
        more.add(0, 102, 0, "Share Selected Text")
        more.add(0, 103, 0, "Refresh")
        more.add(0, 104, 0, "Kill Process (PID)")
        more.add(0, 105, 0, "Style")
        more.add(0, 106, 0, "Keep Screen On")
        more.add(0, 107, 0, "Help")
        more.add(0, 108, 0, "Settings")
        more.add(0, 109, 0, "Report Issue")

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { terminal.copySelectionToClipboard(); toast("copied"); true }
                2 -> { pasteFromClipboard(); true }
                3 -> { openUrl(url); true }
                101 -> { openUrl(url); true }
                102 -> { shareText(selected.ifEmpty { terminal.dumpVisible() }); true }
                103 -> { refreshTerminal(); true }
                104 -> { killProcess(); true }
                105 -> { showStylePicker(); true }
                106 -> { toggleKeepScreenOn(); true }
                107 -> { showHelp(); true }
                108 -> { showSettings(); true }
                109 -> { reportIssue(); true }
                else -> false
            }
        }
        try {
            menu.show()
        } catch (ignored: Exception) {
        }
    }

    /** §9/§10.3 Kill Process: targets the session's tracked foreground PID;
     *  nothing running -> honest message, never a dead tap. */
    private fun killProcess() {
        val sid = currentSessionId ?: return
        val pid = sessionStatuses[sid]?.second ?: -1
        if (pid <= 0) {
            toast("nothing is currently running")
            return
        }
        api.processKill(pid) { ok, _, err ->
            toast(if (ok) "sent kill to pid $pid" else "kill failed: $err")
        }
    }

    private fun refreshTerminal() {
        // §10.3 Refresh: recover the VIEW without killing the session —
        // re-request scrollback and replay it.
        val sid = currentSessionId ?: return
        api.sessionAttach(sid) { ok, result, _ ->
            if (ok && result != null) {
                terminal.clearAll()
                val sb = result.optString("scrollback", "")
                if (sb.isNotEmpty()) terminal.feed(Base64.decode(sb, Base64.DEFAULT))
                toast("view refreshed")
            }
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        val text = clip.getItemAt(0)?.text?.toString() ?: return
        currentSessionId?.let { api.sessionWrite(it, text.toByteArray(Charsets.UTF_8)) }
    }

    // ------------------------------------------------------------------
    // style / settings / help / report
    // ------------------------------------------------------------------

    private fun showStylePicker() {
        val themes = ThemeRegistry.loadAll(this, themesDir())
        val fonts = FontRegistry.FONTS
        val themeNames = themes.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setItems(themeNames) { _, which ->
                settings.themeName = themeNames[which]
                applyAppearance()
                pickFont(fonts)
            }
            .setNeutralButton("Fonts…") { _, _ -> pickFont(fonts) }
            .show()
    }

    private fun pickFont(fonts: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Font")
            .setItems(fonts.toTypedArray()) { _, which ->
                settings.fontName = fonts[which]
                applyAppearance()
            }
            .show()
    }

    private fun themesDir(): java.io.File? {
        return try {
            java.io.File(filesDir, "etc/themes")
        } catch (e: Exception) {
            null
        }
    }

    private fun applyAppearance() {
        val themes = ThemeRegistry.loadAll(this, themesDir())
        val theme = themes.firstOrNull { it.name == settings.themeName } ?: Theme.DEFAULT
        terminal.theme = theme
        terminal.typeface = FontRegistry.resolve(this, settings.fontName)
        terminal.baseTextSizePx = 13f * resources.displayMetrics.density
        terminal.textSizePx = terminal.baseTextSizePx * settings.textZoom
    }

    private fun toggleKeepScreenOn() {
        settings.keepScreenOn = !settings.keepScreenOn
        applyKeepScreenOn()
        toast("keep screen on: " + if (settings.keepScreenOn) "yes" else "no")
    }

    private fun applyKeepScreenOn() {
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showSettings() {
        val items = arrayOf(
            "Wake lock (long builds, screen off) — " + if (settings.wakeLock) "ON" else "OFF",
            "Text size — zoom " + String.format("%.1fx", settings.textZoom),
            "Toggle soft keyboard"
        )
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        settings.wakeLock = !settings.wakeLock
                        toast("wake lock " + if (settings.wakeLock) "enabled (daemon picks it up on next activity)" else "disabled")
                        showSettings()
                    }
                    1 -> {
                        settings.textZoom = if (settings.textZoom >= 1.6f) 0.8f else settings.textZoom + 0.2f
                        applyAppearance()
                    }
                    2 -> {
                        if (terminal.isFocused) terminal.hideSoftKeyboard() else terminal.showSoftKeyboard()
                    }
                }
            }
            .show()
    }

    private fun showHelp() {
        val text = try {
            assets.open("doc/README.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "nhzterm help doc missing from assets."
        }
        val view = TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(view)
        AlertDialog.Builder(this).setTitle("nhzterm Help").setView(scroll)
            .setPositiveButton("Close", null).show()
    }

    private fun reportIssue() {
        val body = buildString {
            append("nhzterm issue report\n")
            append("app: com.nhztech.nhzterm\n")
            append("android: ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append(")\n")
            append("device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("session: ").append(currentSessionId ?: "none").append("\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "nhzterm issue")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(intent, "Report issue via"))
        } catch (e: Exception) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nhzterm-report", body))
            toast("no share target — diagnostics copied to clipboard")
        }
    }

    // ------------------------------------------------------------------
    // URL handling — §10.2: NO auto-clickable links. Interaction only
    // through long-press Open / the Ctrl+Alt+U picker.
    // ------------------------------------------------------------------

    private val urlRegex = Regex("(https?://|www\\.)[^\\s\"'<>\\])}]+")

    private fun extractUrl(text: String): String? = urlRegex.find(text)?.value

    private fun openUrl(url: String?) {
        if (url == null) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (url.startsWith("www.")) "https://$url" else url)))
        } catch (e: Exception) {
            toast("no app can open $url")
        }
    }

    /** Ctrl+Alt+U — extract links from the visible screen and offer them. */
    private fun showUrlPicker() {
        val urls = urlRegex.findAll(terminal.dumpVisible()).map { it.value }.distinct().toList()
        if (urls.isEmpty()) {
            toast("no links on screen")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Open link")
            .setItems(urls.toTypedArray()) { _, which -> openUrl(urls[which]) }
            .show()
    }

    private fun shareText(text: String) {
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    // ------------------------------------------------------------------
    // hardware keys: §10.7 volume emulation, §10.8 Ctrl shortcuts,
    // §10.9 Ctrl+Alt shortcuts
    // ------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // ---- §10.7 volume-key emulation ----
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) volDownCtrl = true
                if (event.action == KeyEvent.ACTION_UP) volDownCtrl = false
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    volUpActive = true
                    // a lone Vol Up press (no combo) still adjusts volume
                    terminal.postDelayed({
                        if (volUpActive) {
                            volUpActive = false
                            dispatchVolumeDefault()
                        }
                    }, 350)
                }
                if (event.action == KeyEvent.ACTION_UP) volUpActive = false
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (volUpActive) {
                val handled = handleVolUpCombo(event)
                if (handled) {
                    volUpActive = false
                    return true
                }
            }
            val ctrl = event.isCtrlPressed || volDownCtrl
            val alt = event.isAltPressed

            // ---- §10.9 Ctrl+Alt shortcuts ----
            if (ctrl && alt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_C -> { createAndAttach(); return true }
                    KeyEvent.KEYCODE_R -> {
                        currentSessionId?.let { promptRename(it, sessionNames[it] ?: "") }
                        return true
                    }
                    KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_DPAD_DOWN -> { cycleSession(1); return true }
                    KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_DPAD_UP -> { cycleSession(-1); return true }
                    KeyEvent.KEYCODE_M -> { showContextMenu(dp(40).toFloat(), dp(80).toFloat()); return true }
                    KeyEvent.KEYCODE_U -> { showUrlPicker(); return true }
                    KeyEvent.KEYCODE_V -> { pasteFromClipboard(); return true }
                    KeyEvent.KEYCODE_K -> {
                        if (terminal.isFocused) terminal.hideSoftKeyboard() else terminal.showSoftKeyboard()
                        return true
                    }
                    KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> { zoom(1); return true }
                    KeyEvent.KEYCODE_MINUS -> { zoom(-1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { togglePanel(); return true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { togglePanel(forceClose = true); return true }
                    in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9,
                    in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> {
                        val digit = when (event.keyCode) {
                            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
                                event.keyCode - KeyEvent.KEYCODE_1
                            else ->
                                event.keyCode - KeyEvent.KEYCODE_NUMPAD_1
                        }
                        jumpToSession(digit)
                        return true
                    }
                }
            }

            // ---- §10.8 standard Ctrl shortcuts ----
            if (ctrl && !alt) {
                val byte = when (event.keyCode) {
                    KeyEvent.KEYCODE_A -> 0x01
                    KeyEvent.KEYCODE_E -> 0x05
                    KeyEvent.KEYCODE_C -> 0x03
                    KeyEvent.KEYCODE_D -> 0x04
                    KeyEvent.KEYCODE_Z -> 0x1a
                    KeyEvent.KEYCODE_L -> 0x0c
                    KeyEvent.KEYCODE_K -> 0x0b
                    KeyEvent.KEYCODE_U -> 0x15
                    KeyEvent.KEYCODE_W -> 0x17
                    KeyEvent.KEYCODE_Y -> 0x19
                    KeyEvent.KEYCODE_R -> 0x12
                    else -> -1
                }
                if (byte >= 0) {
                    terminal.sendBytes(byte)
                    return true
                }
            }

            // plain ctrl+letter from a hardware keyboard (no extra-keys bar)
            if (ctrl && !alt) {
                val c = event.unicodeChar
                if (c in 'a'.code..'z'.code) {
                    terminal.sendBytes(c - 'a'.code + 1)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** §10.7 Vol Up + key table. */
    private fun handleVolUpCombo(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_K -> { extraKeys.toggleRows(); return true }
            KeyEvent.KEYCODE_V -> return false // show system volume slider
            KeyEvent.KEYCODE_E -> { terminal.sendBytes(0x1b); return true }
            KeyEvent.KEYCODE_T -> { terminal.sendBytes(0x09); return true }
            KeyEvent.KEYCODE_W -> { terminal.sendBytes(0x1b, 0x5b, 0x41); return true }
            KeyEvent.KEYCODE_S -> { terminal.sendBytes(0x1b, 0x5b, 0x42); return true }
            KeyEvent.KEYCODE_A -> { terminal.sendBytes(0x1b, 0x5b, 0x44); return true }
            KeyEvent.KEYCODE_D -> { terminal.sendBytes(0x1b, 0x5b, 0x43); return true }
            KeyEvent.KEYCODE_L -> { terminal.sendBytes('|'.code); return true }
            KeyEvent.KEYCODE_H -> { terminal.sendBytes('~'.code); return true }
            KeyEvent.KEYCODE_U -> { terminal.sendBytes('_'.code); return true }
            KeyEvent.KEYCODE_P -> { terminal.sendBytes(0x1b, 0x5b, 0x35, 0x7e); return true }
            KeyEvent.KEYCODE_N -> { terminal.sendBytes(0x1b, 0x5b, 0x36, 0x7e); return true }
            KeyEvent.KEYCODE_B -> { terminal.sendBytes(0x1b, 'b'.code); return true }
            KeyEvent.KEYCODE_F -> { terminal.sendBytes(0x1b, 'f'.code); return true }
            KeyEvent.KEYCODE_PERIOD -> { terminal.sendBytes(0x1c); return true } // Ctrl+\ SIGQUIT
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                terminal.sendBytes(*("\u001bO" + ('P'.code + (event.keyCode - KeyEvent.KEYCODE_1)).toChar()).toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff }.toIntArray())
                return true
            }
            KeyEvent.KEYCODE_0 -> {
                terminal.sendBytes(*"\u001b[21~".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff }.toIntArray()) // F10
                return true
            }
        }
        return false
    }

    private fun dispatchVolumeDefault() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audio.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_SAME,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun cycleSession(delta: Int) {
        val ids = sessionNames.keys.toList()
        if (ids.isEmpty()) return
        val cur = currentSessionId
        val idx = ids.indexOf(cur)
        val next = ((if (idx < 0) 0 else idx + delta) + ids.size) % ids.size
        attachTo(ids[next])
    }

    private fun jumpToSession(index: Int) {
        val ids = sessionNames.keys.toList()
        if (index < ids.size) attachTo(ids[index])
    }

    private fun zoom(direction: Int) {
        settings.textZoom = (settings.textZoom + direction * 0.1f).coerceIn(0.6f, 2.5f)
        applyAppearance()
    }

    // ------------------------------------------------------------------
    // misc
    // ------------------------------------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
        if (::panel.isInitialized) refreshPanel()
    }

    override fun onDestroy() {
        // §3: closing the UI NEVER kills sessions — the daemon owns them.
        // We only disconnect this client.
        if (::api.isInitialized) api.disconnect()
        super.onDestroy()
    }
}
