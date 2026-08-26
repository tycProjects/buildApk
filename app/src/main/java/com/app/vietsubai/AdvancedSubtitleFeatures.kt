package com.app.vietsubai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class SubtitleFormat { SRT, VTT, ASS }

object SubtitleSerializer {
    fun serialize(cues: List<SubtitleCue>, format: SubtitleFormat, style: SubtitleStyle = SubtitleStyle()): String = when (format) {
        SubtitleFormat.SRT -> SrtParser.serialize(cues)
        SubtitleFormat.VTT -> "WEBVTT\n\n" + cues.mapIndexed { i,c -> "${i+1}\n${SrtParser.formatTime(c.startMs).replace(',', '.')} --> ${SrtParser.formatTime(c.endMs).replace(',', '.')}\n${c.text.trim()}" }.joinToString("\n\n") + "\n"
        SubtitleFormat.ASS -> "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,${style.fontName},${style.fontSize},${style.assColor(style.primaryColor)},${style.assColor(style.primaryColor)},${style.assColor(style.outlineColor)},&H00000000,0,0,0,0,100,100,0,0,1,${style.outline},0,${style.alignment},20,20,20,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" + cues.joinToString("\n") { "Dialogue: 0,${assTime(it.startMs)},${assTime(it.endMs)},Default,,0,0,0,,${it.text.replace("\n", "\\N")}" }
    }
    private fun assTime(ms: Long): String = "%d:%02d:%02d.%02d".format(ms/3600000,(ms%3600000)/60000,(ms%60000)/1000,(ms%1000)/10)
}

data class VideoChunk(val startMs: Long, val endMs: Long)
object ChunkPlanner { fun plan(durationMs: Long, chunkMs: Long = 5*60*1000, overlapMs: Long = 1500): List<VideoChunk> { val result=mutableListOf<VideoChunk>(); var start=0L; while(start<durationMs){ val end=(start+chunkMs).coerceAtMost(durationMs); result += VideoChunk(start,(end+overlapMs).coerceAtMost(durationMs)); if(end==durationMs)break; start=end }; return result } }

class GlossaryStore(context: Context) {
    private val prefs=context.getSharedPreferences("translation_glossary",Context.MODE_PRIVATE)
    fun all(): Map<String,String> = runCatching { val a=JSONArray(prefs.getString("items","[]")); (0 until a.length()).associate { val o=a.getJSONObject(it); o.getString("source") to o.getString("target") } }.getOrDefault(emptyMap())
    fun save(items: Map<String,String>) { val a=JSONArray(); items.forEach { (source,target) -> a.put(JSONObject().put("source",source).put("target",target)) }; prefs.edit().putString("items",a.toString()).apply() }
    fun apply(text: String): String = all().entries.fold(text) { result,(source,target) -> result.replace(source,target,ignoreCase=false) }
}
