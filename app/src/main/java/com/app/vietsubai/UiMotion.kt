package com.app.vietsubai

import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.DecelerateInterpolator

fun View.animateEntrance(delay: Long = 0L) {
    postDelayed({ startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_slide_up)) }, delay)
}

fun View.enablePressMotion() {
    setOnTouchListener { view, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> { view.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.88f).setDuration(80).setInterpolator(DecelerateInterpolator()).start() }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start() }
        }
        false
    }
}
