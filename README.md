# Treasure Hunter Auto Dig — prototype

Mục tiêu mặc định: (-20, 20).

Quy luật tọa độ được suy ra từ ảnh người dùng:
- Sang phải: X + 1
- Sang trái: X - 1
- Đi lên: Y + 1
- Đi xuống: Y - 1

Quan trọng:
- Đây là mã nguồn prototype, chưa phải APK cài đặt hoàn chỉnh.
- Dịch vụ Accessibility của Android có thể phát gesture trên màn hình khi người dùng bật quyền tương ứng.
- Phần OCR tọa độ và hiệu chỉnh vị trí joystick/nút đào cần được hoàn thiện theo đúng thiết bị và giao diện game.
- Tọa độ pixel trong mã đang dựa trên ảnh tham chiếu 691x1536 và có thể phải hiệu chỉnh.

Cách build:
1. Mở thư mục này bằng Android Studio.
2. Build APK.
3. Cài APK lên điện thoại.
4. Bật dịch vụ Auto Dig trong Trợ năng.
5. Đặt mục tiêu trong app.

Tài liệu Android về AccessibilityService:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
