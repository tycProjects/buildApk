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

import java.util.List;

public class AutoTyperService extends AccessibilityService {

    private static final String TAG = "YeuEmMoiVuTru";
    private BroadcastReceiver receiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Trạng thái
    private boolean sessionActive = false;
    private boolean running = false;

    private String[] messages;
    private int currentIndex = 0;
    private int delayMs = 0;
    private int charDelayMs = 50;
    private boolean pasteMode = false;

    // Dùng khi gõ từng chữ
    private String currentMessage = "";
    private int charIndex = 0;
    private StringBuilder typedSoFar = new StringBuilder();

    // Lưu config để resume
    private String lastPayload = null;
    private int lastDelayMs = 0;
    private int lastCharDelayMs = 50;
    private boolean lastPasteMode = false;

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.ryan.autotyper.ACTION_START");
        filter.addAction("com.ryan.autotyper.ACTION_STOP");

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.ryan.autotyper.ACTION_START".equals(action)) {
                    String payload = intent.getStringExtra("payload");
                    int delay = intent.getIntExtra("delay_ms", 0);
                    int charDelay = intent.getIntExtra("char_delay_ms", 50);
                    boolean paste = intent.getBooleanExtra("paste_mode", false);

                    lastPayload = payload;
                    lastDelayMs = delay;
                    lastCharDelayMs = charDelay;
                    lastPasteMode = paste;

                    sessionActive = true;
                    startTyping(payload, delay, charDelay, paste);
                } else if ("com.ryan.autotyper.ACTION_STOP".equals(action)) {
                    fullStop();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
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
        fullStop();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Chỉ xử lý volume khi đã START
        if (!sessionActive) {
            return super.onKeyEvent(event);
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event);
        }

        int keyCode = event.getKeyCode();

        // Volume UP = tiếp tục / resume
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!running) {
                if (messages != null && currentIndex < messages.length) {
                    // Resume từ chỗ đang dừng
                    running = true;
                    handler.post(typingRunnable);
                    showToast("▶ Tiếp tục (Volume ▲)");
                } else if (lastPayload != null && !lastPayload.isEmpty()) {
                    // Hết rồi thì start lại từ đầu (LOOP)
                    startTyping(lastPayload, lastDelayMs, lastCharDelayMs, lastPasteMode);
                    showToast("🔄 Chạy lại từ đầu (Volume ▲)");
                }
            }
            return true;
        }

        // Volume DOWN = tạm dừng
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (running) {
                pauseTyping();
                showToast("⏸ Tạm dừng (Volume ▼)");
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

    // Đánh dấu đã hiện toast chờ bàn phím (tránh spam)
    private boolean waitingKeyboardToastShown = false;

    private void startTyping(String payload, int delay, int charDelay, boolean paste) {
        if (payload == null || payload.isEmpty()) {
            Log.e(TAG, "Empty payload");
            return;
        }

        // Reset state — luôn cho Start, chỉ gõ khi bàn phím mở
        handler.removeCallbacksAndMessages(null);

        messages = payload.split("\\|\\|\\|");
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

        Log.d(TAG, "Start typing " + messages.length + " messages, msgDelay=" + delayMs
                + ", charDelay=" + charDelayMs + ", pasteMode=" + pasteMode);

        if (!isKeyboardOpen()) {
            showToast("⏳ Đã Start — mở bàn phím để bắt đầu gõ");
            waitingKeyboardToastShown = true;
        }

        handler.post(typingRunnable);
    }

    /** Tạm dừng — giữ sessionActive để volume vẫn hoạt động */
    private void pauseTyping() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Paused");
    }

    /** STOP hoàn toàn — tắt session, volume không còn tác dụng */
    private void fullStop() {
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
        Log.d(TAG, "Full stopped");
    }

    private final Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || !sessionActive || messages == null) {
                return;
            }

            // Chỉ gõ khi bàn phím đang mở. Nếu đóng thì chờ (không tăng index).
            if (!isKeyboardOpen()) {
                if (!waitingKeyboardToastShown) {
                    showToast("⏳ Đang chờ mở bàn phím...");
                    waitingKeyboardToastShown = true;
                }
                Log.d(TAG, "Keyboard closed, waiting...");
                handler.postDelayed(this, 600);
                return;
            }
            // Bàn phím đã mở lại → reset cờ toast
            waitingKeyboardToastShown = false;

            // ========== CHẾ ĐỘ DÁN TOÀN BỘ ==========
            if (pasteMode) {
                if (currentIndex >= messages.length) {
                    running = false;
                    Log.d(TAG, "Finished all messages (paste)");
                    showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    return;
                }

                String msg = messages[currentIndex];
                currentIndex++;
                boolean success = setTextToFocused(msg);

                if (!success) {
                    // Không ghi được → lùi lại index và chờ
                    currentIndex--;
                    handler.postDelayed(this, 800);
                    return;
                }

                // Dán xong → gửi, rồi delay tin rồi message tiếp
                handler.postDelayed(() -> {
                    if (!running || !sessionActive) return;
                    performSendAction();
                    if (running && sessionActive && currentIndex < messages.length) {
                        handler.postDelayed(typingRunnable, delayMs);
                    } else if (currentIndex >= messages.length) {
                        running = false;
                        showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    }
                }, 200);
                return;
            }

            // ========== CHẾ ĐỘ GÕ TỪNG CHỮ ==========
            // Bắt đầu message mới
            if (currentMessage.isEmpty() || charIndex >= currentMessage.length()) {
                if (currentIndex >= messages.length) {
                    running = false;
                    Log.d(TAG, "Finished all messages");
                    showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                    return;
                }
                currentMessage = messages[currentIndex];
                currentIndex++;
                charIndex = 0;
                typedSoFar.setLength(0);
            }

            // Gõ 1 ký tự
            if (charIndex < currentMessage.length()) {
                char c = currentMessage.charAt(charIndex);
                typedSoFar.append(c);
                charIndex++;
                boolean success = setTextToFocused(typedSoFar.toString());

                if (!success) {
                    // Không ghi được (mất focus) → lùi lại 1 ký tự và chờ
                    charIndex--;
                    if (typedSoFar.length() > 0) {
                        typedSoFar.setLength(typedSoFar.length() - 1);
                    }
                    handler.postDelayed(this, 800);
                    return;
                }

                if (charIndex < currentMessage.length()) {
                    // Còn chữ → delay chữ rồi gõ tiếp
                    handler.postDelayed(this, charDelayMs);
                } else {
                    // Hết message → gửi, rồi delay tin rồi message tiếp
                    handler.postDelayed(() -> {
                        if (!running || !sessionActive) return;
                        performSendAction();
                        if (running && sessionActive && currentIndex < messages.length) {
                            currentMessage = "";
                            handler.postDelayed(typingRunnable, delayMs);
                        } else if (currentIndex >= messages.length) {
                            running = false;
                            showToast("✅ Đã gõ xong tất cả — Ấn Volume ▲ để loop lại");
                        }
                    }, 200);
                }
            }
        }
    };

    /** Kiểm tra bàn phím có đang mở không (có ô EditText đang focus) */
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

    /** Gõ text vào ô đang focus bằng ACTION_SET_TEXT (từng chữ). Trả về true nếu thành công. */
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

    /** Tìm EditText đang focus, BỎ QUA các ô nhập của chính app này */
    private AccessibilityNodeInfo findFocusedEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;

        // Bỏ qua toàn bộ cây thuộc package của app mình
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
