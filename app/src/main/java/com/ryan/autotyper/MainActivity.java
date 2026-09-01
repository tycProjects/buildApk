package com.ryan.autotyper;

import android.Manifest;
import android.animation.ValueAnimator;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

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
    private static final int REQUEST_WIDGET_ICON = 1003;
    private static final String SETTINGS_PREFS = "autotyper_settings";
    private static final String PREF_DELAY_MS = "delay_ms";

    private TextView tvAccessibilityStatus, tvFileName, tvAppName, tvAuthor;
    private LinearLayout fileLibraryList;
    private ValueAnimator appNameGradientAnimator, authorGradientAnimator;
    private Button btnOpenAccessibility, btnChooseFile, btnFloatingWidget, btnStart, btnStop;
    private Spinner spinnerRows, spinnerTypingMode;
    private EditText etDelay, etSuffixName;
    private CheckBox cbAutoRepeat;
    private Button btnChooseWidgetIcon;
    private ImageView imgWidgetIconPreview;

    private final List<String> lines = new ArrayList<>();
    private String fileName = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextFileStore fileStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        fileStore = new TextFileStore(this);
        setupSpinner();
        setupListeners();
        renderFileLibrary();
        startGradientTextAnimations();
        updateAccessibilityStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
        if (fileStore != null) renderFileLibrary();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appNameGradientAnimator != null) appNameGradientAnimator.cancel();
        if (authorGradientAnimator != null) authorGradientAnimator.cancel();
        ioExecutor.shutdownNow();
    }

    private void initViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvFileName = findViewById(R.id.tvFileName);
        tvAppName = findViewById(R.id.tvAppName);
        tvAuthor = findViewById(R.id.tvAuthor);
        fileLibraryList = findViewById(R.id.fileLibraryList);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        btnChooseFile = findViewById(R.id.btnChooseFile);
        btnFloatingWidget = findViewById(R.id.btnFloatingWidget);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        spinnerRows = findViewById(R.id.spinnerRows);
        spinnerTypingMode = findViewById(R.id.spinnerTypingMode);
        etDelay = findViewById(R.id.etDelay);
        etSuffixName = findViewById(R.id.etSuffixName);
        cbAutoRepeat = findViewById(R.id.cbAutoRepeat);
        btnChooseWidgetIcon = findViewById(R.id.btnChooseWidgetIcon);
        imgWidgetIconPreview = findViewById(R.id.imgWidgetIconPreview);
        restoreWidgetIconPreview();
        cbAutoRepeat.setChecked(getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getBoolean("auto_repeat", false));
        String savedDelay = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getString(PREF_DELAY_MS, "1000");
        etDelay.setText(savedDelay);
    }

    private void setupSpinner() {
        String[] items = {"1", "2", "3", "4", "5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerRows.setAdapter(adapter);

        String[] modes = {"Gõ từng chữ", "Dán từng dòng"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerTypingMode.setAdapter(modeAdapter);
    }

    private void startGradientTextAnimations() {
        if (tvAppName != null) {
            tvAppName.post(() -> appNameGradientAnimator = animateGradientText(tvAppName,
                    new int[]{0xFF73E1B1, 0xFF73B8FF, 0xFF9B8CFF, 0xFFFF7184}));
        }
        if (tvAuthor != null) {
            tvAuthor.post(() -> authorGradientAnimator = animateGradientText(tvAuthor,
                    new int[]{0xFF73B8FF, 0xFF9B8CFF, 0xFF73E1B1}));
        }
    }

    private ValueAnimator animateGradientText(TextView view, int[] colors) {
        final int width = Math.max(view.getWidth(), dp(180));
        final LinearGradient gradient = new LinearGradient(-width, 0, width, 0,
                colors, null, Shader.TileMode.MIRROR);
        final Matrix matrix = new Matrix();
        view.getPaint().setShader(gradient);
        ValueAnimator animator = ValueAnimator.ofFloat(-width, width);
        animator.setDuration(4200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value -> {
            matrix.setTranslate((Float) value.getAnimatedValue(), 0f);
            gradient.setLocalMatrix(matrix);
            view.invalidate();
        });
        animator.start();
        return animator;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void renderFileLibrary() {
        if (fileLibraryList == null || fileStore == null) return;
        fileLibraryList.removeAllViews();
        List<java.io.File> files = fileStore.listFiles();
        List<String> selectedNames = fileStore.getSelectedNames();
        tvFileName.setText(selectedNames.isEmpty()
                ? "Chưa chọn file"
                : selectedNames.size() + " file đã chọn");
        tvFileName.setTextColor(ContextCompat.getColor(this,
                selectedNames.isEmpty() ? R.color.text_secondary : R.color.mint));

        if (files.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có file TXT trong thư viện.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            empty.setTextSize(11);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            empty.setBackgroundResource(R.drawable.bg_widget_row_modern);
            fileLibraryList.addView(empty);
            return;
        }

        int rowIndex = 0;
        for (java.io.File file : files) {
            final String name = file.getName();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(46));
            row.setPadding(0, 0, dp(6), 0);
            row.setBackgroundResource(R.drawable.bg_widget_row_modern);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(name);
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            checkBox.setTextSize(12);
            checkBox.setSingleLine(true);
            checkBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
            checkBox.setGravity(android.view.Gravity.CENTER_VERTICAL);
            checkBox.setPadding(dp(10), 0, dp(8), 0);
            checkBox.setChecked(selectedNames.contains(name));
            checkBox.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{ContextCompat.getColor(this, R.color.mint),
                            ContextCompat.getColor(this, R.color.line_bright)}));
            row.addView(checkBox, new LinearLayout.LayoutParams(0, dp(46), 1f));

            Button deleteButton = new Button(this);
            deleteButton.setText("XÓA");
            deleteButton.setTextSize(10);
            deleteButton.setTextColor(ContextCompat.getColor(this, R.color.coral));
            deleteButton.setAllCaps(false);
            deleteButton.setMinHeight(0);
            deleteButton.setMinWidth(0);
            deleteButton.setPadding(dp(8), 0, dp(8), 0);
            deleteButton.setContentDescription("Xóa file " + name);
            deleteButton.setBackgroundResource(R.drawable.bg_action_stop);
            row.addView(deleteButton, new LinearLayout.LayoutParams(dp(58), dp(34)));

            checkBox.setOnClickListener(v -> {
                fileStore.setSelection(name, checkBox.isChecked());
                renderFileLibrary();
                sendBroadcast(new Intent(FilePickerActivity.ACTION_FILE_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
            });
            deleteButton.setOnClickListener(v -> confirmDeleteFile(name));

            row.setAlpha(0f);
            row.setTranslationY(dp(5));
            fileLibraryList.addView(row);
            row.animate().alpha(1f).translationY(0f)
                    .setStartDelay(rowIndex * 30L)
                    .setDuration(170L)
                    .start();
            rowIndex++;
        }
    }

    private void confirmDeleteFile(String name) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xóa file TXT?")
                .setMessage("Bạn có chắc muốn xóa \"" + name + "\" không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    if (fileStore.delete(name)) {
                        renderFileLibrary();
                        sendBroadcast(new Intent(FilePickerActivity.ACTION_FILE_LIBRARY_CHANGED)
                                .setPackage(getPackageName()));
                    }
                })
                .show();
    }

    private void setupListeners() {
        btnOpenAccessibility.setOnClickListener(v -> {
            pressAnimation(v);
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnChooseFile.setOnClickListener(v -> {
            pressAnimation(v);
            openFilePicker();
        });

        btnFloatingWidget.setOnClickListener(v -> {
            pressAnimation(v);
            toggleFloatingWidget();
        });

        btnStart.setOnClickListener(v -> {
            pressAnimation(v);
            startTyping();
        });
        btnStop.setOnClickListener(v -> {
            pressAnimation(v);
            stopTyping();
        });

        btnChooseWidgetIcon.setOnClickListener(v -> {
            pressAnimation(v);
            openWidgetIconPicker();
        });

        etDelay.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_DELAY_MS, s.toString()).apply();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void pressAnimation(View view) {
        view.animate().cancel();
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
        view.animate()
                .scaleX(0.96f).scaleY(0.96f).alpha(0.88f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(55L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .setDuration(170L)
                        .start())
                .start();
    }

    private void toggleFloatingWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
            return;
        }

        Intent intent = new Intent(this, FloatingWidgetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }


    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            tvAccessibilityStatus.setText("Đã bật Trợ năng");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.mint));
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_ok_modern);
        } else {
            tvAccessibilityStatus.setText("Chưa bật Trợ năng");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.coral));
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_warn_modern);
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

    private void openWidgetIconPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_WIDGET_ICON);
    }

    private void restoreWidgetIconPreview() {
        String savedUri = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getString("widget_icon_uri", "");
        if (!savedUri.isEmpty() && imgWidgetIconPreview != null) {
            imgWidgetIconPreview.setImageURI(Uri.parse(savedUri));
        }
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
        if (requestCode == REQUEST_WIDGET_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Một số DocumentProvider không hỗ trợ quyền persistable.
            }
            getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                    .putString("widget_icon_uri", uri.toString()).apply();
            if (imgWidgetIconPreview != null) imgWidgetIconPreview.setImageURI(uri);
            sendBroadcast(new Intent(FloatingWidgetService.ACTION_WIDGET_ICON_CHANGED)
                    .setPackage(getPackageName()));
            return;
        }
        if (requestCode == REQUEST_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                loadFile(uri);
            }
        }
    }

    private void loadFile(final Uri uri) {
        tvFileName.setText("Đang đọc file...");
        tvFileName.setTextColor(ContextCompat.getColor(this, R.color.amber));
        btnChooseFile.setEnabled(false);
        btnStart.setEnabled(false);

        ioExecutor.execute(() -> {
            final List<String> loaded = new ArrayList<>();
            String name = "";
            String error = null;
            try {
                // Lưu bản sao vào thư viện nội bộ trước, sau đó đọc lại từ bản sao.
                // Nhờ vậy file thêm từ giao diện chính và menu nổi dùng chung một danh sách.
                String storedName;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Không mở được file");
                    String originalName = FileNameResolver.getDisplayName(this, uri);
                    storedName = fileStore.importTxt(input, originalName);
                }
                loaded.addAll(fileStore.readLines(storedName));
                name = storedName;
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
                    tvFileName.setText(err);
                    tvFileName.setTextColor(ContextCompat.getColor(this, R.color.coral));
                    return;
                }
                lines.addAll(loaded);
                fileName = finalName;
                renderFileLibrary();
                sendBroadcast(new Intent(FilePickerActivity.ACTION_FILE_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
            });
        });
    }

    private void startTyping() {
        if (!isAccessibilityServiceEnabled()) {
            return;
        }
        final List<String> selectedFiles = fileStore.getSelectedNames();
        if (selectedFiles.isEmpty()) {
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
        final boolean autoRepeat = cbAutoRepeat.isChecked();
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                .putBoolean("auto_repeat", autoRepeat).apply();
        final int finalRows = rows;
        final int finalDelayMs = delayMs;

        btnStart.setEnabled(false);

        ioExecutor.execute(() -> {
            final List<String> sourceLines = new ArrayList<>();
            try {
                for (String selectedFile : selectedFiles) {
                    for (String line : fileStore.readLines(selectedFile)) {
                        if (!line.trim().isEmpty()) sourceLines.add(line);
                    }
                }
                if (sourceLines.isEmpty()) throw new IllegalArgumentException("Các file đã chọn không có nội dung");

                final List<String> messages = new ArrayList<>((sourceLines.size() / finalRows) + 1);
                for (int i = 0; i < sourceLines.size(); i += finalRows) {
                    StringBuilder msg = new StringBuilder();
                    for (int j = 0; j < finalRows && (i + j) < sourceLines.size(); j++) {
                        if (j > 0) msg.append('\n');
                        msg.append(sourceLines.get(i + j));
                    }
                    if (!suffixName.isEmpty()) msg.append(' ').append(suffixName);
                    messages.add(msg.toString());
                }

                mainHandler.post(() -> {
                    btnStart.setEnabled(true);
                    AutoTyperService.setPendingMessages(messages, finalDelayMs, charDelayMs, pasteMode, autoRepeat);
                    Intent intent = new Intent("com.ryan.autotyper.ACTION_START")
                            .setPackage(getPackageName());
                    intent.putExtra("use_pending", true);
                    sendBroadcast(intent);
                    String modeLabel = pasteMode ? "Dán từng dòng" : "Gõ từng chữ";
                });
            } catch (OutOfMemoryError oom) {
                mainHandler.post(() -> {
                    btnStart.setEnabled(true);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    btnStart.setEnabled(true);
                });
            }
        });
    }

    private void stopTyping() {
        Intent intent = new Intent("com.ryan.autotyper.ACTION_STOP")
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
