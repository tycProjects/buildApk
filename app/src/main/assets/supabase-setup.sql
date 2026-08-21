-- KENRSER — Khởi tạo cơ sở dữ liệu Supabase (chạy 1 lần).
-- Cách dùng: Supabase Dashboard > SQL Editor > New query > dán toàn bộ > Run.

-- Bảng tài khoản người dùng
create table if not exists public.users (
  id            bigint generated always as identity primary key,
  username      text    not null unique,
  email         text    not null unique,
  password_hash text    not null,
  role          text    not null default 'member',
  banned        boolean not null default false,
  user_code     text    unique,
  created_at    bigint  not null
);

-- Migration an toàn cho bảng đã tồn tại trước khi có cột banned.
alter table public.users add column if not exists banned boolean not null default false;

-- Mã định danh cố định của mỗi tài khoản (dùng khi xin unban qua Discord).
-- Sinh một lần khi tạo tài khoản, không đổi về sau.
alter table public.users add column if not exists user_code text;

-- Điền mã cho các tài khoản cũ chưa có (định dạng KR-XXXXXXXX viết hoa).
update public.users
set user_code = 'KR-' || upper(substr(md5(random()::text || id::text), 1, 8))
where user_code is null;

-- Đảm bảo tính duy nhất của user_code.
create unique index if not exists idx_users_user_code on public.users (user_code);

-- Bảng đồng bộ qua mã PIN (TTL, đọc một lần rồi xóa)
create table if not exists public.sync (
  pin        text   primary key,
  data       text   not null,
  expires_at bigint not null
);

-- Chỉ mục phụ giúp truy vấn/nhanh dọn dữ liệu hết hạn
create index if not exists idx_users_email on public.users (email);
create index if not exists idx_sync_expires on public.sync (expires_at);
