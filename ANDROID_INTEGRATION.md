# Tích hợp Android trực tiếp: Gemini, Groq, OCR, SRT Editor và FFmpeg

## Kiến trúc

Ứng dụng chạy pipeline ngay trên thiết bị:

```text
Video Uri
  ├─ STT: copy video → FFmpeg tách FLAC mono 16 kHz → Groq Whisper verbose_json
  └─ OCR: MediaMetadataRetriever lấy frame mỗi 4 giây → Gemini đọc subtitle
                         ↓
                  Gemini dịch JSON cue
                         ↓
                   SRT Editor + Media3 Preview
                         ↓
                   FFmpeg subtitles/libass
                         ↓
                         MP4
```

Groq cung cấp endpoint transcription OpenAI-compatible tại `/openai/v1/audio/transcriptions`; `whisper-large-v3-turbo` phù hợp tốc độ/chi phí, còn `whisper-large-v3` ưu tiên độ chính xác. Phản hồi `verbose_json` có segment timestamps để tạo cue SRT.[1]

Gemini nhận ảnh inline bằng base64 cho OCR và trả JSON bằng `responseMimeType=application/json`. Với ảnh inline nhỏ, request nên được giới hạn; video/frame lớn nên chia nhỏ hoặc dùng File API.[2] [3]

## Cấu hình API key

Không commit key vào Git. Thêm vào `gradle.properties` ở thư mục user hoặc file `local.properties` không commit:

```properties
GEMINI_API_KEY=AIza...
GROQ_API_KEY=gsk_...
```

`app/build.gradle` đưa hai giá trị vào `BuildConfig`. Đây chỉ là biện pháp đóng gói tiện dụng cho MVP; APK có thể bị reverse-engineer. Production nên chuyển request sang backend có rate limit, quota và key server-side.

## Groq STT

`AiSubtitleApi.transcribe()` gửi file FLAC dạng multipart, `response_format=verbose_json`, `timestamp_granularities[]=segment` và ngôn ngữ nếu người dùng không chọn `auto`. Groq hiện hỗ trợ upload trực tiếp các định dạng audio/video và giới hạn file tùy tier; video dài nên chunk audio hoặc dùng URL.[1]

## Gemini OCR và dịch

`ocrFrame()` nén frame thành JPEG quality 75, base64 hóa và gửi `inline_data`. Prompt yêu cầu JSON `{text, confidence}` và trả cue với timecode của frame. Pipeline loại bỏ kết quả trùng trong khoảng 2,5 giây. Khi cần OCR chính xác hơn, crop vùng 25–35% phía dưới video trước khi nén.

`translate()` gửi toàn bộ cue JSON, yêu cầu giữ nguyên `startMs/endMs` và chỉ trả mảng JSON. Video dài nên chia cue thành các batch 20–50 mục, retry khi JSON lỗi và dùng schema validation trước khi ghi SRT.

## Video preview và SRT editor

`EditorActivity` dùng Media3 ExoPlayer để phát video. Mỗi `SubtitleCue` được hiển thị thành một hàng gồm timecode và `EditText`. Chạm timecode sẽ seek video tới `startMs`; khi mất focus, nội dung được ghi ngược vào cue. Nút export đọc lại tất cả `EditText`, serialize SRT và gọi renderer.

Để hiển thị subtitle overlay đồng bộ trên player trong lúc xem preview, có thể thêm một `TextView` đặt trên `PlayerView`, rồi cập nhật bằng `player.addListener` hoặc coroutine polling `player.currentPosition`:

```kotlin
lifecycleScope.launch {
    while (isResumed) {
        val cue = cues.firstOrNull { player.currentPosition in it.startMs..it.endMs }
        subtitleOverlay.text = cue?.text.orEmpty()
        delay(80)
    }
}
```

## FFmpeg burn-in

`FfmpegRenderer` thực hiện bốn bước:

1. Copy `content://` Uri vào `cacheDir`, vì FFmpeg cần đường dẫn file đọc được.
2. Serialize cue đã chỉnh sửa thành UTF-8 SRT.
3. Dùng filter `subtitles` với `libass` để vẽ chữ lên từng frame.
4. Encode H.264/AAC và lưu MP4; `Statistics.time` được chia cho duration để cập nhật ProgressBar.

Lệnh tương đương:

```bash
ffmpeg -y -i input.mp4 \
  -vf "subtitles=translated.srt:force_style='FontName=Arial,FontSize=22,Outline=2,Alignment=2'" \
  -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p \
  -c:a aac -b:a 128k -movflags +faststart translated.mp4
```

`-crf 23` và `-preset veryfast` là cấu hình cân bằng. Tăng CRF lên 25–28 để giảm dung lượng; dùng `medium` để nén tốt hơn nhưng chậm hơn. Nếu không cần chữ dính vào hình, hãy dùng subtitle mềm với `-c:v copy -c:s mov_text`; cách đó nhanh hơn và không encode lại video.

### Escape đường dẫn subtitle

Đường dẫn tạo bởi Android có thể chứa dấu `:` hoặc khoảng trắng. Code đã đổi dấu slash và escape `:` trước khi đưa vào filter. Không ghép input từ người dùng trực tiếp vào shell command; nên dùng argument list hoặc validate path để tránh injection.

### Lựa chọn thư viện

FFmpegKit bản chính thức đã được retired; repository công bố FFmpegKitNext là hướng tiếp tục được duy trì nhưng phân phối dạng source-only. Dependency `com.arthenica:ffmpeg-kit-full:6.0-2` trong project chỉ phù hợp MVP/di sản và có thể không còn thích hợp cho release mới.[4] Với production, nên build FFmpegKitNext hoặc FFmpeg native JNI riêng, chỉ bật các codec/filter cần thiết, đồng thời rà soát LGPL/GPL và license của `libass`, x264.

## Các giới hạn cần xử lý trước production

| Rủi ro | Cách xử lý |
|---|---|
| API key nằm trong APK | Chuyển API calls sang backend; key trực tiếp chỉ dùng MVP nội bộ |
| Groq giới hạn dung lượng audio | FLAC mono 16 kHz, chunk 5–10 phút, nối timestamps |
| Gemini JSON lỗi hoặc hallucination | JSON schema, retry, confidence threshold và nút review |
| Video 4K gây nóng máy | scale tối đa 1080p, `veryfast`, hoặc hardware encoder |
| OCR frame trùng | perceptual hash hoặc so sánh text/time window |
| App bị kill khi render | Foreground service + WorkManager notification |
| File tạm chiếm bộ nhớ | xóa cache sau export và giới hạn số frame |

### Tài liệu tham khảo

[1]: https://console.groq.com/docs/speech-to-text "Groq Speech to Text"
[2]: https://ai.google.dev/gemini-api/docs/image-understanding "Gemini Image Understanding"
[3]: https://ai.google.dev/gemini-api/docs/file-input-methods "Gemini File Input Methods"
[4]: https://github.com/arthenica/ffmpeg-kit "FFmpegKit repository and retirement notice"
