package com.zamzzz.killwifi;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private ImageView ivBack;
    private SwitchMaterial swSound, swVibrate, swAnimation, swAutoRetry;
    private SeekBar sbPower, sbThreads;
    private TextView tvPowerValue, tvThreadsValue;
    private MaterialButton btnSave, btnReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        swSound = findViewById(R.id.sw_sound);
        swVibrate = findViewById(R.id.sw_vibrate);
        swAnimation = findViewById(R.id.sw_animation);
        swAutoRetry = findViewById(R.id.sw_auto_retry);
        sbPower = findViewById(R.id.sb_power);
        sbThreads = findViewById(R.id.sb_threads);
        tvPowerValue = findViewById(R.id.tv_power_value);
        tvThreadsValue = findViewById(R.id.tv_threads_value);
        btnSave = findViewById(R.id.btn_save);
        btnReset = findViewById(R.id.btn_reset);
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

        // POWER SEEK
        sbPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPowerValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // THREADS SEEK
        sbThreads.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threads = (progress + 1) * 100;
                tvThreadsValue.setText(threads + " Threads");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // SAVE
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SettingsActivity.this,
                    "✅ Settings Disimpan Tuan!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // RESET
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sbPower.setProgress(100);
                sbThreads.setProgress(4);
                swSound.setChecked(true);
                swVibrate.setChecked(true);
                swAnimation.setChecked(true);
                swAutoRetry.setChecked(false);
                Toast.makeText(SettingsActivity.this,
                    "🔄 Reset ke Default!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSettings() {
        // DEFAULT VALUES
        sbPower.setProgress(100);
        sbThreads.setProgress(4);
        swSound.setChecked(true);
        swVibrate.setChecked(true);
        swAnimation.setChecked(true);
        swAutoRetry.setChecked(false);
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}