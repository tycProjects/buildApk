-- KENRSER OWNER MIGRATION
-- Chạy 1 lần trong Supabase SQL Editor.
-- Mật khẩu không lưu dạng plain-text; đây là scrypt hash tương thích với api/_lib/session.js.

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM public.users WHERE lower(username) = 'kenrser') THEN
    UPDATE public.users
    SET password_hash = 'scrypt$16384$8$1$c34d46341082be56ed45187169805b6c$d0703129690d84cf1cdeb257d0a8434ff7fd63351d220a2c19f6f4ee36306f6ed6b06d130af9d8e2afb4a848b68910b382d55f91c0b6c6c8cbd43ff0f65a604d',
        role = 'admin',
        banned = false,
        ban_reason = NULL
    WHERE lower(username) = 'kenrser';
  ELSIF EXISTS (SELECT 1 FROM public.users WHERE lower(username) = 'kietlaanh') THEN
    UPDATE public.users
    SET username = 'Kenrser',
        password_hash = 'scrypt$16384$8$1$c34d46341082be56ed45187169805b6c$d0703129690d84cf1cdeb257d0a8434ff7fd63351d220a2c19f6f4ee36306f6ed6b06d130af9d8e2afb4a848b68910b382d55f91c0b6c6c8cbd43ff0f65a604d',
        role = 'admin',
        banned = false,
        ban_reason = NULL
    WHERE lower(username) = 'kietlaanh';
  ELSE
    RAISE NOTICE 'Không tìm thấy Kenrser/Kietlaanh. Hãy đăng ký username Kenrser trước, sau đó chạy lại migration.';
  END IF;

  -- Nếu còn tài khoản Kietlaanh riêng, không để tài khoản đó là super-admin.
  UPDATE public.users
  SET role = CASE WHEN lower(username) = 'kenrser' THEN 'admin' ELSE 'member' END
  WHERE lower(username) = 'kietlaanh';
END $$;
