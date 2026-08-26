# Subtitle Styling

`EditorActivity` cho phép chọn font, cỡ chữ, màu chữ, màu viền và vị trí phụ đề trước khi enqueue `VideoBurnInWorker`.

| Tùy chọn | Ví dụ | Mapping FFmpeg |
|---|---|---|
| Font | Arial, Roboto, DejaVu Sans | `FontName` |
| Cỡ chữ | 8–96, mặc định 22 | `FontSize` |
| Màu chữ | `#FFFFFF` | `PrimaryColour` |
| Màu viền | `#000000` | `OutlineColour` |
| Vị trí | bottom center, bottom left, bottom right, top center | ASS `Alignment` 2, 1, 3, 8 |

Màu RGB được đổi sang định dạng ASS BGR alpha-zero. Ví dụ `#FFCC00` trở thành `&H0000CCFF`. Renderer tạo filter:

```text
subtitles=translated.srt:force_style='FontName=Roboto,FontSize=24,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BorderStyle=1,Outline=2,Alignment=2'
```

Cỡ chữ được giới hạn 8–96; màu được kiểm tra bằng regex hex 6 ký tự; font và alignment lấy từ danh sách được whitelist. Queue nhiều video hiện dùng style mặc định `Arial`, 22px, chữ trắng, viền đen, bottom center. Có thể mở rộng `QueueActivity` bằng cách truyền thêm các key `KEY_FONT`, `KEY_FONT_SIZE`, `KEY_PRIMARY_COLOR`, `KEY_OUTLINE_COLOR`, `KEY_OUTLINE` và `KEY_ALIGNMENT` vào `Data` giống `EditorActivity`.
