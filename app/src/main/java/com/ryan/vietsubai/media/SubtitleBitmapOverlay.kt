package com.ryan.vietsubai.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.media3.effect.BitmapOverlay
import com.ryan.vietsubai.model.SubtitleSegment
import com.ryan.vietsubai.model.SubtitleStyle

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
    private val videoHeightPx: Int,
    private val fontUri: String = "",
    private val region: FloatArray = floatArrayOf(0.08f, 0.72f, 0.92f, 0.96f),
    private val style: SubtitleStyle = SubtitleStyle()
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
            color = this@SubtitleBitmapOverlay.style.textColor.toInt()
            textSize = h * (this@SubtitleBitmapOverlay.style.fontSize / 420f).coerceIn(.025f, .10f)
            typeface = android.graphics.Typeface.create(loadTypeface(), when {
                this@SubtitleBitmapOverlay.style.bold && this@SubtitleBitmapOverlay.style.italic -> android.graphics.Typeface.BOLD_ITALIC
                this@SubtitleBitmapOverlay.style.bold -> android.graphics.Typeface.BOLD
                this@SubtitleBitmapOverlay.style.italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            })
            textAlign = when (this@SubtitleBitmapOverlay.style.align) { 0 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            letterSpacing = this@SubtitleBitmapOverlay.style.letterSpacing
            setShadowLayer(this@SubtitleBitmapOverlay.style.shadow, 0f, this@SubtitleBitmapOverlay.style.shadow * .25f, Color.BLACK)
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@SubtitleBitmapOverlay.style.strokeWidth.coerceAtLeast(0f)
            color = this@SubtitleBitmapOverlay.style.strokeColor.toInt()
            clearShadowLayer()
        }
        val left = (region.getOrNull(0) ?: 0.08f).coerceIn(0f, 1f) * w
        val top = (region.getOrNull(1) ?: 0.72f).coerceIn(0f, 1f) * h
        val right = (region.getOrNull(2) ?: 0.92f).coerceIn(0f, 1f) * w
        val bottom = (region.getOrNull(3) ?: 0.96f).coerceIn(0f, 1f) * h
        val maxWidth = (right - left).coerceAtLeast(w * 0.2f)
        val lines = wrap(text, fillPaint, maxWidth)
        val lineHeight = fillPaint.textSize * 1.25f
        val x = when (this@SubtitleBitmapOverlay.style.align) { 0 -> left + 8f; 2 -> right - 8f; else -> (left + right) / 2f }
        val baseY = bottom - (lines.size - 1) * lineHeight
        if (this@SubtitleBitmapOverlay.style.backgroundColor ushr 24 > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@SubtitleBitmapOverlay.style.backgroundColor.toInt(); style = Paint.Style.FILL }
            canvas.drawRoundRect(left, top, right, bottom, this@SubtitleBitmapOverlay.style.backgroundRadius, this@SubtitleBitmapOverlay.style.backgroundRadius, bg)
        }
        lines.forEachIndexed { i, line ->
            val y = baseY + i * lineHeight
            canvas.drawText(line, x, y.coerceIn(top + lineHeight, bottom), strokePaint)
            canvas.drawText(line, x, y.coerceIn(top + lineHeight, bottom), fillPaint)
        }
        return bitmap
    }

    private fun loadTypeface(): android.graphics.Typeface {
        return runCatching {
            if (fontUri.isBlank()) return@runCatching android.graphics.Typeface.DEFAULT_BOLD
            val path = android.net.Uri.parse(fontUri).path ?: return@runCatching android.graphics.Typeface.DEFAULT_BOLD
            android.graphics.Typeface.createFromFile(path)
        }.getOrDefault(android.graphics.Typeface.DEFAULT_BOLD)
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
