package com.example.fflauncher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String FREE_FIRE = "com.dts.freefireth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("FF Launcher");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        Button launch = new Button(this);
        launch.setText("MỞ FREE FIRE");
        launch.setOnClickListener(v -> launchFreeFire());

        root.addView(title);
        root.addView(launch);

        setContentView(root);
    }

    private void launchFreeFire() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(FREE_FIRE);

        if (intent == null) {
            Toast.makeText(this,
                "Không tìm thấy Free Fire. Kiểm tra package của bản game.",
                Toast.LENGTH_LONG).show();
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
