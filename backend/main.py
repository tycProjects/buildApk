"""
Tải Video – Backend yt-dlp
API: POST /api/extract  { "url": "..." }
     GET  /api/health
     GET  /api/download?url=...&format_id=...  (stream file)
"""

from __future__ import annotations

import asyncio
import logging
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field, HttpUrl
import yt_dlp

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("tai-video-backend")

app = FastAPI(
    title="Tải Video API",
    description="Backend yt-dlp cho app Android Tải Video (com.ryan.videodownload)",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── Config ───────────────────────────────────────────────
DOWNLOAD_DIR = Path(os.getenv("DOWNLOAD_DIR", tempfile.gettempdir())) / "tai_video"
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

YDL_OPTS_BASE: dict[str, Any] = {
    "quiet": True,
    "no_warnings": True,
    "noplaylist": True,
    "skip_download": True,
    "socket_timeout": 30,
    "retries": 3,
    "extractor_args": {
        "youtube": {
            "player_client": ["android", "web"],
        }
    },
}


# ─── Models ───────────────────────────────────────────────
class ExtractRequest(BaseModel):
    url: str = Field(..., min_length=8, description="Video URL")


class FormatOut(BaseModel):
    format_id: str
    quality: str
    format: str
    url: Optional[str] = None
    size_bytes: Optional[int] = None
    has_audio: bool = True
    fps: Optional[int] = None
    height: Optional[int] = None
    vcodec: Optional[str] = None
    acodec: Optional[str] = None
    note: Optional[str] = None


class VideoOut(BaseModel):
    id: Optional[str] = None
    title: str
    thumbnail: Optional[str] = None
    duration: int = 0
    platform: str = "other"
    original_url: str
    author: Optional[str] = None
    description: Optional[str] = None
    formats: list[FormatOut]


# ─── Helpers ──────────────────────────────────────────────
def detect_platform(url: str) -> str:
    u = url.lower()
    if "youtube.com" in u or "youtu.be" in u:
        return "youtube"
    if "tiktok.com" in u or "vm.tiktok" in u:
        return "tiktok"
    if "instagram.com" in u:
        return "instagram"
    if "facebook.com" in u or "fb.watch" in u:
        return "facebook"
    if "twitter.com" in u or "x.com" in u:
        return "twitter"
    if "vimeo.com" in u:
        return "vimeo"
    return "other"


def quality_label(f: dict) -> str:
    height = f.get("height")
    if height:
        return f"{height}p"
    abr = f.get("abr")
    if abr and f.get("vcodec") in (None, "none"):
        return f"Audio {int(abr)}kbps"
    fmt_note = f.get("format_note") or f.get("format_id") or "unknown"
    return str(fmt_note)


def ext_of(f: dict) -> str:
    e = (f.get("ext") or "mp4").lower()
    if e in ("m4a", "mp3", "opus", "ogg", "webm", "mp4", "mkv"):
        return e
    return "mp4"


def has_audio(f: dict) -> bool:
    ac = f.get("acodec")
    return ac is not None and ac != "none"


def has_video(f: dict) -> bool:
    vc = f.get("vcodec")
    return vc is not None and vc != "none"


def sanitize_filename(name: str) -> str:
    name = re.sub(r'[\\/:*?"<>|]', "_", name)
    return name[:100].strip() or "video"


def run_extract(url: str) -> dict:
    opts = {**YDL_OPTS_BASE}
    with yt_dlp.YoutubeDL(opts) as ydl:
        return ydl.extract_info(url, download=False)


def build_formats(info: dict) -> list[FormatOut]:
    raw = info.get("formats") or []
    out: list[FormatOut] = []
    seen: set[str] = set()

    # Prefer progressive (video+audio) then video-only / audio-only
    for f in raw:
        url = f.get("url")
        if not url:
            continue
        fid = str(f.get("format_id", ""))
        key = f"{fid}:{f.get('height')}:{f.get('ext')}"
        if key in seen:
            continue
        seen.add(key)

        v = has_video(f)
        a = has_audio(f)
        if not v and not a:
            continue

        label = quality_label(f)
        if v and not a:
            label = f"{label} (video only)"
        elif a and not v:
            label = f"Audio • {label}" if "Audio" not in label else label

        out.append(
            FormatOut(
                format_id=fid,
                quality=label,
                format=ext_of(f),
                url=url,
                size_bytes=f.get("filesize") or f.get("filesize_approx"),
                has_audio=a,
                fps=f.get("fps"),
                height=f.get("height"),
                vcodec=f.get("vcodec"),
                acodec=f.get("acodec"),
                note=f.get("format_note"),
            )
        )

    # Sort: has both > height desc > audio
    def sort_key(x: FormatOut):
        both = 1 if (x.has_audio and x.height) else 0
        h = x.height or 0
        audio_only = 0 if x.height else 1
        return (-both, -h, audio_only)

    out.sort(key=sort_key)

    # Deduplicate similar heights keeping best
    best: list[FormatOut] = []
    used_h: set[int] = set()
    for f in out:
        h = f.height or -1
        if h > 0 and h in used_h and f.has_audio:
            continue
        if h > 0 and f.has_audio:
            used_h.add(h)
        best.append(f)

    return best[:30]  # limit


# ─── Routes ───────────────────────────────────────────────
@app.get("/api/health")
async def health():
    try:
        ver = yt_dlp.version.__version__
    except Exception:
        ver = "unknown"
    return {"ok": True, "yt_dlp": ver, "service": "tai-video-backend"}


@app.post("/api/extract", response_model=VideoOut)
async def extract(body: ExtractRequest):
    url = body.url.strip()
    if not url.startswith(("http://", "https://")):
        raise HTTPException(400, "URL phải bắt đầu bằng http:// hoặc https://")

    logger.info("extract: %s", url)
    try:
        info = await asyncio.to_thread(run_extract, url)
    except yt_dlp.utils.DownloadError as e:
        logger.warning("yt-dlp error: %s", e)
        raise HTTPException(422, f"yt-dlp: {e}")
    except Exception as e:
        logger.exception("extract failed")
        raise HTTPException(500, f"Lỗi server: {e}")

    if not info:
        raise HTTPException(404, "Không lấy được thông tin video")

    formats = build_formats(info)
    if not formats:
        # fallback: dùng url trực tiếp nếu extractor trả về
        if info.get("url"):
            formats = [
                FormatOut(
                    format_id="best",
                    quality="Best",
                    format=info.get("ext") or "mp4",
                    url=info["url"],
                    size_bytes=info.get("filesize"),
                    has_audio=True,
                )
            ]

    platform = detect_platform(url)
    thumb = None
    if info.get("thumbnail"):
        thumb = info["thumbnail"]
    elif info.get("thumbnails"):
        thumbs = info["thumbnails"]
        thumb = thumbs[-1].get("url") if thumbs else None

    return VideoOut(
        id=info.get("id"),
        title=info.get("title") or "Video",
        thumbnail=thumb,
        duration=int(info.get("duration") or 0),
        platform=platform,
        original_url=url,
        author=info.get("uploader") or info.get("channel") or info.get("creator"),
        description=(info.get("description") or "")[:500] or None,
        formats=formats,
    )


@app.get("/api/download")
async def download(
    url: str = Query(..., description="Original page URL"),
    format_id: str = Query("best", description="yt-dlp format id"),
):
    """Download & stream file về client (server tải giúp khi CDN chặn app)."""
    if not url.startswith(("http://", "https://")):
        raise HTTPException(400, "URL không hợp lệ")

    outtmpl = str(DOWNLOAD_DIR / "%(id)s.%(ext)s")
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": format_id if format_id != "best" else "bv*+ba/b",
        "outtmpl": outtmpl,
        "merge_output_format": "mp4",
        "socket_timeout": 60,
        "retries": 3,
    }

    def _dl():
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            path = ydl.prepare_filename(info)
            # merge may change ext
            p = Path(path)
            if not p.exists():
                for cand in DOWNLOAD_DIR.glob(f"{info.get('id')}.*"):
                    return cand, info
            return p, info

    try:
        path, info = await asyncio.to_thread(_dl)
    except Exception as e:
        raise HTTPException(500, f"Download failed: {e}")

    if not path or not Path(path).exists():
        raise HTTPException(500, "File không tồn tại sau khi tải")

    filename = sanitize_filename(info.get("title") or "video") + Path(path).suffix
    return FileResponse(
        path,
        filename=filename,
        media_type="application/octet-stream",
        background=None,
    )


@app.get("/")
async def root():
    return {
        "service": "Tải Video Backend",
        "docs": "/docs",
        "health": "/api/health",
        "extract": "POST /api/extract {\"url\": \"...\"}",
    }


if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
