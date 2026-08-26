package com.app.vietsubai

data class LanguageOption(val code: String, val label: String)

object LanguageCatalog {
    val source = listOf(
        LanguageOption("auto", "Tự động phát hiện"),
        LanguageOption("vi", "Tiếng Việt"),
        LanguageOption("en", "English"),
        LanguageOption("ja", "日本語"),
        LanguageOption("ko", "한국어"),
        LanguageOption("zh", "中文"),
        LanguageOption("th", "ภาษาไทย"),
        LanguageOption("fr", "Français"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("es", "Español")
    )
    val target = source.filterNot { it.code == "auto" }
}
