# Tải Video (`com.ryan.videodownload`)

App Android + **Backend yt-dlp** tải video đa nền tảng.

## Tính năng mới

- **Tự động lưu vào album máy**: Sau khi tải xong, video được copy vào `Movies/VideoDownloader` và hiện trong Gallery / Photos.
- **Xem video trong app**: Chạm vào item trong lịch sử tải (hoặc nút Play) để mở player ExoPlayer full-screen.

## Cấu trúc

```
VideoDownloaderApp/
├── app/                 # Android (Kotlin + Compose)
└── backend/             # FastAPI + yt-dlp
    ├── main.py
    ├── requirements.txt
    ├── Dockerfile
    └── README.md
```

## 1. Chạy backend (bắt buộc để ổn định)

```bash
cd backend
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
# Cài ffmpeg (khuyến nghị): apt install ffmpeg / brew install ffmpeg

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

- Docs: http://127.0.0.1:8000/docs  
- Health: http://127.0.0.1:8000/api/health  

### Docker

```bash
cd backend
docker build -t tai-video-backend .
docker run -p 8000:8000 tai-video-backend
```

## 2. Cấu hình app

Trong `app/build.gradle` → `BACKEND_URL`:

| Môi trường | Giá trị |
|------------|---------|
| Emulator Android | `http://10.0.2.2:8000` |
| Máy thật (cùng Wi‑Fi) | `http://IP_MÁY_TÍNH:8000` |
| Production | `https://your-domain.com` |

## 3. Chạy app

Android Studio → Open `VideoDownloaderApp` → Sync → Run.

## API backend

| Method | Path | Body / Query |
|--------|------|----------------|
| GET | `/api/health` | — |
| POST | `/api/extract` | `{"url":"https://..."}` |
| GET | `/api/download` | `?url=...&format_id=best` |

## Thứ tự extract trong app

1. **Backend yt-dlp** (chính)
2. Piped (YouTube)
3. tikwm (TikTok)
4. Cobalt
5. oEmbed (chỉ metadata)

## Quyền app

INTERNET, POST_NOTIFICATIONS (API 33+), storage legacy API ≤ 28.  
Lưu album dùng MediaStore (không cần WRITE_EXTERNAL_STORAGE trên Android 10+).

## Lưu ý album

- Video được lưu vào **Movies → VideoDownloader** trên máy.
- File gốc vẫn giữ trong thư mục app (`Android/data/.../files/Download/VideoDownloader`) để xem trong app.
