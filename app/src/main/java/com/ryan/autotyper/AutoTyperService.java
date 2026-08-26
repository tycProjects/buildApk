package com.ryan.autotyper;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class AutoTyperService extends AccessibilityService {

    private static final String TAG = "YeuEmMoiVuTru";
    private BroadcastReceiver receiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ========== Static holder — tránh TransactionTooLarge với file lớn ==========
    private static List<String> pendingMessages = null;
    private static int pendingDelayMs = 1000;
    private static int pendingCharDelayMs = 50;
    private static boolean pendingPasteMode = false;

    /** Gọi từ MainActivity trước khi broadcast ACTION_START */
    public static void setPendingMessages(List<String> messages, int delayMs, int charDelayMs, boolean pasteMode) {
        pendingMessages = messages;
        pendingDelayMs = delayMs;
        pendingCharDelayMs = charDelayMs;
        pendingPasteMode = pasteMode;
    }

    // Trạng thái
    private boolean sessionActive = false;
    private boolean running = false;

    private List<String> messages = null;
    private int currentIndex = 0;
    private int delayMs = 0;
    private int charDelayMs = 50;
    private boolean pasteMode = false;

    // Dùng khi gõ từng chữ
    private String currentMessage = "";
    private int charIndex = 0;
    private final StringBuilder typedSoFar = new StringBuilder();

    // Lưu config để loop lại
    private List<String> lastMessages = null;
    private int lastDelayMs = 0;
    private int lastCharDelayMs = 50;
    private boolean lastPasteMode = false;

    // Đánh dấu đã hiện toast chờ bàn phím (tránh spam)
    private boolean waitingKeyboardToastShown = false;

    // Nhấn đúp Volume Down để ẩn/hiện Floating Widget.
    private boolean volumeDownPending = false;
    private static final long DOUBLE_PRESS_TIMEOUT_MS = 280L;
    private final Runnable singleVolumeDownAction = () -> {
        volumeDownPending = false;
        if (running) {
            pauseTyping();
            showToast("⏸ Tạm dừng (Volume ▼)");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.ryan.autotyper.ACTION_START");
        filter.addAction("com.ryan.autotyper.ACTION_PAUSE");
        filter.addAction("com.ryan.autotyper.ACTION_RESUME");
        filter.addAction("com.ryan.autotyper.ACTION_STOP");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.ryan.autotyper.ACTION_START".equals(action)) {
                    // Ưu tiên data từ static holder (file lớn)
                    if (pendingMessages != null && !pendingMessages.isEmpty()) {
                        List<String> msgs = pendingMessages;
                        int delay = pendingDelayMs;
                        int charDelay = pendingCharDelayMs;
                        boolean paste = pendingPasteMode;
                        // Clear pending ngay để tránh giữ reference thừa
                        pendingMessages = null;

                        lastMessages = msgs;
                        lastDelayMs = delay;
                        lastCharDelayMs = charDelay;
                        lastPasteMode = paste;

                        sessionActive = true;
                        startTyping(msgs, delay, charDelay, paste);
                    }
                } else if ("com.ryan.autotyper.ACTION_PAUSE".equals(action)) {
                    if (running) pauseTyping();
                } else if ("com.ryan.autotyper.ACTION_RESUME".equals(action)) {
                    if (!running && messages != null && currentIndex < messages.size()) {
                        running = true;
                        handler.post(typingRunnable);
                    }
                } else if ("com.ryan.autotyper.ACTION_STOP".equals(action)) {
                    fullStop();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        Log.d(TAG, "Service created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(singleVolumeDownAction);
        volumeDownPending = false;
        fullStop();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        // Cho phép nhấn đúp Volume Down để điều khiển widget ngay cả khi
        // hiện chưa có session AutoTyper đang chạy.
        if (!sessionActive && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event);
        }

        // Volume UP = tiếp tục / resume / loop
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!running) {
                if (messages != null && currentIndex < messages.size()) {
                    running = true;
                    handler.post(typingRunnable);
                    showToast("▶ Tiếp tục (Volume ▲)");
                } else if (lastMessages != null && !lastMessages.isEmpty()) {
                    startTyping(lastMessages, lastDelayMs, lastCharDelayMs, lastPasteMode);
                    showToast("🔄 Chạy lại từ đầu (Volume ▲)");
                }
            }
            return true;
        }

        // Volume DOWN một lần = tạm dừng.
        // Volume DOWN hai lần nhanh = ẩn/hiện menu Floating Widget.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeDownPending) {
                handler.removeCallbacks(singleVolumeDownAction);
                volumeDownPending = false;

                Intent toggleIntent = new Intent(FloatingWidgetService.ACTION_TOGGLE_MENU)
                        .setPackage(getPackageName());
                sendBroadcast(toggleIntent);
                showToast("☰ Đã chuyển trạng thái menu nổi");
            } else {
                volumeDownPending = true;
                handler.postDelayed(singleVolumeDownAction, DOUBLE_PRESS_TIMEOUT_MS);
            }
            return true;
        }

        return super.onKeyEvent(event);
    }

    private void showToast(final String msg) {
        handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {}
        }
        fullStop();
        Log.d(TAG, "Service destroyed");
    }

    private void startTyping(List<String> msgs, int delay, int charDelay, boolean paste) {
        if (msgs == null || msgs.isEmpty()) {
            Log.e(TAG, "Empty messages");
            return;
        }

        handler.removeCallbacksAndMessages(null);

        messages = msgs;
        currentIndex = 0;
        delayMs = delay;
        charDelayMs = Math.max(0, charDelay);
        pasteMode = paste;
        currentMessage = "";
        charIndex = 0;
        typedSoFar.setLength(0);
        running = true;
        sessionActive = true;
        waitingKeyboardToastShown = false;

        Log.d(TAG, "Start typing " + messages.size() + " messages, msgDelay=" + delayMs
                + ", charDelay=" + charDelayMs + ", pasteMode=" + pasteMode);

        if (!isKeyboardOpen()) {
            showToast("⏳ Đã Start — mở bàn phím để bắt đầu gõ");
            waitingKeyboardToastShown = true;
        }

        handler.post(typingRunnable);
    }

    private void pauseTyping() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Paused");
    }

    private void fullStop() {
        handler.removeCallbacks(singleVolumeDownAction);
        volumeDownPending = false;
        running = false;
        sessionActive = false;
        handler.removeCallbacksAndMessages(null);
        messages = null;
        currentIndex = 0;
        currentMessage = "";
        charIndex = 0;
        typedSoFar.setLength(0);
        pasteMode = false;
        waitingKeyboardToastShown = false;
        // Không clear lastMessages — vẫn cho phép loop sau STOP nếu user muốn start lại
        Log.d(TAG, "Full stopped");
    }

    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || !sessionActive || messages == null) {
                return;
            }

            if (!isKeyboardOpen()) {
                if (!waitingKeyboardToastShown) {
                    showToast("⏳ Đang chờ mở bàn phím...");
                    waitingKeyboardToastShown = true;
                }
                Log.d(TAG, "Keyboard closed, waiting...");
                handler.postDelayed(this, 600);
                return;
            }
            waitingKeyboardToastShown = false;

            final int total = messages.size();

            // ========== CHẾ ĐỘ DÁN TỪNG DÒNG ==========
            if (pasteMode) {
                if (currentIndex >= total) {
                    running = false;
                    Log.d(TAG, "Finished all messages (paste)");
                    showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    return;
                }

                String msg = messages.get(currentIndex);
                currentIndex++;
                boolean success = setTextToFocused(msg);

                if (!success) {
                    currentIndex--;
                    handler.postDelayed(this, 800);
                    return;
                }

                handler.postDelayed(() -> {
                    if (!running || !sessionActive) return;
                    performSendAction();
                    if (running && sessionActive && currentIndex < total) {
                        handler.postDelayed(typingRunnable, delayMs);
                    } else if (currentIndex >= total) {
                        running = false;
                        showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    }
                }, 200);
                return;
            }

            // ========== CHẾ ĐỘ GÕ TỪNG CHỮ ==========
            if (currentMessage.isEmpty() || charIndex >= currentMessage.length()) {
                if (currentIndex >= total) {
                    running = false;
                    Log.d(TAG, "Finished all messages");
                    showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    return;
                }
                currentMessage = messages.get(currentIndex);
                currentIndex++;
                charIndex = 0;
                typedSoFar.setLength(0);
            }

            if (charIndex < currentMessage.length()) {
                char c = currentMessage.charAt(charIndex);
                typedSoFar.append(c);
                charIndex++;
                boolean success = setTextToFocused(typedSoFar.toString());

                if (!success) {
                    charIndex--;
                    if (typedSoFar.length() > 0) {
                        typedSoFar.setLength(typedSoFar.length() - 1);
                    }
                    handler.postDelayed(this, 800);
                    return;
                }

                if (charIndex < currentMessage.length()) {
                    handler.postDelayed(this, charDelayMs);
                } else {
                    handler.postDelayed(() -> {
                        if (!running || !sessionActive) return;
                        performSendAction();
                        if (running && sessionActive && currentIndex < total) {
                            currentMessage = "";
                            handler.postDelayed(typingRunnable, delayMs);
                        } else if (currentIndex >= total) {
                            running = false;
                            showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                        }
                    }, 200);
                }
            }
        }
    };

    private boolean isKeyboardOpen() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo focused = findFocusedEditText(root);
            boolean open = focused != null;
            if (focused != null) focused.recycle();
            root.recycle();
            return open;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean setTextToFocused(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "No root window");
                return false;
            }

            AccessibilityNodeInfo focused = findFocusedEditText(root);
            if (focused != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                focused.recycle();
                root.recycle();
                return ok;
            } else {
                Log.w(TAG, "No focused EditText found");
                root.recycle();
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting text", e);
            return false;
        }
    }

    private static final String OWN_PACKAGE = "com.ryan.autotyper";

    private AccessibilityNodeInfo findFocusedEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence pkg = node.getPackageName();
        if (pkg != null && OWN_PACKAGE.equals(pkg.toString())) {
            return null;
        }

        if (node.isFocused() && node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }

        CharSequence className = node.getClassName();
        if (className != null && className.toString().contains("EditText") && node.isFocused()) {
            return AccessibilityNodeInfo.obtain(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findFocusedEditText(child);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private void performSendAction() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            String[] sendTexts = {"Send", "send", "Gửi", "gửi", "➤", "→"};
            for (String text : sendTexts) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                if (nodes != null) {
                    for (AccessibilityNodeInfo btn : nodes) {
                        if (btn.isClickable() || (btn.getParent() != null && btn.getParent().isClickable())) {
                            if (btn.isClickable()) {
                                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            } else {
                                btn.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                            Log.d(TAG, "Clicked send: " + text);
                            btn.recycle();
                            root.recycle();
                            return;
                        }
                        btn.recycle();
                    }
                }
            }

            findAndClickSendByDesc(root);
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error sending", e);
        }
    }

    private boolean findAndClickSendByDesc(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString().toLowerCase();
            if ((d.contains("send") || d.contains("gửi")) && node.isClickable()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndClickSendByDesc(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }
}
