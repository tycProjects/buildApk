package com.nhztech.nhzterm.ui

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.File

/**
 * One terminal theme (§11): background, foreground, cursor, selection and
 * the full 16-slot ANSI palette. Shipped as JSON in assets/themes/ (the
 * untouched, restorable original — §12.2), copied to etc/themes/ on first
 * run where user edits live.
 */
data class Theme(
    val name: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val selection: Int,
    val ansi: IntArray // 16 entries
) {
    fun brighten(color: Int): Int {
        // simple 15% lift for bold base colors
        val r = ((color shr 16 and 0xff) * 1.15).toInt().coerceAtMost(255)
        val g = ((color shr 8 and 0xff) * 1.15).toInt().coerceAtMost(255)
        val b = ((color and 0xff) * 1.15).toInt().coerceAtMost(255)
        return (r shl 16) or (g shl 8) or b
    }

    companion object {
        val DEFAULT = Theme(
            "Dracula",
            0x282a36, 0xf8f8f2, 0xf8f8f2, 0x44475a,
            intArrayOf(
                0x21222c, 0xff5555, 0x50fa7b, 0xf1fa8c,
                0xbd93f9, 0xff79c6, 0x8be9fd, 0xf8f8f2,
                0x6272a4, 0xff6e6e, 0x69ff94, 0xffffa5,
                0xd6acff, 0xff92df, 0xa4ffff, 0xffffff
            )
        )
    }
}

/**
 * Theme registry — the 25 themes from concept §11, loaded from JSON.
 * Suggested out-of-the-box defaults (§11) sort first in pickers:
 * Dracula, Nord, Gruvbox Dark, Matrix Green.
 */
object ThemeRegistry {

    private val DEFAULTS_FIRST = listOf("Dracula", "Nord", "Gruvbox Dark", "Matrix Green")

    fun loadAll(context: Context, themesDir: File?): List<Theme> {
        val themes = mutableListOf<Theme>()
        val seen = HashSet<String>()

        // user-modified copies first (etc/themes), assets as fallback
        if (themesDir != null) {
            themesDir.listFiles()?.forEach { f ->
                parse(f.readText())?.let {
                    if (seen.add(it.name)) themes.add(it)
                }
            }
        }
        try {
            context.assets.list("themes")?.forEach { name ->
                if (!name.endsWith(".json")) return@forEach
                context.assets.open("themes/$name").bufferedReader().use { r ->
                    parse(r.readText())?.let {
                        if (seen.add(it.name)) themes.add(it)
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        themes.sortWith(Comparator { a, b ->
            val ia = DEFAULTS_FIRST.indexOf(a.name).let { if (it < 0) Int.MAX_VALUE else it }
            val ib = DEFAULTS_FIRST.indexOf(b.name).let { if (it < 0) Int.MAX_VALUE else it }
            if (ia != ib) ia.compareTo(ib) else a.name.compareTo(b.name)
        })
        return themes
    }

    fun parse(json: String): Theme? {
        return try {
            val o = JSONObject(json)
            val ansiArr = o.getJSONArray("ansi")
            val ansi = IntArray(16) { parseColor(ansiArr.getString(it)) }
            Theme(
                name = o.getString("name"),
                background = parseColor(o.getString("background")),
                foreground = parseColor(o.getString("foreground")),
                cursor = parseColor(o.optString("cursor", o.getString("foreground"))),
                selection = parseColor(o.optString("selection", "#3a3a4a")),
                ansi = ansi
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseColor(s: String): Int = Color.parseColor(s) and 0xffffff
}
