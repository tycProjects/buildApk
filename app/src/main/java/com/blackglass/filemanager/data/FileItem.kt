package com.blackglass.filemanager.data

import java.io.File

data class FileItem(
    val file: File
) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath
    val isDirectory: Boolean get() = file.isDirectory
    val sizeBytes: Long get() = if (file.isFile) runCatchingLength() else 0L
    val lastModified: Long get() = file.lastModified()
    val extension: String get() = if (file.isFile) file.extension.lowercase() else ""

    private fun runCatchingLength(): Long = try {
        file.length()
    } catch (_: Exception) {
        0L
    }
}

enum class SortMode(val label: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortDirection { ASCENDING, DESCENDING }

enum class ViewMode { LIST, GRID }

enum class FileCategory {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, TEXT, OTHER
}

fun FileItem.category(): FileCategory {
    if (isDirectory) return FileCategory.FOLDER
    return when (extension) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> FileCategory.IMAGE
        "mp4", "mkv", "webm", "avi", "mov", "3gp" -> FileCategory.VIDEO
        "mp3", "wav", "ogg", "flac", "m4a", "aac" -> FileCategory.AUDIO
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx" -> FileCategory.DOCUMENT
        "zip", "rar", "7z", "tar", "gz" -> FileCategory.ARCHIVE
        "apk" -> FileCategory.APK
        "txt", "md", "json", "xml", "log", "kt", "java", "py", "js" -> FileCategory.TEXT
        else -> FileCategory.OTHER
    }
}
