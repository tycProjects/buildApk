package com.ryan.vietsubai.editor

import android.content.Context
import android.net.Uri
import com.ryan.vietsubai.model.FontAsset
import java.io.File

class FontManager(private val context: Context) {
    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    fun import(uri: Uri, displayName: String): FontAsset {
        val ext = displayName.substringAfterLast('.', "ttf").lowercase().let { if (it in listOf("ttf","otf")) it else "ttf" }
        val id = "font_${System.currentTimeMillis()}"
        val file = File(dir, "$id.$ext")
        context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Không đọc được font" }.use { it.copyTo(file.outputStream()) } }
        return FontAsset(id, displayName, Uri.fromFile(file).toString())
    }
}
