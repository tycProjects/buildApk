# Báo cáo kiểm tra project

## Kết quả

Kiểm tra archive ZIP: đạt, không có lỗi dữ liệu nén.

Kiểm tra XML: đạt, các tệp XML trong `res` và `AndroidManifest.xml` đều well-formed.

Kiểm tra Java tĩnh: đạt cân bằng ngoặc và không phát hiện resource ID nội bộ bị thiếu.

Kiểm tra broadcast: đã bổ sung đăng ký và xử lý `ACTION_PAUSE`/`ACTION_RESUME`, vốn trước đó có thể khiến nút Pause/Resume của Floating Widget không hoạt động.

Kiểm tra bảo mật broadcast: các receiver động dùng `RECEIVER_NOT_EXPORTED` trên Android mới; các lệnh Start/Stop từ Activity dùng `setPackage(getPackageName())`.

## Lưu ý về Volume Down

`AccessibilityService.onKeyEvent()` đang trả về `true` cho Volume Down để chiếm quyền điều khiển. Vì vậy âm lượng hệ thống có thể không thay đổi khi app đang bắt phím. Đây là hành vi có chủ đích để dùng Volume Down làm phím tắt, không phải lỗi biên dịch. Nếu muốn tránh ảnh hưởng âm lượng hệ thống, cần thêm tùy chọn bật/tắt phím tắt hoặc chỉ bắt phím khi Floating Widget đang chạy.

Nhấn đơn Volume Down được trì hoãn 280 ms để chờ nhấn đúp. Nhấn đúp sẽ ẩn/hiện menu và hủy thao tác pause đơn. Đây là giới hạn thiết kế cần giữ ổn định.

## Giới hạn build

Sandbox không có `gradlew`, Gradle hoặc `sdkmanager`, nên chưa thể chạy `assembleDebug` trong môi trường này. Project đã qua kiểm tra tĩnh nhưng vẫn cần Android Studio Sync và Build trên máy có Android SDK để xác nhận biên dịch cuối cùng.
