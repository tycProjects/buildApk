# Queue nhiều video và lịch sử export

`QueueActivity` dùng `OpenMultipleDocuments` để chọn nhiều video. Mỗi video được tạo một bản ghi `HistoryItem` với trạng thái `QUEUED`, sau đó tạo một `OneTimeWorkRequest<QueuedVideoWorker>`. Các request được nối bằng `WorkContinuation.then()`, nên WorkManager xử lý từng video theo thứ tự, không chạy đồng thời.

`QueuedVideoWorker` lấy API key từ `ApiKeyStore`, chạy pipeline STT/OCR → dịch → FFmpeg burn-in, rồi cập nhật `HistoryStore` thành `SUCCESS` cùng đường dẫn output. Nếu lỗi, trạng thái là `FAILED`; nếu người dùng nhấn Hủy trên notification, trạng thái là `CANCELLED`. Lỗi được retry tối đa hai lần.

`HistoryActivity` đọc danh sách local và hiển thị tên video, trạng thái, thông báo lỗi và nút mở MP4 output. Video được mở qua `FileProvider`, tránh `FileUriExposedException` trên Android 7+. Nút xóa lịch sử chỉ xóa metadata; bản production nên hỏi xác nhận và xóa cả output file không còn dùng.

## Các nút chính

| Nút | Kết quả |
|---|---|
| `Export nhiều video trong background` | Mở QueueActivity |
| `Đưa vào hàng đợi` | Tạo chuỗi WorkManager tuần tự |
| `Lịch sử video đã export` | Mở HistoryActivity |
| `Hủy video này` trong notification | Hủy worker đang chạy |

Queue cần API key đã được nhập trong SettingsActivity trước khi bắt đầu. WorkManager giữ tác vụ trong background và foreground notification giúp người dùng theo dõi tiến trình khi tắt màn hình. Output hiện được lưu trong cache; nên chuyển sang MediaStore hoặc app-specific external storage nếu cần giữ lâu dài.
