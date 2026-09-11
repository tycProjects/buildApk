package com.example.aimdragtrainer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String[] GAME_PACKAGES = {
        "com.dts.freefireth",
        "com.dts.freefireth"
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        TrainerView trainer = findViewById(R.id.trainer);
        SeekBar sensitivity = findViewById(R.id.sensitivity);
        TextView sensitivityText = findViewById(R.id.sensitivityText);
        TextView score = findViewById(R.id.score);
        Switch autoLock = findViewById(R.id.autoLock);
        Switch dragMode = findViewById(R.id.dragMode);
        Switch magicMode = findViewById(R.id.magicMode);
        Button shizukuButton = findViewById(R.id.shizukuButton);
        TextView shizukuStatus = findViewById(R.id.shizukuStatus);
        Button launchGame = findViewById(R.id.launchGame);

        trainer.setScoreView(score);

        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                float value = Math.max(0.2f, p / 100f);
                trainer.setSensitivity(value);
                sensitivityText.setText(String.format("Lực kéo: %.1f", value));
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        autoLock.setOnCheckedChangeListener((button, checked) ->
                trainer.setAutoHeadLock(checked));

        dragMode.setOnCheckedChangeListener((button, checked) ->
                trainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        magicMode.setOnCheckedChangeListener((button, checked) ->
                trainer.setEffectMode(checked));

        shizukuButton.setOnClickListener(v -> checkShizuku(shizukuStatus));
        checkShizuku(shizukuStatus);

        launchGame.setOnClickListener(v -> launchFreeFire(shizukuStatus));
    }

    private void checkShizuku(TextView status) {
        if (!Shizuku.pingBinder()) {
            status.setText("Shizuku: chưa chạy");
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            status.setText("Shizuku: đã cấp quyền");
        } else {
            status.setText("Shizuku: đang chạy, chưa cấp quyền");
            Shizuku.requestPermission(100);
        }
    }

    private void launchFreeFire(TextView status) {
        PackageManager pm = getPackageManager();
        for (String pkg : GAME_PACKAGES) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }
        }
        status.setText("Không tìm thấy Free Fire thường");
    }
}
