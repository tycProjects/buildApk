package com.example.robloxgraphicstool;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 100;
    private TextView status;
    private Button authButton;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> {
            if (requestCode == REQUEST_CODE) updateStatus();
        };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 45, 40, 40);

        TextView title = new TextView(this);
        title.setText("🎮 Roblox Graphics Tool");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        box.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 25, 0, 15);
        box.addView(status);

        authButton = new Button(this);
        authButton.setText("🔐 Ủy quyền Shizuku");
        authButton.setOnClickListener(v -> requestShizuku());
        box.addView(authButton);

        addButton(box, "⚡ Giảm đồ họa", v -> requirePermission("Đã chọn giảm đồ họa"));
        addButton(box, "🟡 Đồ họa trung bình", v -> requirePermission("Đã chọn đồ họa trung bình"));
        addButton(box, "🟢 Khôi phục", v -> requirePermission("Đã chọn khôi phục"));
        addButton(box, "🚀 Mở Roblox", v -> openRoblox());

        setContentView(box);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        updateStatus();
    }

    private void addButton(LinearLayout box, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        box.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }

    private boolean hasShizukuPermission() {
        try {
            return !Shizuku.isPreV11() &&
                   Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateStatus() {
        try {
            if (Shizuku.isPreV11()) {
                status.setText("Shizuku quá cũ: cần Shizuku API v11+.");
                authButton.setEnabled(false);
            } else if (hasShizukuPermission()) {
                status.setText("🟢 Shizuku: ĐÃ ỦY QUYỀN");
                authButton.setText("✅ Đã ủy quyền");
                authButton.setEnabled(false);
            } else {
                status.setText("🔴 Shizuku: CHƯA ỦY QUYỀN");
                authButton.setText("🔐 Ủy quyền Shizuku");
                authButton.setEnabled(true);
            }
        } catch (Exception e) {
            status.setText("🔴 Shizuku chưa chạy. Hãy mở Shizuku và khởi động dịch vụ.");
        }
    }

    private void requestShizuku() {
        try {
            if (Shizuku.isPreV11()) {
                toast("Cần Shizuku v11 trở lên.");
                return;
            }
            if (hasShizukuPermission()) {
                updateStatus();
                return;
            }
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Exception e) {
            toast("Shizuku chưa chạy hoặc chưa kết nối.");
        }
    }

    private void requirePermission(String action) {
        if (!hasShizukuPermission()) {
            toast("Hãy bấm 'Ủy quyền Shizuku' trước.");
            requestShizuku();
            return;
        }
        // Chỗ này chỉ là UI hook. Không tự sửa dữ liệu Roblox.
        toast(action);
    }

    private void openRoblox() {
        String[] packages = {
            "com.roblox.client.vnggames",
            "com.roblox.client"
        };

        for (String pkg : packages) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                startActivity(intent);
                return;
            }
        }

        toast("Không tìm thấy Roblox/Roblox VN đã cài.");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
