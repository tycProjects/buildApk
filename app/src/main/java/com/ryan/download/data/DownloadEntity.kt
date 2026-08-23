package com.ryan.download.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnail: String,
    val url: String,
    val filePath: String?,
    val state: String,
    val timestamp: Long = System.currentTimeMillis()
)
