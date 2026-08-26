package com.app.vietsubai

data class SubtitleStyle(
    val fontName: String = "Arial",
    val fontSize: Int = 22,
    val primaryColor: String = "#FFFFFF",
    val outlineColor: String = "#000000",
    val outline: Int = 2,
    val alignment: Int = 2
) {
    fun assColor(hex: String): String {
        val value = hex.removePrefix("#").padStart(6, '0').takeLast(6)
        val rr = value.substring(0, 2); val gg = value.substring(2, 4); val bb = value.substring(4, 6)
        return "&H00$bb$gg$rr"
    }
    fun forceStyle(): String = "FontName=$fontName,FontSize=$fontSize,PrimaryColour=${assColor(primaryColor)},OutlineColour=${assColor(outlineColor)},BorderStyle=1,Outline=$outline,Alignment=$alignment"
}
