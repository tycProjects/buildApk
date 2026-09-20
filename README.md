# LeonTeam Overlay

Overlay nổi hiển thị PIN / FPS / GIỜ với chữ RGB 7 màu. Kotlin, Gradle, minSdk 26, targetSdk 35.

## Build APK
Cách 1 – Android Studio: File ▸ Open thư mục này ▸ đợi Gradle Sync ▸ Build ▸ Build APK(s).

Cách 2 – dòng lệnh (cần JDK 17 + Android SDK, đặt `ANDROID_HOME` hoặc file `local.properties` có `sdk.dir=...`):
```
gradle wrapper --gradle-version 8.9   # chạy 1 lần để sinh gradlew
./gradlew assembleDebug               # Windows: gradlew.bat assembleDebug
```
APK debug: `app/build/outputs/apk/debug/app-debug.apk`
APK release (ký debug key, cài được ngay): `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

Cài: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Cách đo
- PIN: `ACTION_BATTERY_CHANGED` → `EXTRA_LEVEL * 100 / EXTRA_SCALE`.
- GIỜ: `System.currentTimeMillis()` định dạng HH:mm:ss, cập nhật đúng mỗi giây.
- FPS: `ViewTreeObserver.OnDrawListener` đếm số lần cây View của overlay thực sự được vẽ; mỗi giây lấy `số_lần_vẽ / thời_gian_thực_trôi_qua (nanoTime)`.
  Đây là FPS render của overlay, KHÔNG phải FPS của game/app khác (Android không cho app thường đọc).
