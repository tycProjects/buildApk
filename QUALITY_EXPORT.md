# Cấu hình chất lượng video export

Màn hình `EditorActivity` cung cấp ba lựa chọn trước khi enqueue `VideoBurnInWorker`.

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| Bitrate | `1500k`, `2500k`, `4000k`, `6000k` | Bitrate H.264 mục tiêu; cao hơn thường cho hình ảnh tốt hơn và file lớn hơn |
| Độ phân giải | `original`, `640x360`, `1280x720`, `1920x1080` | `original` giữ kích thước; tùy chọn khác scale và pad đúng khung hình |
| Preset | `ultrafast`, `veryfast`, `faster`, `fast`, `medium` | Nhanh hơn sẽ tốn bitrate/file lớn hơn; `veryfast` là mặc định cân bằng |

Các tham số được đưa vào `Data` bằng `VideoBurnInWorker.KEY_BITRATE`, `KEY_RESOLUTION` và `KEY_PRESET`. Worker đọc lại, whitelist bitrate/preset và tạo filter scale an toàn trước khi gọi `FfmpegRenderer`.

Ví dụ lệnh tạo ra tương đương:

```bash
ffmpeg -y -i input.mp4 \
  -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,subtitles=translated.srt" \
  -c:v libx264 -preset veryfast -b:v 2500k -maxrate 2500k -bufsize 5000k \
  -pix_fmt yuv420p -c:a aac -b:a 128k -movflags +faststart output.mp4
```

`-maxrate` giới hạn bitrate đỉnh và `-bufsize` được đặt bằng hai lần bitrate mục tiêu để giảm dao động kích thước file. Với video dọc hoặc tỉ lệ khác 16:9, filter `force_original_aspect_ratio=decrease` và `pad` tránh kéo méo hình.

Tác vụ export vẫn chạy trong Foreground Worker; thay đổi chất lượng chỉ ảnh hưởng đến job mới. Nếu muốn hủy job đang chạy, dùng nút Hủy trong notification.
