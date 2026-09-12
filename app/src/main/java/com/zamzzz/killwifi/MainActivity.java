package com.zamzzz.killwifi;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // VIEWS
    private EditText etIpAddress;
    private MaterialButton btnGaskeun, btnSettings, btnAbout;
    private ImageView ivSkull, ivWifi, ivLightning;
    private TextView tvStatus, tvPowerLabel, tvPowerValue, tvTitle, tvSubtitle;
    private View powerBar;

    // IP VALIDATION PATTERN
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // INIT VIEWS
        initViews();

        // SETUP ANIMASI
        setupAnimations();

        // SETUP LISTENER
        setupListeners();
    }

    private void initViews() {
        etIpAddress = findViewById(R.id.et_ip_address);
        btnGaskeun = findViewById(R.id.btn_gaskeun);
        btnSettings = findViewById(R.id.btn_settings);
        btnAbout = findViewById(R.id.btn_about);
        ivSkull = findViewById(R.id.iv_skull);
        ivWifi = findViewById(R.id.iv_wifi);
        ivLightning = findViewById(R.id.iv_lightning);
        tvStatus = findViewById(R.id.tv_status);
        tvPowerLabel = findViewById(R.id.tv_power_label);
        tvPowerValue = findViewById(R.id.tv_power_value);
        tvTitle = findViewById(R.id.tv_main_title);
        tvSubtitle = findViewById(R.id.tv_main_subtitle);
        powerBar = findViewById(R.id.power_bar);
    }

    private void setupAnimations() {
        // PULSE SKULL
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(ivSkull, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(ivSkull, "scaleY", 1f, 1.1f, 1f);
        pulseX.setDuration(1500);
        pulseY.setDuration(1500);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.start();
        pulseY.start();

        // ROTATE WIFI ICON
        ObjectAnimator rotateWifi = ObjectAnimator.ofFloat(ivWifi, "rotation", 0f, 360f);
        rotateWifi.setDuration(6000);
        rotateWifi.setRepeatCount(ValueAnimator.INFINITE);
        rotateWifi.setInterpolator(new LinearInterpolator());
        rotateWifi.start();

        // LIGHTNING FLASH
        ObjectAnimator flash = ObjectAnimator.ofFloat(ivLightning, "alpha", 1f, 0.2f, 1f);
        flash.setDuration(600);
        flash.setRepeatCount(ValueAnimator.INFINITE);
        flash.start();
    }

    private void setupListeners() {
        // TEXT WATCHER UNTUK IP
        etIpAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String ip = s.toString().trim();
                updateStatus(ip);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // TOMBOL GASKEUN
        btnGaskeun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = etIpAddress.getText().toString().trim();

                // VALIDASI
                if (ip.isEmpty()) {
                    shakeView(etIpAddress);
                    Toast.makeText(MainActivity.this, 
                        "⚠️ Masukin IP Dulu Tuan!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!IP_PATTERN.matcher(ip).matches()) {
                    shakeView(etIpAddress);
                    Toast.makeText(MainActivity.this, 
                        "⚠️ IP Tidak Valid Tuan!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // ANIMASI BUTTON
                animateButton(btnGaskeun);

                // PINDAH KE ATTACK ACTIVITY
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(MainActivity.this, AttackActivity.class);
                        intent.putExtra("TARGET_IP", ip);
                        startActivity(intent);
                        overridePendingTransition(android.R.anim.fade_in, 
                                                  android.R.anim.fade_out);
                    }
                }, 200);
            }
        });

        // TOMBOL SETTINGS
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, 
                                          android.R.anim.fade_out);
            }
        });

        // TOMBOL ABOUT
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, 
                                          android.R.anim.fade_out);
            }
        });
    }

    private void updateStatus(String ip) {
        if (ip.isEmpty()) {
            tvStatus.setText("● SIAP");
            tvStatus.setTextColor(getResources().getColor(R.color.neon_cyan));
            tvPowerValue.setText("0%");
        } else if (IP_PATTERN.matcher(ip).matches()) {
            tvStatus.setText("● TARGET TERKUNCI");
            tvStatus.setTextColor(getResources().getColor(R.color.status_success));
            tvPowerValue.setText("100%");

            // ANIMASI POWER BAR
            ObjectAnimator powerAnim = ObjectAnimator.ofFloat(powerBar, "scaleX", 0.3f, 1f);
            powerAnim.setDuration(600);
            powerAnim.start();

        } else {
            tvStatus.setText("● IP TIDAK VALID");
            tvStatus.setTextColor(getResources().getColor(R.color.status_danger));
            tvPowerValue.setText("0%");
        }
    }

    private void shakeView(View view) {
        android.view.animation.Animation shake = 
            AnimationUtils.loadAnimation(this, R.anim.shake);
        view.startAnimation(shake);
    }

    private void animateButton(View button) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f, 1f);
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.start();
        scaleY.start();
    }
}