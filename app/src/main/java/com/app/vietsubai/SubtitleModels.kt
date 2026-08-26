package com.app.vietsubai

data class SubtitleCue(
    var index: Int,
    var startMs: Long,
    var endMs: Long,
    var text: String
)

object SrtParser {
    private val time = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s+-->\\s+(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
    fun parse(input: String): MutableList<SubtitleCue> {
        return input.replace("\\r", "").trim().split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines(); if (lines.size < 3) return@mapNotNull null
            val match = time.find(lines[1]) ?: return@mapNotNull null
            SubtitleCue(lines[0].trim().toIntOrNull() ?: 0, parseTime(match.groupValues, 1), parseTime(match.groupValues, 5), lines.drop(2).joinToString("\\n"))
        }.toMutableList()
    }
    private fun parseTime(v: List<String>, offset: Int): Long = (((v[offset].toLong() * 60 + v[offset + 1].toLong()) * 60 + v[offset + 2].toLong()) * 1000 + v[offset + 3].toLong())
    fun parseDisplayTime(value: String): Long? {
        val m = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})").matchEntire(value.trim()) ?: return null
        return (((m.groupValues[1].toLong()*60 + m.groupValues[2].toLong())*60 + m.groupValues[3].toLong())*1000 + m.groupValues[4].toLong())
    }
    fun formatTime(ms: Long): String { val h=ms/3600000; val m=(ms%3600000)/60000; val s=(ms%60000)/1000; val z=ms%1000; return "%02d:%02d:%02d,%03d".format(h,m,s,z) }
    fun serialize(cues: List<SubtitleCue>): String = cues.mapIndexed { i, c -> "${i+1}\n${formatTime(c.startMs)} --> ${formatTime(c.endMs)}\n${c.text.trim()}" }.joinToString("\n\n") + "\n"
}
