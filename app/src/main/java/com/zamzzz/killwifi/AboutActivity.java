package com.zamzzz.killwifi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class AboutActivity extends AppCompatActivity {

    private ImageView ivBack, ivLogo;
    private TextView tvAppName, tvSubtitle, tvVersion, tvCreator, tvDesc;
    private MaterialButton btnShare, btnRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        initViews();
        setupAnimations();
        setupListeners();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivLogo = findViewById(R.id.iv_logo);
        tvAppName = findViewById(R.id.tv_app_name);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvVersion = findViewById(R.id.tv_version);
        tvCreator = findViewById(R.id.tv_creator);
        tvDesc = findViewById(R.id.tv_desc);
        btnShare = findViewById(R.id.btn_share);
        btnRate = findViewById(R.id.btn_rate);
    }

    private void setupAnimations() {
        // ROTATE LOGO
        ObjectAnimator rotate = ObjectAnimator.ofFloat(ivLogo, "rotation", 0f, 360f);
        rotate.setDuration(8000);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.start();

        // PULSE LOGO
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(ivLogo, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(ivLogo, "scaleY", 1f, 1.1f, 1f);
        pulseX.setDuration(2000);
        pulseY.setDuration(2000);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.start();
        pulseY.start();
    }

    private void setupListeners() {
        // BACK
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // SHARE
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,
                    "💀 KILL WIFI PRO - ZAMZZZ EDITION 💀\n" +
                    "The Ultimate WiFi Killer Tool!\n" +
                    "Download sekarang!");
                startActivity(Intent.createChooser(share, "Share via"));
            }
        });

        // RATE
        btnRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}