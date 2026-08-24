package com.ryan.vietsubai.subtitle

import com.ryan.vietsubai.model.SubtitleSegment

object SrtParser {
    private val time = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
    fun parse(raw: String): List<SubtitleSegment> = raw.replace("\r", "").split("\n\n+".toRegex()).mapNotNull { block ->
        val lines = block.lines(); val m = lines.firstNotNullOfOrNull { time.find(it) } ?: return@mapNotNull null
        fun ms(i: Int) = m.groupValues[i].toLong() * if (i == 4 || i == 8) 1 else 1
        fun stamp(h: String, min: String, sec: String, milli: String) = h.toLong()*3600000 + min.toLong()*60000 + sec.toLong()*1000 + milli.toLong()
        val start = stamp(m.groupValues[1],m.groupValues[2],m.groupValues[3],m.groupValues[4])
        val end = stamp(m.groupValues[5],m.groupValues[6],m.groupValues[7],m.groupValues[8])
        val timeIndex = lines.indexOfFirst { time.containsMatchIn(it) }
        val text = lines.drop(timeIndex + 1).joinToString(" ").trim()
        if (text.isBlank()) null else SubtitleSegment(start / 1000.0, end / 1000.0, text)
    }
    fun toSrt(items: List<SubtitleSegment>): String = buildString {
        fun stamp(seconds: Double): String { val ms=(seconds*1000).toLong(); val h=ms/3600000; val m=(ms%3600000)/60000; val s=(ms%60000)/1000; val x=ms%1000; return "%02d:%02d:%02d,%03d".format(h,m,s,x) }
        items.forEachIndexed { i, s -> append(i+1).append('\n').append(stamp(s.start)).append(" --> ").append(stamp(s.end)).append('\n').append(s.translation ?: s.text).append("\n\n") }
    }
}
