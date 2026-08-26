package com.app.vietsubai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HistoryItem(val id: String, val inputName: String, val outputPath: String?, val status: String, val createdAt: Long, val error: String? = null)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_history", Context.MODE_PRIVATE)
    @Synchronized fun all(): List<HistoryItem> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]")); (0 until array.length()).map { val o=array.getJSONObject(it); HistoryItem(o.getString("id"),o.getString("inputName"),o.optString("outputPath").ifBlank { null },o.getString("status"),o.getLong("createdAt"),o.optString("error").ifBlank { null }) }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())
    @Synchronized fun addQueued(name: String): String { val id=UUID.randomUUID().toString(); save((all()+HistoryItem(id,name,null,"QUEUED",System.currentTimeMillis())).take(100)); return id }
    @Synchronized fun update(id: String, status: String, output: String? = null, error: String? = null) { save(all().map { if (it.id==id) it.copy(status=status,outputPath=output ?: it.outputPath,error=error) else it }) }
    @Synchronized fun remove(id: String) { save(all().filterNot { it.id==id }) }
    private fun save(items: List<HistoryItem>) { val a=JSONArray(); items.forEach { a.put(JSONObject().put("id",it.id).put("inputName",it.inputName).put("outputPath",it.outputPath ?: "").put("status",it.status).put("createdAt",it.createdAt).put("error",it.error ?: "")) }; prefs.edit().putString("items",a.toString()).apply() }
}
