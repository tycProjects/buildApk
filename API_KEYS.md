# Cấu hình Gemini và Groq API key

Màn hình chính có nút **Cài đặt Gemini & Groq API key** mở `SettingsActivity`. Người dùng nhập hai key, nhấn **Lưu API key**, và ứng dụng xóa nội dung ô nhập sau khi lưu. `ApiKeyStore` lưu dữ liệu bằng `EncryptedSharedPreferences` với `MasterKey` AES-256-GCM của Android Keystore.

`MainActivity` tạo `ApiKeyStore` khi bắt đầu pipeline. Nếu thiếu một trong hai key, app không gọi AI và yêu cầu người dùng mở SettingsActivity. Khi đủ key, `AiSubtitleApi` nhận key từ store thay vì `BuildConfig` hoặc hằng số trong source.

| Thành phần | Vai trò |
|---|---|
| `SettingsActivity.kt` | Nhập, lưu và xóa key |
| `activity_settings.xml` | Giao diện cấu hình |
| `ApiKeyStore.kt` | Mã hóa và truy xuất key |
| `MainActivity.kt` | Kiểm tra key trước khi chạy STT/OCR/dịch |
| `androidx.security:security-crypto` | Tạo encrypted preferences và MasterKey |

## Cảnh báo bảo mật

Mã hóa local giúp bảo vệ key khi lưu trữ thông thường, nhưng không biến APK thành client tin cậy. API key vẫn được dùng ở phía thiết bị và có thể bị quan sát bằng instrumentation, log, memory dump hoặc reverse engineering. Không dùng mô hình này cho ứng dụng phát hành rộng nếu key có quota hoặc quyền thanh toán lớn. Production nên dùng backend: app gửi request có token người dùng, backend giữ Gemini/Groq key, áp quota/rate limit và ghi audit log.

## Build

Không cần `GEMINI_API_KEY` hoặc `GROQ_API_KEY` trong `gradle.properties` nữa. Hai `buildConfigField` cũ đã được xóa khỏi `app/build.gradle`. Chỉ người dùng cuối nhập key từ SettingsActivity.
