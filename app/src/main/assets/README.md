# KENRSER — Bản triển khai trên Vercel

Đây là phiên bản của web KENRSER chạy trên **Vercel** (serverless functions) thay vì cPanel/PHP.
Frontend giữ nguyên (vanilla HTML/CSS/JS); backend PHP được port sang **Node.js** không cần cài
thêm phụ thuộc nào (chỉ dùng `node:crypto` có sẵn).

## Cấu trúc

```
vercel/
├── index.html, script.js, style.css, ...   # frontend (copy nguyên bản)
├── login.html, register.html, admin.html   # trang tài khoản
├── api/
│   ├── tmdb.js      # proxy TMDB, ẩn API key, whitelist endpoint, cache
│   ├── sync.js      # đồng bộ qua PIN 6 số (TTL 600s, đọc một lần)
│   ├── auth.js      # đăng ký/đăng nhập/đăng xuất/phiên/admin
│   └── _lib/        # code dùng chung (không phải route vì prefix "_")
│       ├── http.js      # security headers, CORS, JSON, rate-limit (Upstash)
│       ├── session.js   # phiên cookie ký HMAC + băm mật khẩu scrypt
│       ├── users.js     # kho tài khoản (Supabase)
│       ├── store.js     # kho sync (Upstash hoặc Supabase)
│       ├── upstash.js   # client Upstash Redis REST
│       └── supabase.js  # client Supabase PostgREST
├── vercel.json     # rewrite *.php -> function, security headers
├── package.json    # type: module, node >= 18
└── .env.example    # danh sách biến môi trường
```

Frontend vẫn gọi `/api/tmdb.php`, `/api/sync.php`, `/api/auth.php`. `vercel.json` **rewrite**
các đường dẫn `.php` này sang function Node tương ứng, nên không phải sửa frontend.

## Khác biệt so với bản cPanel

- **Phiên đăng nhập**: bản cPanel dùng PHP session (lưu ở server). Serverless không có trạng
  thái nên bản này dùng **cookie đã ký HMAC-SHA256** (HttpOnly, Secure, SameSite=Lax) — cần
  biến `SESSION_SECRET`.
- **Băm mật khẩu**: dùng `scrypt` (module crypto sẵn có). Hash tạo ở bản Vercel **không** tương
  thích ngược với hash bcrypt của bản PHP — nên dùng cơ sở dữ liệu riêng cho bản Vercel, hoặc
  đăng ký lại tài khoản.
- **Lưu trữ**: không dùng MySQL (không hợp serverless). Dùng **Upstash Redis REST** cho sync +
  rate-limit, **Supabase** cho tài khoản (và có thể cho cả sync).
- **Rate-limit**: dựa trên Upstash (`INCR`/`EXPIRE`). Nếu không cấu hình Upstash thì bỏ qua
  rate-limit (endpoint vẫn chạy).

## Biến môi trường

Xem `.env.example`. Tối thiểu:

| Biến | Bắt buộc | Dùng để |
|------|----------|---------|
| `TMDB_API_KEY` | ✅ | Proxy TMDB |
| `SESSION_SECRET` | ✅ (nếu dùng tài khoản) | Ký cookie phiên. Sinh: `openssl rand -hex 32` |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | ✅ (cho tài khoản) | Kho users/sync |
| `UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN` | tùy chọn | Sync + rate-limit |
| `ALLOWED_ORIGIN` | tùy chọn | CORS; để rỗng => suy từ Host |

> Kho lưu **SYNC** ưu tiên Upstash, nếu không có thì dùng Supabase. Kho **TÀI KHOẢN** dùng Supabase.

### Tạo bảng Supabase (SQL Editor)

```sql
create table if not exists users (
  id bigint generated always as identity primary key,
  username text not null unique,
  email text not null unique,
  password_hash text not null,
  role text not null default 'member',
  created_at bigint not null
);

create table if not exists sync (
  pin text primary key,
  data text not null,
  expires_at bigint not null
);
```

## Triển khai

### Cách 1 — Vercel CLI

```bash
npm i -g vercel
cd vercel
vercel            # lần đầu: liên kết project
# thêm biến môi trường:
vercel env add TMDB_API_KEY
vercel env add SESSION_SECRET
vercel env add SUPABASE_URL
vercel env add SUPABASE_SERVICE_KEY
# (tùy chọn) UPSTASH_REDIS_REST_URL, UPSTASH_REDIS_REST_TOKEN, ALLOWED_ORIGIN
vercel --prod
```

### Cách 2 — Dashboard

1. Đẩy thư mục `vercel/` lên một repo Git (hoặc đặt Root Directory = `vercel`).
2. Vercel > New Project > import repo.
3. **Settings > Environment Variables**: thêm các biến ở trên.
4. Deploy.

### Chạy thử cục bộ

```bash
cd vercel
cp .env.example .env.local   # điền giá trị
vercel dev
```

> Lưu ý: đăng nhập/đồng bộ trên `localhost` bị frontend chủ động tắt (cần HTTPS thật).

## Tài khoản admin

Tài khoản đầu tiên đăng ký với username **`Kenrser`** (không phân biệt hoa/thường) sẽ tự
động là admin. Admin có thể cấp/thu quyền cho email khác trong trang `admin.html`.

## Kiểm thử nhanh

- `https://<domain>/api/tmdb.php?ep=/trending/movie/day` → JSON phim thịnh hành.
- Đăng ký tại `/register.html`, đăng nhập `/login.html`, kiểm tra `/api/auth.php?action=me`.
- Tạo mã PIN ở modal đồng bộ, nhập mã ở thiết bị khác để khôi phục.


## Owner KENRSER

Sau khi deploy bản này, chạy `owner-kenrser.sql` trong Supabase SQL Editor để đặt tài khoản
**Kenrser** thành super-admin và đặt lại mật khẩu theo cấu hình bạn yêu cầu.

## Chặn quảng cáo trong trình phát

Frontend đã:
- sửa bộ nhận diện URL quảng cáo bị lỗi regex;
- chặn popup/mở tab mới từ trang chính;
- thêm `sandbox` cho iframe player để hạn chế popup và điều hướng top-level do player gây ra.

Lưu ý: quảng cáo được render bên trong iframe cross-origin vẫn do máy chủ player kiểm soát,
nên frontend không thể xóa trực tiếp nội dung quảng cáo bên trong iframe.
