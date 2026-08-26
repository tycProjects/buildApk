package com.ryan.autotyper;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FILE = 1001;
    private static final int REQUEST_PERMISSION = 1002;

    private TextView tvAccessibilityStatus, tvFileName;
    private Button btnOpenAccessibility, btnChooseFile, btnFloatingWidget, btnStart, btnStop;
    private Spinner spinnerRows, spinnerTypingMode;
    private EditText etDelay, etSuffixName;

    private final List<String> lines = new ArrayList<>();
    private String fileName = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupSpinner();
        setupListeners();
        checkPermissions();
        updateAccessibilityStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvFileName = findViewById(R.id.tvFileName);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnChooseFile = findViewById(R.id.btnChooseFile);
        btnFloatingWidget = findViewById(R.id.btnFloatingWidget);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        spinnerRows = findViewById(R.id.spinnerRows);
        spinnerTypingMode = findViewById(R.id.spinnerTypingMode);
        etDelay = findViewById(R.id.etDelay);
        etSuffixName = findViewById(R.id.etSuffixName);
    }

    private void setupSpinner() {
        String[] items = {"1", "2", "3", "4", "5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items);
        spinnerRows.setAdapter(adapter);

        String[] modes = {"Gõ từng chữ", "Dán từng dòng"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modes);
        spinnerTypingMode.setAdapter(modeAdapter);
    }

    private void setupListeners() {
        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Tìm và bật \"YEU EM MOI VU TRU\"", Toast.LENGTH_LONG).show();
        });

        btnChooseFile.setOnClickListener(v -> openFilePicker());

        btnFloatingWidget.setOnClickListener(v -> toggleFloatingWidget());

        btnStart.setOnClickListener(v -> startTyping());
        btnStop.setOnClickListener(v -> stopTyping());
    }

    private void toggleFloatingWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            Toast.makeText(this, "Hãy bật quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
            Toast.makeText(this, "Hãy cho phép thông báo rồi bấm mở menu nổi lại", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, FloatingWidgetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Đã bật menu nổi. Volume Down 2 lần để ẩn/hiện.", Toast.LENGTH_SHORT).show();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION);
            }
        }
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            tvAccessibilityStatus.setText("✅  Đã bật Trợ năng (Accessibility)");
            tvAccessibilityStatus.setTextColor(0xFF4CAF50);
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_ok);
        } else {
            tvAccessibilityStatus.setText("⚠️  Chưa bật Trợ năng (Accessibility)");
            tvAccessibilityStatus.setTextColor(0xFFFF6B6B);
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_warn);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                loadFile(uri);
            }
        }
    }

    private void loadFile(final Uri uri) {
        tvFileName.setText("⏳ Đang đọc file...");
        tvFileName.setTextColor(0xFFFFC107);
        btnChooseFile.setEnabled(false);
        btnStart.setEnabled(false);

        ioExecutor.execute(() -> {
            final List<String> loaded = new ArrayList<>();
            String name = "";
            String error = null;
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) {
                    error = "Không mở được file";
                } else {
                    // Buffer lớn hơn cho file lớn
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is), 64 * 1024);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        loaded.add(line);
                    }
                    reader.close();
                    is.close();

                    name = uri.getLastPathSegment();
                    if (name != null && name.contains(":")) {
                        name = name.substring(name.lastIndexOf(":") + 1);
                    }
                }
            } catch (OutOfMemoryError oom) {
                error = "File quá lớn, hết bộ nhớ";
                loaded.clear();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : "Lỗi đọc file";
            }

            final String err = error;
            final String finalName = name != null ? name : "";
            mainHandler.post(() -> {
                btnChooseFile.setEnabled(true);
                btnStart.setEnabled(true);
                lines.clear();
                if (err != null) {
                    tvFileName.setText("❌ " + err);
                    tvFileName.setTextColor(0xFFFF6B6B);
                    Toast.makeText(this, "❌ " + err, Toast.LENGTH_LONG).show();
                    return;
                }
                lines.addAll(loaded);
                fileName = finalName;
                tvFileName.setText("📄  " + fileName + "  (" + lines.size() + " dòng)");
                tvFileName.setTextColor(0xFF4CAF50);
                Toast.makeText(this, "✅ Đã load " + lines.size() + " dòng", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void startTyping() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "❌ Hãy bật quyền Trợ năng trước!", Toast.LENGTH_LONG).show();
            return;
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "❌ Chưa chọn file .txt!", Toast.LENGTH_SHORT).show();
            return;
        }

        int rows = Integer.parseInt(spinnerRows.getSelectedItem().toString());
        int delayMs = 1000;
        try {
            delayMs = Integer.parseInt(etDelay.getText().toString());
            if (delayMs < 0) delayMs = 0;
        } catch (Exception ignored) {}

        final String suffixName = etSuffixName.getText().toString().trim();
        final int charDelayMs = 50;
        final boolean pasteMode = spinnerTypingMode.getSelectedItemPosition() == 1;
        final int finalRows = rows;
        final int finalDelayMs = delayMs;

        btnStart.setEnabled(false);
        Toast.makeText(this, "⏳ Đang chuẩn bị " + lines.size() + " dòng...", Toast.LENGTH_SHORT).show();

        // Build message list off UI thread — không gửi qua Intent (tránh TransactionTooLarge)
        ioExecutor.execute(() -> {
            final List<String> messages = new ArrayList<>((lines.size() / finalRows) + 1);
            try {
                for (int i = 0; i < lines.size(); i += finalRows) {
                    StringBuilder msg = new StringBuilder();
                    for (int j = 0; j < finalRows && (i + j) < lines.size(); j++) {
                        if (j > 0) msg.append('\n');
                        msg.append(lines.get(i + j));
                    }
                    if (!suffixName.isEmpty()) {
                        msg.append(' ').append(suffixName);
                    }
                    messages.add(msg.toString());
                }
            } catch (OutOfMemoryError oom) {
                mainHandler.post(() -> {
                    btnStart.setEnabled(true);
                    Toast.makeText(this, "❌ Hết bộ nhớ khi chuẩn bị tin", Toast.LENGTH_LONG).show();
                });
                return;
            }

            mainHandler.post(() -> {
                btnStart.setEnabled(true);
                // Truyền list qua static holder — không qua Intent extra
                AutoTyperService.setPendingMessages(messages, finalDelayMs, charDelayMs, pasteMode);

                Intent intent = new Intent("com.ryan.autotyper.ACTION_START")
                        .setPackage(getPackageName());
                // Chỉ gửi flag nhẹ, data nằm ở static
                intent.putExtra("use_pending", true);
                sendBroadcast(intent);

                String modeLabel = pasteMode ? "Dán từng dòng" : "Gõ từng chữ";
                Toast.makeText(this,
                        "🚀 Đã START (" + modeLabel + ") — " + messages.size() + " tin\n"
                                + "Chuyển sang app chat.\n▲ volume = tiếp tục, ▼ = tạm dừng\n■ STOP = tắt hết",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void stopTyping() {
        Intent intent = new Intent("com.ryan.autotyper.ACTION_STOP")
                .setPackage(getPackageName());
        sendBroadcast(intent);
        Toast.makeText(this, "⏹️ Đã STOP — tắt hết (kể cả phím volume)", Toast.LENGTH_SHORT).show();
    }
}
