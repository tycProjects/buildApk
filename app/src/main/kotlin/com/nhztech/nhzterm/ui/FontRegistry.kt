package com.nhztech.nhzterm.ui

import android.content.Context
import android.graphics.Typeface

/**
 * Font registry — the 25 fonts from concept §11.
 *
 * Licensing reality: font binaries can't all be bundled blindly, so the
 * registry resolves a chosen font in priority order:
 *   1. assets/fonts/<slug>.ttf  — drop licensed fonts here (or into
 *      etc/fonts on device) and they light up immediately
 *   2. the platform monospace face — always works, honest fallback
 *
 * The picker always shows all 25 names; the renderer never fails.
 */
object FontRegistry {

    val FONTS = listOf(
        "JetBrains Mono", "Fira Code", "Cascadia Code", "Iosevka", "Hack",
        "IBM Plex Mono", "Source Code Pro", "Space Mono", "Terminus",
        "Victor Mono", "Monaspace", "Inconsolata", "Ubuntu Mono",
        "DejaVu Sans Mono", "Consolas", "Menlo", "SF Mono", "Roboto Mono",
        "Anonymous Pro", "PT Mono", "Operator Mono", "Input Mono",
        "Recursive Mono", "Departure Mono", "Comic Mono"
    )

    fun slug(name: String): String =
        name.lowercase().replace(" ", "-")

    fun resolve(context: Context, name: String): Typeface {
        val candidates = listOf(
            "fonts/" + slug(name) + ".ttf",
            "fonts/" + slug(name) + ".otf"
        )
        for (path in candidates) {
            try {
                return Typeface.createFromAsset(context.assets, path)
            } catch (ignored: Exception) {
            }
        }
        return Typeface.MONOSPACE
    }
}
