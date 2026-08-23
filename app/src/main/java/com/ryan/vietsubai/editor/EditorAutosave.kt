package com.ryan.vietsubai.editor

import android.content.Context
import com.ryan.vietsubai.model.EditorDraft
import org.json.JSONArray
import org.json.JSONObject
import com.ryan.vietsubai.model.BlurCropRegion
import com.ryan.vietsubai.model.FontAsset
import com.ryan.vietsubai.model.SubtitleSegment

class EditorAutosave(context: Context) {
    private val prefs=context.getSharedPreferences("editor_autosave",Context.MODE_PRIVATE)
    fun save(d:EditorDraft){
        val o=JSONObject().put("videoUri",d.videoUri).put("videoName",d.videoName).put("speed",d.speed).put("volume",d.volume).put("subtitleText",d.subtitleText).put("selectedTool",d.selectedTool).put("activeFontId",d.activeFontId).put("ocrScanFps",d.ocrScanFps).put("ttsRate",d.ttsRate).put("ttsPadMs",d.ttsPadMs).put("ttsSyncLabel",d.ttsSyncLabel)
        o.put("fonts",JSONArray(d.fonts.map{JSONObject().put("id",it.id).put("name",it.name).put("uri",it.uri)}))
        o.put("blur",JSONArray(d.blurRegions.map{JSONObject().put("id",it.id).put("l",it.left).put("t",it.top).put("r",it.right).put("b",it.bottom).put("s",it.startMs).put("e",it.endMs)}))
        o.put("subs",JSONArray(d.subtitleSegments.map{JSONObject().put("start",it.start).put("end",it.end).put("text",it.text).put("translation",it.translation)}))
        prefs.edit().putString("draft",o.toString()).apply()
    }
    fun load():EditorDraft?=runCatching{val o=JSONObject(prefs.getString("draft",null)?:return null);val fonts=o.optJSONArray("fonts")?.let{a->List(a.length()){i->val x=a.getJSONObject(i);FontAsset(x.getString("id"),x.getString("name"),x.getString("uri"))}}?:emptyList();val blur=o.optJSONArray("blur")?.let{a->List(a.length()){i->val x=a.getJSONObject(i);BlurCropRegion(x.getString("id"),x.getDouble("l").toFloat(),x.getDouble("t").toFloat(),x.getDouble("r").toFloat(),x.getDouble("b").toFloat(),x.optLong("s"),x.optLong("e",Long.MAX_VALUE))}}?:emptyList();val subs=o.optJSONArray("subs")?.let{a->List(a.length()){i->val x=a.getJSONObject(i);SubtitleSegment(x.getDouble("start"),x.getDouble("end"),x.getString("text"),x.optString("translation").ifBlank{null})}}?:emptyList();EditorDraft(videoUri=o.optString("videoUri").ifBlank{null},videoName=o.optString("videoName"),speed=o.optDouble("speed",1.0).toFloat(),volume=o.optDouble("volume",1.0).toFloat(),subtitleText=o.optString("subtitleText"),selectedTool=o.optString("selectedTool","trim"),activeFontId=o.optString("activeFontId").ifBlank{null},fonts=fonts,blurRegions=blur,subtitleSegments=subs,ocrScanFps=o.optDouble("ocrScanFps",2.0).toFloat(),ttsRate=o.optDouble("ttsRate",1.0).toFloat(),ttsPadMs=o.optLong("ttsPadMs",0),ttsSyncLabel=o.optString("ttsSyncLabel","Chưa đồng bộ"))}.getOrNull()
    fun clear(){prefs.edit().remove("draft").apply()}
}
