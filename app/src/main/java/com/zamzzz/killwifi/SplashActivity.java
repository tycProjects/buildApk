package com.zamzzz.killwifi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView ivSkull;
    private TextView tvTitle, tvSubtitle, tvLoading, tvVersion;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // INIT VIEWS
        ivSkull = findViewById(R.id.iv_skull);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvLoading = findViewById(R.id.tv_loading);
        tvVersion = findViewById(R.id.tv_version);
        progressBar = findViewById(R.id.progress_bar);

        // ANIMASI FADE IN TITLE
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(1500);
        tvTitle.startAnimation(fadeIn);
        tvSubtitle.startAnimation(fadeIn);

        // ANIMASI PULSE SKULL
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(ivSkull, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(ivSkull, "scaleY", 1f, 1.15f, 1f);
        pulseX.setDuration(1200);
        pulseY.setDuration(1200);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.setInterpolator(new LinearInterpolator());
        pulseY.setInterpolator(new LinearInterpolator());
        pulseX.start();
        pulseY.start();

        // ANIMASI ROTATE GLOW
        ObjectAnimator rotate = ObjectAnimator.ofFloat(ivSkull, "rotation", 0f, 360f);
        rotate.setDuration(8000);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.start();

        // ANIMASI PROGRESS BAR
        ObjectAnimator progressAnim = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnim.setDuration(2500);
        progressAnim.start();

        // ANIMASI LOADING TEXT BLINK
        ObjectAnimator blink = ObjectAnimator.ofFloat(tvLoading, "alpha", 1f, 0.3f, 1f);
        blink.setDuration(800);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.start();

        // PINDAH KE MAIN ACTIVITY SETELAH 2.8 DETIK
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, 2800);
    }

    @Override
    public void onBackPressed() {
        // DISABLE BACK BUTTON DI SPLASH
    }
}