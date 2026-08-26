# UI hiện đại và video preview mượt

Giao diện dùng Material 3 Dark, nền gradient tối, accent tím/xanh, card bo góc và animation fade-slide nhẹ. `UiMotion.kt` cung cấp entrance animation 240–280ms và press scale 0.97 trong 80–140ms.

Video preview dùng Media3 ExoPlayer với `DefaultLoadControl`: buffer ban đầu vừa phải để giảm thời gian chờ, ưu tiên thời gian hơn ngưỡng kích thước, seek increment 5 giây và `PlayerView` resize mode `fit`. Subtitle overlay được cập nhật bằng `Choreographer.FrameCallback` theo frame UI; chỉ cập nhật TextView khi cue hiện tại thay đổi, tránh polling coroutine 80ms và giảm redraw không cần thiết.

Player không còn bị release trong `onStop()`, nên khi người dùng mở màn hình khác hoặc tắt màn hình, trạng thái playback được giữ. Player chỉ release trong `onDestroy()`. Frame callback dừng ở `onPause()` và khởi động lại ở `onResume()`.

Nếu muốn tăng độ mượt trên thiết bị yếu, có thể hạ preview xuống 720p, tắt hiệu ứng nền động và giảm tần suất cập nhật overlay xuống 30fps; không nên animate toàn bộ danh sách cue khi người dùng cuộn.
