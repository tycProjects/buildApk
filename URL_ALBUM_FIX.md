# Bản sửa URL và chọn video từ album

## Đã sửa

| Hạng mục | Kết quả |
|---|---|
| Package | `com.app.vietsubai` |
| App label | Vietsub AI |
| Nút Từ máy | Dùng Android Photo Picker, chỉ chọn video từ album |
| Nút Dán URL | Mở màn hình downloader native |
| Nút Clipboard | Đọc URL hợp lệ từ clipboard và mở downloader |
| Downloader | OkHttp follow redirect, kiểm tra HTTP/content-type, streaming và progress |
| URL HTML/JSON | Không lưu nhầm HTML thành MP4; báo lỗi rõ ràng |
| Gradle build | `assembleDebug` thành công |

## Giới hạn URL nền tảng

Downloader native hiện nhận URL media trực tiếp. URL `b23.tv` là URL rút gọn/trang nền tảng, không phải URL MP4; nếu link hết hạn hoặc máy chủ trả HTML/JSON, app sẽ dừng nhanh và hiển thị lý do thay vì đứng ở 0%. Việc phân giải đầy đủ mọi trang Douyin/Bilibili cần extractor native riêng hoặc backend resolver; phiên bản này không tự giả mạo thành file video khi chưa có media URL.

## Build

Lệnh: `./gradlew assembleDebug --no-daemon --max-workers=1`

Kết quả: `BUILD SUCCESSFUL`
