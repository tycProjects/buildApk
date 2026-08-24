package com.ryan.vietsubai.media

import android.content.Context
import android.net.Uri
import androidx.work.*
import java.util.UUID

class MediaProcessor(private val context: Context) {
    fun enqueue(source: Uri, config: ProcessingConfig): UUID {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(workDataOf(
                "source_uri" to source.toString(),
                "target_language" to config.targetLanguage,
                "voice" to config.voice,
                "burn_subtitles" to config.burnSubtitles,
                "subtitle_source" to config.subtitleSource,
                "subtitle_uri" to config.subtitleUri,
                "keep_original_volume" to config.keepOriginalVolume,
                "export_mode" to config.exportMode
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }
}

data class ProcessingConfig(
    val targetLanguage: String,
    val voice: String,
    val burnSubtitles: Boolean,
    val subtitleSource: String,
    val subtitleUri: String?,
    val keepOriginalVolume: Float,
    val exportMode: String
)
