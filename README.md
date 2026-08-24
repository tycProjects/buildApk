# Tải Video (com.ryan.videodownload)

Ứng dụng tải video đa nền tảng – Kotlin + Jetpack Compose + Gradle (Groovy).

## Thông tin

| | |
|--|--|
| **Tên app** | Tải Video |
| **Package** | `com.ryan.videodownload` |
| **Min SDK** | 26 |
| **UI** | Compose Material 3 + gradient dark |
| **Download** | Multi-connection OkHttp |
| **Player** | Media3 ExoPlayer |

## Mở project

1. Android Studio → Open folder này
2. Sync Gradle
3. Run

## Quyền

- INTERNET / NETWORK
- POST_NOTIFICATIONS (Android 13+, runtime)
- WRITE_EXTERNAL_STORAGE chỉ API ≤ 28
- Android 10+: lưu app-specific dir (không cần xin storage), sau đó copy vào MediaStore (album máy)

## Tính năng chính

1. **Tải video** từ URL (multi-connection OkHttp)
2. **Tự động lưu vào album máy** sau khi tải xong  
   → thư mục `Movies/VideoDownloader` (hiển thị trong Gallery / Photos)
3. **Xem video trong app** – chạm vào item đã tải trong Lịch sử → mở trình phát ExoPlayer

## Lưu ý Extractor

`VideoExtractor` hỗ trợ thật:
- YouTube (Piped API)
- TikTok (tikwm)
- **Bilibili** (Cobalt + detect b23.tv / bilibili.com)
- **CapCut** (template / share link – preview video)
- Instagram / Facebook / X / Vimeo… (Cobalt)

Production có thể bổ sung backend + yt-dlp nếu cần.
