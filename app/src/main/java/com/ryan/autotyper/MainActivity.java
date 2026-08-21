package com.ryan.autotyper;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FILE = 1001;
    private static final int REQUEST_PERMISSION = 1002;

    private TextView tvAccessibilityStatus, tvFileName;
    private Button btnOpenAccessibility, btnChooseFile, btnStart, btnStop;
    private Spinner spinnerRows, spinnerTypingMode;
    private EditText etDelay, etSuffixName;

    private List<String> lines = new ArrayList<>();
    private String fileName = "";

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

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvFileName = findViewById(R.id.tvFileName);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnChooseFile = findViewById(R.id.btnChooseFile);
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

        btnStart.setOnClickListener(v -> startTyping());
        btnStop.setOnClickListener(v -> stopTyping());
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

    private void loadFile(Uri uri) {
        lines.clear();
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            is.close();

            fileName = uri.getLastPathSegment();
            if (fileName != null && fileName.contains(":")) {
                fileName = fileName.substring(fileName.lastIndexOf(":") + 1);
            }
            tvFileName.setText("📄  " + fileName + "  (" + lines.size() + " dòng)");
            tvFileName.setTextColor(0xFF4CAF50);

            Toast.makeText(this, "✅ Đã load " + lines.size() + " dòng", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ Lỗi đọc file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

        String suffixName = etSuffixName.getText().toString().trim();
        int charDelayMs = 50;
        // true = Dán từng dòng, false = Gõ từng chữ
        boolean pasteMode = spinnerTypingMode.getSelectedItemPosition() == 1;

        // Build payload: each "message" is N lines joined
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < lines.size(); i += rows) {
            StringBuilder msg = new StringBuilder();
            for (int j = 0; j < rows && (i + j) < lines.size(); j++) {
                if (j > 0) msg.append("\n");
                msg.append(lines.get(i + j));
            }
            // Add suffix name if provided
            if (!suffixName.isEmpty()) {
                msg.append(" ").append(suffixName);
            }
            if (payload.length() > 0) payload.append("|||");
            payload.append(msg);
        }

        Intent intent = new Intent("com.ryan.autotyper.ACTION_START");
        intent.putExtra("payload", payload.toString());
        intent.putExtra("delay_ms", delayMs);
        intent.putExtra("char_delay_ms", charDelayMs);
        intent.putExtra("paste_mode", pasteMode);
        sendBroadcast(intent);

        String modeLabel = pasteMode ? "Dán từng dòng" : "Gõ từng chữ";
        Toast.makeText(this, "🚀 Đã START (" + modeLabel + ")!\nChuyển sang app chat.\n▲ volume = tiếp tục, ▼ = tạm dừng\n■ STOP = tắt hết", Toast.LENGTH_LONG).show();
    }

    private void stopTyping() {
        Intent intent = new Intent("com.ryan.autotyper.ACTION_STOP");
        sendBroadcast(intent);
        Toast.makeText(this, "⏹️ Đã STOP — tắt hết (kể cả phím volume)", Toast.LENGTH_SHORT).show();
    }
}
