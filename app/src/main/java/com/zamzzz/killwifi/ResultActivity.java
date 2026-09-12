package com.zamzzz.killwifi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ResultActivity extends AppCompatActivity {

    // VIEWS
    private ImageView ivResult;
    private TextView tvTitle, tvResult, tvTotalLabel, tvTotal,
                     tvTimeLabel, tvTime, tvTargetLabel, tvTarget;
    private MaterialButton btnBack;

    // DATA
    private String targetIP;
    private long totalPackets;
    private long duration;
    private boolean success;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // AMBIL DATA
        targetIP = getIntent().getStringExtra("TARGET_IP");
        totalPackets = getIntent().getLongExtra("TOTAL_PACKETS", 0);
        duration = getIntent().getLongExtra("DURATION", 0);
        success = getIntent().getBooleanExtra("SUCCESS", true);

        // INIT
        initViews();
        setupAnimations();
        setupListeners();
        displayResult();
    }

    private void initViews() {
        ivResult = findViewById(R.id.iv_result);
        tvTitle = findViewById(R.id.tv_result_title);
        tvResult = findViewById(R.id.tv_result_status);
        tvTotalLabel = findViewById(R.id.tv_total_label);
        tvTotal = findViewById(R.id.tv_total_value);
        tvTimeLabel = findViewById(R.id.tv_time_label);
        tvTime = findViewById(R.id.tv_time_value);
        tvTargetLabel = findViewById(R.id.tv_target_label);
        tvTarget = findViewById(R.id.tv_target_value);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupAnimations() {
        // PULSE ICON
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(ivResult, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(ivResult, "scaleY", 1f, 1.2f, 1f);
        pulseX.setDuration(1200);
        pulseY.setDuration(1200);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.setInterpolator(new LinearInterpolator());
        pulseY.setInterpolator(new LinearInterpolator());
        pulseX.start();
        pulseY.start();

        // FADE IN TITLE
        tvTitle.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
    }

    private void displayResult() {
        // SET ICON BERDASARKAN HASIL
        if (success) {
            ivResult.setImageResource(R.drawable.ic_skull);
            ivResult.setColorFilter(getResources().getColor(R.color.neon_green));
            tvResult.setText(R.string.result_success);
            tvResult.setTextColor(getResources().getColor(R.color.status_success));
        } else {
            ivResult.setImageResource(R.drawable.ic_skull);
            ivResult.setColorFilter(getResources().getColor(R.color.neon_red));
            tvResult.setText(R.string.result_failed);
            tvResult.setTextColor(getResources().getColor(R.color.status_danger));
        }

        // FORMAT DATA
        tvTotal.setText(formatNumber(totalPackets));
        tvTime.setText(duration + " detik");
        tvTarget.setText(targetIP != null ? targetIP : "Unknown");
    }

    private String formatNumber(long num) {
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000.0);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000.0);
        if (num >= 1_000L) return String.format("%.2fK", num / 1_000.0);
        return String.valueOf(num);
    }

    @Override
    public void onBackPressed() {
        btnBack.performClick();
    }
}