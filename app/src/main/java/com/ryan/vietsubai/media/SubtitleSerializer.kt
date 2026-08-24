package com.ryan.vietsubai.media

import com.ryan.vietsubai.model.SubtitleSegment
import org.json.JSONArray
import org.json.JSONObject

/**
 * WorkManager's Data only accepts primitives/strings, so the subtitle list produced in the
 * Editor (already translated by [com.ryan.vietsubai.ai.SubtitleAiPipeline]) is serialized to a
 * compact JSON string before being queued for [com.ryan.vietsubai.media.ProcessingWorker].
 */
object SubtitleSerializer {
    fun toJson(items: List<SubtitleSegment>): String {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(
                JSONObject()
                    .put("start", s.start)
                    .put("end", s.end)
                    .put("text", s.text)
                    .put("translation", s.translation)
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<SubtitleSegment> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SubtitleSegment(
                    start = o.getDouble("start"),
                    end = o.getDouble("end"),
                    text = o.getString("text"),
                    translation = o.optString("translation").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())
    }
}
