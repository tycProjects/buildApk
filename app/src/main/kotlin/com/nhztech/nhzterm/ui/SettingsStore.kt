package com.nhztech.nhzterm.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * User settings — plain SharedPreferences, shared with the daemon (same
 * app). The wake-lock key is read by NhztermdService; default OFF per
 * concept §8 ("battery cost must be a deliberate choice").
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nhzterm_prefs", Context.MODE_PRIVATE)

    var themeName: String
        get() = prefs.getString("theme", "Dracula") ?: "Dracula"
        set(v) = prefs.edit().putString("theme", v).apply()

    var fontName: String
        get() = prefs.getString("font", "JetBrains Mono") ?: "JetBrains Mono"
        set(v) = prefs.edit().putString("font", v).apply()

    var textZoom: Float
        get() = prefs.getFloat("text_zoom", 1f)
        set(v) = prefs.edit().putFloat("text_zoom", v).apply()

    var wakeLock: Boolean
        get() = prefs.getBoolean("wake_lock", false)
        set(v) = prefs.edit().putBoolean("wake_lock", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()

    var extraKeysVisible: Boolean
        get() = prefs.getBoolean("extra_keys", true)
        set(v) = prefs.edit().putBoolean("extra_keys", v).apply()
}
