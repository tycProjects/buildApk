package com.tycept.wzhpermissiontest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_ALL = 7001;
    private TextView status;

    private final String[] runtimePermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(28, 28, 28, 28);
        page.setBackgroundColor(0xFF101214);

        TextView title = text("WZH 2 APK — Permission Test", 24, 0xFFE8E8E8);
        page.addView(title);

        TextView info = text(
                "This test app declares the permissions shown in your WZH permissions screen. " +
                "Tap the buttons to request/check them. Some permissions are special, restricted, " +
                "or only applicable on certain Android versions.",
                15, 0xFFB9BEC4);
        page.addView(info, margin(0, 12, 0, 18));

        Button request = button("REQUEST AVAILABLE PERMISSIONS");
        request.setOnClickListener(v -> requestAvailablePermissions());
        page.addView(request);

        Button refresh = button("REFRESH STATUS");
        refresh.setOnClickListener(v -> updateStatus());
        page.addView(refresh, margin(0, 8, 0, 8));

        Button biometric = button("TEST BIOMETRIC");
        biometric.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Biometric permission")
                        .setMessage("USE_BIOMETRIC is declared. A real biometric prompt also requires a device with enrolled biometrics and the BiometricPrompt API.")
                        .setPositiveButton("OK", null)
                        .show());
        page.addView(biometric);

        Button settings = button("OPEN APP SETTINGS");
        settings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        page.addView(settings, margin(0, 8, 0, 16));

        status = text("", 14, 0xFFB9BEC4);
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);

        updateStatus();
    }

    private void requestAvailablePermissions() {
        List<String> needed = new ArrayList<>();

        for (String permission : runtimePermissions) {
            if (Build.VERSION.SDK_INT < 33 &&
                    (permission.equals(Manifest.permission.READ_MEDIA_IMAGES)
                            || permission.equals(Manifest.permission.READ_MEDIA_VIDEO)
                            || permission.equals(Manifest.permission.READ_MEDIA_AUDIO)
                            || permission.equals(Manifest.permission.POST_NOTIFICATIONS)
                            || permission.equals(Manifest.permission.NEARBY_WIFI_DEVICES))) {
                continue;
            }

            if (Build.VERSION.SDK_INT < 31 &&
                    (permission.equals(Manifest.permission.BLUETOOTH_SCAN)
                            || permission.equals(Manifest.permission.BLUETOOTH_CONNECT))) {
                continue;
            }

            if (Build.VERSION.SDK_INT < 29 &&
                    permission.equals(Manifest.permission.ACTIVITY_RECOGNITION)) {
                continue;
            }

            if (Build.VERSION.SDK_INT >= 33 &&
                    permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                continue;
            }

            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needed.add(permission);
            }
        }

        if (needed.isEmpty()) {
            updateStatus();
            new AlertDialog.Builder(this)
                    .setTitle("Nothing else to request")
                    .setMessage("All requestable permissions are already granted, unavailable on this Android version, or require a special system flow.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        requestPermissions(needed.toArray(new String[0]), REQUEST_ALL);
    }

    private void updateStatus() {
        StringBuilder s = new StringBuilder();
        s.append("DECLARED / REQUESTABLE STATUS\n\n");

        for (String p : runtimePermissions) {
            if (Build.VERSION.SDK_INT < 33 &&
                    (p.equals(Manifest.permission.READ_MEDIA_IMAGES)
                            || p.equals(Manifest.permission.READ_MEDIA_VIDEO)
                            || p.equals(Manifest.permission.READ_MEDIA_AUDIO)
                            || p.equals(Manifest.permission.POST_NOTIFICATIONS)
                            || p.equals(Manifest.permission.NEARBY_WIFI_DEVICES))) {
                continue;
            }

            if (Build.VERSION.SDK_INT >= 33 && p.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                continue;
            }

            boolean granted;
            try {
                granted = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
            } catch (Exception e) {
                granted = false;
            }

            s.append(granted ? "✓ " : "○ ");
            s.append(p.substring(p.lastIndexOf('.') + 1));
            s.append("\n");
        }

        s.append("\nNormal permissions such as INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK and VIBRATE do not use the runtime permission dialog.\n");
        s.append("\nSpecial notes:\n");
        s.append("• Background location needs a separate system flow after foreground location.\n");
        s.append("• SMS/call-log permissions may be restricted by Google Play/device policy and may require default-handler roles.\n");
        s.append("• Bluetooth/Nearby Wi‑Fi behavior varies by Android version.\n");
        s.append("• Media permissions changed significantly on Android 13+.\n");

        status.setText(s.toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }
}
