package com.ryan.autotyper;

import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * Floating widget điều khiển phiên AutoTyper.
 * Widget chỉ gửi broadcast; logic gõ vẫn nằm trong AutoTyperService.
 */
public class FloatingWidgetService extends Service {
    public static final String ACTION_SHOW = "com.ryan.autotyper.FLOATING_SHOW";
    public static final String ACTION_HIDE = "com.ryan.autotyper.FLOATING_HIDE";
    public static final String ACTION_TOGGLE_MENU = "com.ryan.autotyper.FLOATING_TOGGLE_MENU";

    private static final String ACTION_START = "com.ryan.autotyper.ACTION_START";
    private static final String ACTION_PAUSE = "com.ryan.autotyper.ACTION_PAUSE";
    private static final String ACTION_RESUME = "com.ryan.autotyper.ACTION_RESUME";
    private static final String ACTION_STOP = "com.ryan.autotyper.ACTION_STOP";
    private static final String PREFS_NAME = "floating_widget_preferences";
    private static final String PREF_MENU_VISIBLE = "menu_visible";
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
    private View widgetMenu;
    private View expandButton;
    private boolean paused = false;
    private boolean menuVisible = true;
    private android.content.SharedPreferences preferences;
    private BroadcastReceiver toggleReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        menuVisible = preferences.getBoolean(PREF_MENU_VISIBLE, true);

        widgetView = LayoutInflater.from(this).inflate(R.layout.view_floating_widget, null);
        statusText = widgetView.findViewById(R.id.tvWidgetStatus);
        pauseButton = widgetView.findViewById(R.id.btnWidgetPause);
        widgetMenu = widgetView.findViewById(R.id.widgetMenu);
        expandButton = widgetView.findViewById(R.id.btnWidgetExpand);

        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
                .setContentText("Chạm để mở app · Volume Down 2 lần để ẩn/hiện menu")
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
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_TOGGLE_MENU);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(toggleReceiver, filter);
        }
    }

    private void bindActions() {
        widgetView.findViewById(R.id.btnWidgetCollapse).setOnClickListener(v -> collapseMenu());
        expandButton.setOnClickListener(v -> expandMenu());

        widgetView.findViewById(R.id.btnWidgetStart).setOnClickListener(v -> {
            sendCommand(ACTION_START);
            setStatus("Đang chạy");
        });

        pauseButton.setOnClickListener(v -> {
            paused = !paused;
            sendCommand(paused ? ACTION_RESUME : ACTION_PAUSE);
            pauseButton.setImageResource(paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            setStatus(paused ? "Đã tạm dừng" : "Đang chạy");
        });

        widgetView.findViewById(R.id.btnWidgetStop).setOnClickListener(v -> {
            sendCommand(ACTION_STOP);
            setStatus("Đã dừng");
            paused = false;
            pauseButton.setImageResource(android.R.drawable.ic_media_pause);
        });

        widgetView.findViewById(R.id.btnWidgetClose).setOnClickListener(v -> stopSelf());
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

    private void collapseMenu() {
        menuVisible = false;
        if (preferences != null) {
            preferences.edit().putBoolean(PREF_MENU_VISIBLE, false).apply();
        }
        widgetMenu.setVisibility(View.GONE);
        expandButton.setVisibility(View.VISIBLE);
        layoutParams.x = dp(12);
        layoutParams.y = dp(120);
        windowManager.updateViewLayout(widgetView, layoutParams);
    }

    private void expandMenu() {
        menuVisible = true;
        if (preferences != null) {
            preferences.edit().putBoolean(PREF_MENU_VISIBLE, true).apply();
        }
        expandButton.setVisibility(View.GONE);
        widgetMenu.setVisibility(View.VISIBLE);
        windowManager.updateViewLayout(widgetView, layoutParams);
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(action).setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void setStatus(String status) {
        if (statusText != null) statusText.setText(status);
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
        stopForeground(true);
        if (toggleReceiver != null) {
            try {
                unregisterReceiver(toggleReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver chưa được đăng ký hoặc đã được hủy.
            }
        }
        if (widgetView != null && windowManager != null) {
            try {
                windowManager.removeView(widgetView);
            } catch (IllegalArgumentException ignored) {
                // View đã được remove trước đó.
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
