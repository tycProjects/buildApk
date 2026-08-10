package com.blackglass.filemanager.util

import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

object FileUtils {

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format("%.1f %s", value, units[digitGroups])
        }
    }

    fun formatDate(timestampMillis: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(timestampMillis))
    }
}
