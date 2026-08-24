package com.tycept.chatapp

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        logo.scaleX = 0f
        logo.scaleY = 0f
        logo.alpha = 0f
        title.alpha = 0f
        title.translationY = 40f
        subtitle.alpha = 0f

        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0f, 1.15f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0f, 1.15f, 1f)
        val logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)

        val logoSet = AnimatorSet()
        logoSet.playTogether(logoScaleX, logoScaleY, logoAlpha)
        logoSet.duration = 650
        logoSet.interpolator = OvershootInterpolator(1.6f)

        val titleAlpha = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f)
        val titleTrans = ObjectAnimator.ofFloat(title, "translationY", 40f, 0f)
        val titleSet = AnimatorSet()
        titleSet.playTogether(titleAlpha, titleTrans)
        titleSet.duration = 450
        titleSet.interpolator = AccelerateDecelerateInterpolator()
        titleSet.startDelay = 350

        val subtitleAlpha = ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f)
        subtitleAlpha.duration = 400
        subtitleAlpha.startDelay = 550

        val fullSet = AnimatorSet()
        fullSet.playTogether(logoSet, titleSet, subtitleAlpha)
        fullSet.start()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ChatListActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1900)
    }
}
