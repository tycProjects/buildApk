package com.ryan.vietsubai.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.media3.effect.BitmapOverlay
import com.ryan.vietsubai.model.SubtitleSegment

/**
 * Draws whichever subtitle segment is active at a given frame onto a transparent bitmap. Used as
 * a [androidx.media3.effect.OverlayEffect] entry by [ProcessingWorker] to hard-burn subtitles.
 *
 * NOTE: the exact `BitmapOverlay`/`OverlaySettings` surface has shifted a little across Media3
 * releases. This targets the media3-effect 1.3.x API (bundled transitively by media3-transformer
 * 1.3.1 declared in app/build.gradle) — if Android Studio flags a signature mismatch after Gradle
 * sync, check `androidx.media3.effect.BitmapOverlay` in the installed version's sources for the
 * exact abstract members and adjust accordingly.
 */
class SubtitleBitmapOverlay(
    segments: List<SubtitleSegment>,
    private val videoWidthPx: Int,
    private val videoHeightPx: Int
) : BitmapOverlay() {

    // Sorted once up front so each frame can binary-search for the active line instead of scanning
    // the whole list — on a long video with hundreds of lines this is called for every encoded
    // frame, so O(n) per frame adds up to real render time.
    private val sortedSegments = segments.sortedBy { it.start }
    private var cachedText: String? = null
    private var cachedBitmap: Bitmap = blankBitmap()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val active = findActiveSegment(ms)
        val text = active?.translation ?: active?.text
        if (text != cachedText) {
            cachedText = text
            cachedBitmap = if (text.isNullOrBlank()) blankBitmap() else render(text)
        }
        return cachedBitmap
    }

    /** Binary search on start time, then confirm the frame falls within that line's [start, end]. */
    private fun findActiveSegment(ms: Long): SubtitleSegment? {
        if (sortedSegments.isEmpty()) return null
        var lo = 0
        var hi = sortedSegments.size - 1
        var candidate = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if ((sortedSegments[mid].start * 1000).toLong() <= ms) {
                candidate = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (candidate < 0) return null
        val seg = sortedSegments[candidate]
        return if (ms <= (seg.end * 1000).toLong()) seg else null
    }

    private fun blankBitmap(): Bitmap = Bitmap.createBitmap(videoWidthPx.coerceAtLeast(2), videoHeightPx.coerceAtLeast(2), Bitmap.Config.ARGB_8888)

    private fun render(text: String): Bitmap {
        val w = videoWidthPx.coerceAtLeast(2)
        val h = videoHeightPx.coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = h * 0.045f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = fillPaint.textSize * 0.09f
            color = Color.BLACK
        }
        val maxWidth = w * 0.9f
        val lines = wrap(text, fillPaint, maxWidth)
        val lineHeight = fillPaint.textSize * 1.25f
        val baseY = h * 0.92f - (lines.size - 1) * lineHeight
        lines.forEachIndexed { i, line ->
            val y = baseY + i * lineHeight
            canvas.drawText(line, w / 2f, y, strokePaint)
            canvas.drawText(line, w / 2f, y, fillPaint)
        }
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.take(3)
    }
}
