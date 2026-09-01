# Sticky Foreground Service

## Cơ chế

`FloatingWidgetService.onStartCommand()` trả về `START_STICKY` cho các lần khởi động bình thường. Nếu process bị hệ thống thu hồi, Android có thể tạo lại service với `intent == null`; `onCreate()` sẽ dựng lại notification channel, notification và Floating Widget.

Lệnh dừng chủ động `ACTION_HIDE` trả về `START_NOT_STICKY`, vì người dùng đã yêu cầu tắt widget.

## Tự chạy sau reboot

`FloatingWidgetBootReceiver` lắng nghe `BOOT_COMPLETED`. Receiver chỉ khởi động service nếu:

```java
FloatingWidgetService.isAutoStartAfterBoot(context) == true
Settings.canDrawOverlays(context) == true
```

Bật/tắt từ một Switch trong Activity:

```java
switchAutoStart.setChecked(
        FloatingWidgetService.isAutoStartAfterBoot(this));

switchAutoStart.setOnCheckedChangeListener((button, enabled) ->
        FloatingWidgetService.setAutoStartAfterBoot(this, enabled));
```

Tùy chọn mặc định là `false`, tránh tự bật overlay sau khi người dùng vừa cài app.

## Giới hạn

`START_STICKY` không thể khởi động lại service sau khi người dùng bấm Force Stop, tắt quyền overlay, tắt app bằng công cụ quản lý pin, hoặc khi nhà sản xuất áp dụng chính sách chặn app nền. Notification vẫn phải được người dùng cho phép trên Android 13+.
