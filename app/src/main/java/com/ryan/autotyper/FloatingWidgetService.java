package com.ryan.autotyper;

import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Floating widget điều khiển phiên AutoTyper.
 * Widget chỉ gửi broadcast; logic gõ vẫn nằm trong AutoTyperService.
 */
public class FloatingWidgetService extends Service {
    public static final String ACTION_SHOW = "com.ryan.autotyper.FLOATING_SHOW";
    public static final String ACTION_HIDE = "com.ryan.autotyper.FLOATING_HIDE";
    public static final String ACTION_TOGGLE_MENU = "com.ryan.autotyper.FLOATING_TOGGLE_MENU";
    public static final String ACTION_WIDGET_ICON_CHANGED = "com.ryan.autotyper.WIDGET_ICON_CHANGED";

    private static final String ACTION_START = "com.ryan.autotyper.ACTION_START";
    private static final String ACTION_PAUSE = "com.ryan.autotyper.ACTION_PAUSE";
    private static final String ACTION_RESUME = "com.ryan.autotyper.ACTION_RESUME";
    private static final String ACTION_STOP = "com.ryan.autotyper.ACTION_STOP";
    private static final String PREFS_NAME = "floating_widget_preferences";
    private static final String PREF_MENU_VISIBLE = "menu_visible";
    private static final String SETTINGS_PREFS = "autotyper_settings";
    private static final String PREF_DELAY_MS = "delay_ms";
    private static final String PREF_WIDGET_MODE = "widget_typing_mode";
    public static final String PREF_AUTO_START_BOOT = "auto_start_after_boot";
    private static final String NOTIFICATION_CHANNEL_ID = "floating_widget_channel";
    private static final int NOTIFICATION_ID = 7001;

    public static void setAutoStartAfterBoot(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AUTO_START_BOOT, enabled)
                .apply();
    }

    public static boolean isAutoStartAfterBoot(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_AUTO_START_BOOT, false);
    }

    private WindowManager windowManager;
    private View widgetView;
    private WindowManager.LayoutParams layoutParams;
    private TextView statusText;
    private ImageButton pauseButton;
    private TextView pauseLabel;
    private View widgetMenu;
    private View expandButton;
    private boolean viewAttached = false;
    private boolean paused = false;
    private boolean menuVisible = true;
    private android.content.SharedPreferences preferences;
    private BroadcastReceiver toggleReceiver;
    private BroadcastReceiver libraryReceiver;
    private TextFileStore fileStore;
    private LinearLayout fileList;
    private Spinner modeSpinner;
    private TextView delaySync;
    private android.widget.CheckBox autoRepeatCheckBox;
    private static final String ACTION_FILE_LIBRARY_CHANGED =
            "com.ryan.autotyper.ACTION_FILE_LIBRARY_CHANGED";
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        menuVisible = preferences.getBoolean(PREF_MENU_VISIBLE, true);

        widgetView = LayoutInflater.from(this).inflate(R.layout.view_floating_widget, null);
        statusText = widgetView.findViewById(R.id.tvWidgetStatus);
        pauseButton = widgetView.findViewById(R.id.btnWidgetPause);
        pauseLabel = widgetView.findViewById(R.id.tvWidgetPauseLabel);
        widgetMenu = widgetView.findViewById(R.id.widgetMenu);
        expandButton = widgetView.findViewById(R.id.btnWidgetExpand);
        applyCustomIcon();
        fileStore = new TextFileStore(this);
        fileList = widgetView.findViewById(R.id.widgetFileList);
        modeSpinner = widgetView.findViewById(R.id.spinnerWidgetMode);
        delaySync = widgetView.findViewById(R.id.tvWidgetDelaySync);
        autoRepeatCheckBox = widgetView.findViewById(R.id.cbWidgetAutoRepeat);
        if (autoRepeatCheckBox != null) {
            autoRepeatCheckBox.setChecked(getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                    .getBoolean("auto_repeat", false));
        }
        if (modeSpinner != null) {
            ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"Gõ từng chữ", "Dán từng dòng"});
            modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            modeSpinner.setAdapter(modeAdapter);
            modeSpinner.setSelection(preferences.getInt(PREF_WIDGET_MODE, 0));
        }
        updateDelaySyncLabel();
        renderFileList();

        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.x = dp(12);
        layoutParams.y = dp(120);

        bindActions();
        bindDragGesture();
        registerToggleReceiver();
        windowManager.addView(widgetView, layoutParams);
        viewAttached = true;

        // Khôi phục trạng thái menu của lần chạy trước.
        if (!menuVisible) {
            collapseMenu();
        }
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Floating Widget",
                    android.app.NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Giữ menu nổi hoạt động khi app chạy nền");
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags);

        android.app.Notification.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            notificationBuilder = new android.app.Notification.Builder(this);
        }

        android.app.Notification notification = notificationBuilder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Floating Widget đang hoạt động")
                .setContentText("Menu nổi đang hoạt động")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(android.app.Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void registerToggleReceiver() {
        toggleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_TOGGLE_MENU.equals(intent.getAction())) {
                    if (menuVisible) collapseMenu();
                    else expandMenu();
                } else if (ACTION_FILE_LIBRARY_CHANGED.equals(intent.getAction())) {
                    renderFileList();
                } else if (ACTION_WIDGET_ICON_CHANGED.equals(intent.getAction())) {
                    applyCustomIcon();
                } else if (ACTION_STOP.equals(intent.getAction())) {
                    paused = false;
                    if (pauseButton != null) pauseButton.setImageResource(R.drawable.ic_widget_pause);
                    if (pauseLabel != null) pauseLabel.setText("PAUSE");
                    mainHandler.postDelayed(FloatingWidgetService.this::stopSelf, 90L);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_TOGGLE_MENU);
        filter.addAction(ACTION_FILE_LIBRARY_CHANGED);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_WIDGET_ICON_CHANGED);
        ContextCompat.registerReceiver(this, toggleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void bindActions() {

        expandButton.setOnClickListener(v -> {
            pressAnimation(v);
            expandMenu();
        });

        widgetView.findViewById(R.id.btnWidgetAddFile).setOnClickListener(v -> {
            pressAnimation(v);
            Intent picker = new Intent(this, FilePickerActivity.class);
            picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(picker);
        });

        widgetView.findViewById(R.id.btnWidgetStart).setOnClickListener(v -> {
            pressAnimation(v);
            startSelectedFile();
        });

        pauseButton.setOnClickListener(v -> {
            pressAnimation(v);
            paused = !paused;
            sendCommand(paused ? ACTION_RESUME : ACTION_PAUSE);
            pauseButton.setImageResource(paused
                    ? R.drawable.ic_widget_play
                    : R.drawable.ic_widget_pause);
            if (pauseLabel != null) pauseLabel.setText(paused ? "RESUME" : "PAUSE");
            setStatus(paused ? "Đã tạm dừng" : "Đang chạy");
        });

        widgetView.findViewById(R.id.btnWidgetStop).setOnClickListener(v -> {
            pressAnimation(v);
            sendCommand(ACTION_STOP);
            paused = false;
            pauseButton.setImageResource(R.drawable.ic_widget_pause);
            if (pauseLabel != null) pauseLabel.setText("PAUSE");
            mainHandler.postDelayed(this::stopSelf, 90L);
        });

        widgetView.findViewById(R.id.btnWidgetClose).setOnClickListener(v -> {
            pressAnimation(v);
            collapseMenu();
        });
    }

    private void bindDragGesture() {
        widgetView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float touchX;
            private float touchY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX - (int) (event.getRawX() - touchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - touchY);
                        windowManager.updateViewLayout(widgetView, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Trả false nếu muốn click đi qua; ở đây các nút vẫn nhận click riêng.
                        return System.currentTimeMillis() - downTime >= 120;
                    default:
                        return false;
                }
            }
        });
    }

    private void renderFileList() {
        if (fileList == null || fileStore == null) return;
        fileList.removeAllViews();
        List<File> files = fileStore.listFiles();
        if (files.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có file. Hãy thêm file TXT.");
            empty.setTextColor(getResources().getColor(R.color.text_muted));
            empty.setTextSize(11);
            empty.setPadding(dp(10), dp(12), dp(10), dp(12));
            empty.setBackgroundResource(R.drawable.bg_widget_row_modern);
            fileList.addView(empty);
            return;
        }
        int rowIndex = 0;
        for (File file : files) {
            String name = file.getName();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(4), dp(4), dp(4));
            row.setBackgroundResource(R.drawable.bg_widget_row_modern);

            CheckBox check = new CheckBox(this);
            check.setChecked(fileStore.isSelected(name));
            check.setButtonTintList(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{getResources().getColor(R.color.mint),
                            getResources().getColor(R.color.line_bright)}));
            check.setContentDescription("Chọn " + name);
            check.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(38)));
            check.setOnClickListener(v -> {
                fileStore.setSelection(name, check.isChecked());
                renderFileList();
            });

            TextView title = new TextView(this);
            title.setText(name);
            title.setTextColor(getResources().getColor(R.color.text_primary));
            title.setTextSize(12);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1));

            ImageButton delete = new ImageButton(this);
            delete.setImageResource(android.R.drawable.ic_menu_delete);
            delete.setColorFilter(getResources().getColor(R.color.coral));
            delete.setBackgroundResource(R.drawable.bg_widget_icon_modern);
            delete.setPadding(dp(8), dp(8), dp(8), dp(8));
            delete.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
            delete.setContentDescription("Xóa " + name);
            delete.setOnClickListener(v -> {
                fileStore.delete(name);
                renderFileList();
            });

            row.addView(check);
            row.addView(title);
            row.addView(delete);
            row.setOnClickListener(v -> {
                fileStore.toggleSelection(name);
                renderFileList();
                setStatus(fileStore.isSelected(name) ? "Đã chọn" : "Đã bỏ chọn");
            });
            row.setAlpha(0f);
            row.setTranslationY(dp(6));
            fileList.addView(row);
            row.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(rowIndex * 35L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .setDuration(180L)
                    .start();
            rowIndex++;
        }
    }

    private void startSelectedFile() {
        final List<String> selectedFiles = fileStore.getSelectedNames();
        if (selectedFiles.isEmpty()) {
            setStatus("Chưa chọn file");
            return;
        }
        int delayMs = readSharedDelayMs();
        final int finalDelayMs = Math.max(0, Math.min(delayMs, 3600000));
        updateDelaySyncLabel();
        final int finalCharDelayMs = 50;
        final int selectedMode = modeSpinner.getSelectedItemPosition();
        final boolean pasteMode = selectedMode == 1;
        final boolean autoRepeat = autoRepeatCheckBox != null && autoRepeatCheckBox.isChecked();
        preferences.edit()
                .putInt(PREF_WIDGET_MODE, selectedMode)
                .apply();
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
                .putBoolean("auto_repeat", autoRepeat).apply();
        setStatus("Đang chuẩn bị " + selectedFiles.size() + " file");
        fileExecutor.execute(() -> {
            try {
                List<String> messages = new ArrayList<>();
                for (String selected : selectedFiles) {
                    for (String line : fileStore.readLines(selected)) {
                        if (!line.trim().isEmpty()) messages.add(line);
                    }
                }
                if (messages.isEmpty()) throw new IllegalArgumentException("Các file đã chọn không có nội dung");
                AutoTyperService.setPendingMessages(messages, finalDelayMs, finalCharDelayMs, pasteMode, autoRepeat);
                sendCommand(ACTION_START);
                mainHandler.post(() -> setStatus("Đang chạy"));
            } catch (Exception error) {
                mainHandler.post(() -> setStatus("Lỗi file"));
            }
        });
    }

    private void applyCustomIcon() {
        if (!(expandButton instanceof ImageButton)) return;
        ImageButton iconButton = (ImageButton) expandButton;
        String savedUri = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
                .getString("widget_icon_uri", "");
        if (savedUri.isEmpty()) {
            iconButton.setImageResource(R.drawable.ic_widget_play);
            return;
        }
        try {
            iconButton.setImageURI(android.net.Uri.parse(savedUri));
        } catch (RuntimeException ignored) {
            iconButton.setImageResource(R.drawable.ic_widget_play);
        }
    }

    private int readSharedDelayMs() {
        android.content.SharedPreferences settings = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String raw;
        try {
            raw = settings.getString(PREF_DELAY_MS, "1000");
        } catch (ClassCastException legacyType) {
            raw = String.valueOf(settings.getInt(PREF_DELAY_MS, 1000));
        }
        try {
            return Math.max(0, Math.min(Integer.parseInt(raw.trim()), 3600000));
        } catch (Exception ignored) {
            return 1000;
        }
    }

    private void updateDelaySyncLabel() {
        if (delaySync == null) return;
        delaySync.setText(readSharedDelayMs() + " ms");
    }

    private void pressAnimation(View view) {
        view.animate().cancel();
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
        view.animate()
                .scaleX(0.91f).scaleY(0.91f).alpha(0.82f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(65L)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                        .setDuration(185L)
                        .start())
                .start();
    }

    private void collapseMenu() {
        menuVisible = false;
        if (preferences != null) {
            preferences.edit().putBoolean(PREF_MENU_VISIBLE, false).apply();
        }
        // Chạy exit animation trước khi remove khỏi WindowManager để không bị biến mất đột ngột.
        widgetMenu.animate().cancel();
        widgetMenu.animate()
                .alpha(0f).scaleX(0.93f).scaleY(0.93f)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .setDuration(145L)
                .withEndAction(() -> {
                    widgetMenu.setVisibility(View.GONE);
                    widgetMenu.setAlpha(1f);
                    widgetMenu.setScaleX(1f);
                    widgetMenu.setScaleY(1f);
                    expandButton.setAlpha(0f);
                    expandButton.setScaleX(0.82f);
                    expandButton.setScaleY(0.82f);
                    expandButton.setVisibility(View.VISIBLE);
                    expandButton.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                            .setDuration(180L).start();
                })
                .start();
    }

    private void expandMenu() {
        menuVisible = true;
        if (preferences != null) {
            preferences.edit().putBoolean(PREF_MENU_VISIBLE, true).apply();
        }
        if (!viewAttached && windowManager != null && widgetView != null) {
            windowManager.addView(widgetView, layoutParams);
            viewAttached = true;
        }
        expandButton.animate().cancel();
        expandButton.setVisibility(View.GONE);
        widgetMenu.animate().cancel();
        widgetMenu.setAlpha(0f);
        widgetMenu.setScaleX(0.92f);
        widgetMenu.setScaleY(0.92f);
        widgetMenu.setTranslationY(dp(10));
        widgetMenu.setVisibility(View.VISIBLE);
        widgetMenu.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f))
                .setDuration(260L)
                .start();
        windowManager.updateViewLayout(widgetView, layoutParams);
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(action).setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void setStatus(String status) {
        if (statusText == null) return;
        statusText.animate().cancel();
        statusText.animate()
                .alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(75L)
                .withEndAction(() -> {
                    statusText.setText(status);
                    statusText.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                            .setDuration(160L)
                            .start();
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Đảm bảo notification được khôi phục nếu Android tạo lại service.
        startAsForeground();
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            // stopSelf() là dừng có chủ đích; Android sẽ không tự khởi động lại.
            stopSelf();
            return START_NOT_STICKY;
        }

        // Nếu process bị hệ thống kill, Android sẽ tạo lại service.
        // Intent có thể là null khi service được khôi phục.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        fileExecutor.shutdownNow();
        stopForeground(true);
        if (toggleReceiver != null) {
            try {
                unregisterReceiver(toggleReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver chưa được đăng ký hoặc đã được hủy.
            }
        }
        if (viewAttached && widgetView != null && windowManager != null) {
            try {
                windowManager.removeView(widgetView);
            } catch (IllegalArgumentException ignored) {
                // View đã được remove trước đó.
            }
            viewAttached = false;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
