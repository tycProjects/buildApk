package com.zamzzz.killwifi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.zamzzz.killwifi.core.IPKiller;

import java.util.Random;

public class AttackActivity extends AppCompatActivity {

    // VIEWS
    private TextView tvTargetIP, tvPackets, tvSpeed, tvStatus, tvLog;
    private ProgressBar progressBar;
    private ImageView ivSkull, ivRadar;
    private MaterialButton btnStop;

    // CORE
    private IPKiller killer;
    private String targetIP;

    // ANIMASI
    private ObjectAnimator rotateAnim, pulseAnim;

    // LOG SIMULATOR
    private Handler logHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private long totalPackets = 0;
    private long startTime = 0;

    private final String[] LOG_LINES = {
        "► Sending UDP packets…",
        "► TCP SYN flood active…",
        "► HTTP request flooding…",
        "► Bypassing firewall…",
        "► Injecting payload…",
        "► Target overloaded…",
        "► Connection timeout…",
        "► Packet storm deployed…",
        "► Brute forcing port…",
        "► Deauth sequence initiated…",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attack);

        // AMBIL TARGET IP
        targetIP = getIntent().getStringExtra("TARGET_IP");
        if (targetIP == null) targetIP = "192.168.1.1";

        // INIT
        initViews();
        setupAnimations();
        setupListeners();

        // UPDATE UI
        tvTargetIP.setText("🎯 TARGET: " + targetIP);

        // MULAI SERANGAN
        startAttack();
    }

    private void initViews() {
        tvTargetIP = findViewById(R.id.tv_target_ip);
        tvPackets = findViewById(R.id.tv_packets);
        tvSpeed = findViewById(R.id.tv_speed);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        progressBar = findViewById(R.id.progress_bar);
        ivSkull = findViewById(R.id.iv_skull);
        ivRadar = findViewById(R.id.iv_radar);
        btnStop = findViewById(R.id.btn_stop);
    }

    private void setupAnimations() {
        // PULSE SKULL
        ObjectAnimator sx = ObjectAnimator.ofFloat(ivSkull, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(ivSkull, "scaleY", 1f, 1.15f, 1f);
        sx.setDuration(800); sy.setDuration(800);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sx.start(); sy.start();

        // ROTATE RADAR
        rotateAnim = ObjectAnimator.ofFloat(ivRadar, "rotation", 0f, 360f);
        rotateAnim.setDuration(2000);
        rotateAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotateAnim.setInterpolator(new LinearInterpolator());
        rotateAnim.start();

        // PROGRESS BAR
        ObjectAnimator progressAnim = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnim.setDuration(3000);
        progressAnim.setRepeatCount(ValueAnimator.INFINITE);
        progressAnim.start();
    }

    private void setupListeners() {
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAttack();
            }
        });
    }

    // ═══════════════════════════════════════════
    //  MULAI SERANGAN
    // ═══════════════════════════════════════════
    private void startAttack() {
        startTime = System.currentTimeMillis();

        killer = new IPKiller(targetIP, 80);
        killer.setListener(new IPKiller.OnAttackListener() {
            @Override
            public void onProgress(long packets, long bytes, long speed) {
                totalPackets = packets;
                runOnUiThread(() -> {
                    tvPackets.setText(formatNumber(packets));
                    tvSpeed.setText(formatNumber(speed) + " pkt/s");
                });
            }

            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> tvStatus.setText("🔥 " + status));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                    Toast.makeText(AttackActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });

        killer.start();
        tvStatus.setText("🔥 ATTACKING…");

        // START LOG SIMULATOR
        startLogSimulator();
    }

    // ═══════════════════════════════════════════
    //  STOP SERANGAN
    // ═══════════════════════════════════════════
    private void stopAttack() {
        if (killer != null) {
            killer.stop();
        }

        logHandler.removeCallbacksAndMessages(null);

        // STOP ANIMASI
        if (rotateAnim != null) rotateAnim.cancel();

        // PINDAH KE RESULT
        long duration = (System.currentTimeMillis() - startTime) / 1000;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(AttackActivity.this, ResultActivity.class);
            intent.putExtra("TARGET_IP", targetIP);
            intent.putExtra("TOTAL_PACKETS", totalPackets);
            intent.putExtra("DURATION", duration);
            intent.putExtra("SUCCESS", totalPackets > 1000);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 500);
    }

    // ═══════════════════════════════════════════
    //  LOG SIMULATOR (efek hacker)
    // ═══════════════════════════════════════════
    private void startLogSimulator() {
        logHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (killer != null && killer.isRunning()) {
                    String log = LOG_LINES[random.nextInt(LOG_LINES.length)];
                    String current = tvLog.getText().toString();
                    String[] lines = current.split("\n");
                    StringBuilder sb = new StringBuilder();

                    int start = Math.max(0, lines.length - 6);
                    for (int i = start; i < lines.length; i++) {
                        sb.append(lines[i]).append("\n");
                    }
                    sb.append(log);

                    tvLog.setText(sb.toString());

                    logHandler.postDelayed(this, 300 + random.nextInt(500));
                }
            }
        }, 500);
    }

    // ═══════════════════════════════════════════
    //  HELPER
    // ═══════════════════════════════════════════
    private String formatNumber(long num) {
        if (num >= 1_000_000_000L) return String.format("%.2fB", num / 1_000_000_000.0);
        if (num >= 1_000_000L) return String.format("%.2fM", num / 1_000_000.0);
        if (num >= 1_000L) return String.format("%.2fK", num / 1_000.0);
        return String.valueOf(num);
    }

    @Override
    public void onBackPressed() {
        // Disable back saat attacking
        Toast.makeText(this, "🛑 Klik STOP dulu Tuan!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (killer != null && killer.isRunning()) {
            killer.stop();
        }
        logHandler.removeCallbacksAndMessages(null);
    }
}