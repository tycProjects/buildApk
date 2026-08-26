# Vietsub AI Android

Ứng dụng Android native dùng **Gradle Groovy** để chọn video, nhận dạng phụ đề theo hai chế độ, dịch và xuất kết quả. Chế độ STT tách audio bằng FFmpeg rồi gọi Groq Whisper; chế độ OCR lấy các frame định kỳ và gửi ảnh đến Gemini để đọc chữ. Gemini tiếp tục dịch các đoạn phụ đề, sau đó FFmpeg burn-in phụ đề vào video MP4.

## Cấu trúc

| Thành phần | Vai trò |
|---|---|
| `app/` | Android native Java, Gradle Groovy, chọn video, gọi backend, xuất SRT/MP4 |
| `backend/src/server.js` | Upload video, FFmpeg, Groq STT, Gemini OCR/dịch, render phụ đề |
| `backend/.env.example` | Mẫu cấu hình khóa API và model |

## Chạy backend

Cài FFmpeg trên máy chạy backend, sau đó thực hiện:

```bash
cd backend
cp .env.example .env
# điền GEMINI_API_KEY và GROQ_API_KEY
npm install
npm start
```

Backend mặc định chạy cổng `3000`. Khóa Gemini và Groq chỉ nằm ở backend, không đưa vào APK.

## Build Android

Mở thư mục dự án bằng Android Studio và đồng bộ Gradle. Nếu chạy trên Android Emulator, `10.0.2.2` trong `MainActivity.java` trỏ về máy host. Với điện thoại thật, thay `API_URL` bằng địa chỉ IP LAN của máy backend và bật HTTPS khi triển khai thật.

Luồng sử dụng là chọn video, chọn `STT` hoặc `OCR`, chọn ngôn ngữ nguồn và đích, bấm **Nhận dạng và dịch**, chỉnh sửa nội dung xem trước ở phiên bản tiếp theo, rồi bấm **Xuất SRT** hoặc **Xuất video MP4 đã dịch**. Video MP4 được render với phụ đề cứng bằng FFmpeg.

## Lưu ý kỹ thuật

Bản MVP hiện dịch toàn bộ kết quả nhận dạng trong một yêu cầu Gemini. Với video dài, nên bổ sung cơ chế chia batch, hàng đợi job, tiến trình theo từng đoạn, giới hạn dung lượng upload và dọn dẹp file tạm. Endpoint tải video hiện dùng bộ nhớ tiến trình; bản production nên lưu object storage với URL có thời hạn.

Gemini được dùng cho hiểu ảnh/OCR và dịch; Groq Whisper được dùng cho STT đa ngôn ngữ. Groq translation endpoint chỉ dịch audio sang tiếng Anh, vì vậy pipeline này dùng transcription rồi dịch văn bản qua Gemini để hỗ trợ nhiều ngôn ngữ đích.

## Tài liệu tham khảo

[1]: https://ai.google.dev/gemini-api/docs/image-understanding "Gemini Image Understanding"
[2]: https://ai.google.dev/gemini-api/docs/text-generation "Gemini Text Generation"
[3]: https://console.groq.com/docs/speech-to-text "Groq Speech to Text"
