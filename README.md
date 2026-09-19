# Roblox Graphics Tool - Android

Đây là skeleton Android Studio để build APK.

## Build
Mở thư mục bằng Android Studio, Gradle Sync rồi Build > Build APK(s).

## Shizuku
Project này chưa tự thực thi lệnh Shizuku và không sửa file/data của Roblox.
Nút đồ họa hiện là giao diện/khung để nối implementation Shizuku sau này.

Để tích hợp Shizuku thật, thêm thư viện/API Shizuku chính thức vào Gradle và kiểm tra permission trước khi chạy các thao tác Android hợp lệ.

Package Roblox mặc định được mở là `com.roblox.client`; nếu bản Roblox của thiết bị dùng package khác thì đổi trong `MainActivity.java`.
