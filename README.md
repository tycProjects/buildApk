# Roblox Graphics Tool v2

Đã sửa:
- Có nút `Ủy quyền Shizuku` và trạng thái được cập nhật sau khi người dùng cấp quyền.
- Dùng Shizuku API 13.1.5 + provider.
- Nhận diện cả Roblox quốc tế `com.roblox.client` và Roblox VN `com.roblox.client.vnggames`.
- Nút Mở Roblox thử cả hai package.

Roblox VN trên Google Play dùng package `com.roblox.client.vnggames`.

## Build
Mở thư mục này trong Android Studio → Sync Gradle → Build APK.

## Shizuku
1. Cài và khởi động Shizuku.
2. Mở app này.
3. Bấm `Ủy quyền Shizuku`.
4. Chấp nhận trong Shizuku.
5. Khi hiện `🟢 Shizuku: ĐÃ ỦY QUYỀN`, các nút tool mới cho phép chạy.

Lưu ý: các nút đồ họa hiện là hook/UI; chúng chưa tự thay đổi cấu hình đồ họa nội bộ của Roblox.
