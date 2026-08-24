package com.ryan.vietsubai.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.media3.effect.BitmapOverlay
import com.ryan.vietsubai.model.BlurCropRegion

class RegionCoverOverlay(
    private val regions: List<BlurCropRegion>,
    private val width: Int,
    private val height: Int,
) : BitmapOverlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(205, 10, 10, 16) }
    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        regions.filter { ms in it.startMs..it.endMs }.forEach { r ->
            canvas.drawRoundRect(r.left * width, r.top * height, r.right * width, r.bottom * height, 18f, 18f, paint)
        }
        return bitmap
    }
}
