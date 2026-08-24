# Tải Video – Backend yt-dlp

FastAPI + yt-dlp phục vụ app Android `com.ryan.videodownload`.

## Chạy local

```bash
cd backend
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# Cần ffmpeg nếu muốn merge video+audio
# Ubuntu: sudo apt install ffmpeg
# macOS:  brew install ffmpeg

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

API docs: http://127.0.0.1:8000/docs

## Docker

```bash
docker build -t tai-video-backend .
docker run -p 8000:8000 tai-video-backend
```

## Endpoints

| Method | Path | Mô tả |
|--------|------|--------|
| GET | `/api/health` | Health + version yt-dlp |
| POST | `/api/extract` | Body `{"url":"..."}` → metadata + formats |
| GET | `/api/download?url=...&format_id=...` | Server tải file rồi stream về |

## App Android

Trong `VideoExtractor` / `BuildConfig` đặt:

```
BASE_URL = "http://10.0.2.2:8000"   // emulator → host machine
BASE_URL = "http://192.168.x.x:8000" // máy thật cùng Wi‑Fi
```

Production: deploy VPS (Railway, Fly.io, VPS) + HTTPS.
