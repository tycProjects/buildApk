package com.leonteam.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.TextView

/**
 * TextView có hiệu ứng RGB 7 màu chạy mượt liên tục:
 * đỏ → cam → vàng → xanh lá → cyan → xanh dương → tím → (đỏ) lặp lại.
 *
 * Cách làm: một LinearGradient (TileMode.REPEAT, màu đầu = màu cuối nên nối liền mạch)
 * được trượt ngang theo thời gian (SystemClock.uptimeMillis) ở mỗi frame vẽ.
 * View tự yêu cầu vẽ frame kế tiếp bằng postInvalidateOnAnimation() nên hiệu ứng
 * chạy theo nhịp vsync của màn hình.
 */
class RgbTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    private val rainbow = intArrayOf(
        Color.parseColor("#FF1E1E"), // đỏ
        Color.parseColor("#FF8C00"), // cam
        Color.parseColor("#FFE600"), // vàng
        Color.parseColor("#00E640"), // xanh lá
        Color.parseColor("#00E5FF"), // cyan
        Color.parseColor("#2E6BFF"), // xanh dương
        Color.parseColor("#B44DFF"), // tím
        Color.parseColor("#FF1E1E")  // đỏ (khép vòng)
    )

    /** Thời gian một vòng màu đầy đủ. */
    private val cycleMillis = 3500L

    private val gradientWidth = 240f * resources.displayMetrics.density
    private val shader = LinearGradient(
        0f, 0f, gradientWidth, 0f, rainbow, null, Shader.TileMode.REPEAT
    )
    private val shaderMatrix = Matrix()

    init {
        setShadowLayer(3f * resources.displayMetrics.density, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() % cycleMillis
        val offset = t.toFloat() / cycleMillis * gradientWidth
        shaderMatrix.setTranslate(offset, 0f)
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader
        super.onDraw(canvas)
        postInvalidateOnAnimation() // vẽ frame tiếp theo ở vsync kế tiếp
    }
}
