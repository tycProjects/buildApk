# Chọn ngôn ngữ gốc video

Màn hình chính và QueueActivity hiện dùng `LanguageCatalog`. Người dùng chọn tên ngôn ngữ thân thiện, còn pipeline truyền mã ISO-639-1 tương ứng.

| Hiển thị | Mã gửi API |
|---|---|
| Tự động phát hiện | `auto` |
| Tiếng Việt | `vi` |
| English | `en` |
| 日本語 | `ja` |
| 한국어 | `ko` |
| 中文 | `zh` |
| ภาษาไทย | `th` |
| Français | `fr` |
| Deutsch | `de` |
| Español | `es` |

Với STT, `vi`, `en`, `ja`... được gửi vào trường `language` của Groq Whisper; lựa chọn `auto` bỏ qua trường này để mô hình tự nhận dạng. Việc chỉ định ngôn ngữ thường giúp tăng độ chính xác và giảm độ trễ. Với OCR, mã ngôn ngữ được truyền tiếp vào bước Gemini dịch, vì nội dung chữ trên frame không cần tham số `language` của Groq.

Mã nguồn chính là `LanguageCatalog.kt`. `MainActivity` và `QueueActivity` dùng `selectedItemPosition` để lấy code thay vì truyền nhãn hiển thị vào API.
