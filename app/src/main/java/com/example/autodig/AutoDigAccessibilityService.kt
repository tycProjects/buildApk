package com.example.autodig

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Prototype controller.
 *
 * Coordinate rule established from the supplied screenshots:
 * right: X + 1
 * left : X - 1
 * up   : Y + 1
 * down : Y - 1
 *
 * NOTE: OCR region and joystick/attack coordinates must be calibrated for
 * the user's device before enabling automatic control.
 */
class AutoDigAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var currentX: Int? = null
    private var currentY: Int? = null

    // 691x1536 reference screenshot coordinates; recalibrate for the device.
    private val joystickX = 326f
    private val joystickY = 1189f
    private val digX = 326f
    private val digY = 1189f

    override fun onServiceConnected() {
        super.onServiceConnected()
        startLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Game canvas usually exposes no text nodes, so coordinate OCR/screenshot
        // integration should be added here for the final device-specific build.
    }

    override fun onInterrupt() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startLoop() {
        if (running) return
        running = true
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return
                val tx = getSharedPreferences("cfg", MODE_PRIVATE).getInt("tx", -20)
                val ty = getSharedPreferences("cfg", MODE_PRIVATE).getInt("ty", 20)

                val x = currentX
                val y = currentY
                if (x != null && y != null) {
                    when {
                        x < tx -> swipeJoystick(1, 0)
                        x > tx -> swipeJoystick(-1, 0)
                        y < ty -> swipeJoystick(0, 1)
                        y > ty -> swipeJoystick(0, -1)
                        else -> tap(digX, digY)
                    }
                }
                handler.postDelayed(this, 650)
            }
        })
    }

    private fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(p, 0, 60)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun swipeJoystick(dx: Int, dy: Int) {
        val p = Path()
        p.moveTo(joystickX, joystickY)
        p.lineTo(joystickX + dx * 120f, joystickY - dy * 120f)
        val stroke = GestureDescription.StrokeDescription(p, 0, 180)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
