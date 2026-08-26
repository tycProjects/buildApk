# Background export với WorkManager

`VideoBurnInWorker` là `CoroutineWorker` chạy ở foreground. `EditorActivity` truyền `video_uri` và SRT vào `Data`, sau đó enqueue `OneTimeWorkRequest`. Worker gọi `setForeground()` trước khi chạy FFmpeg nên hệ điều hành hiển thị notification liên tục; người dùng có thể tắt màn hình hoặc rời Activity mà tác vụ vẫn được WorkManager quản lý.[1]

## Luồng

```text
EditorActivity
  → OneTimeWorkRequest<VideoBurnInWorker>
  → setForeground(notification + Cancel)
  → FfmpegRenderer.burnIn(...)
  → setProgress + update notification
  → Result.success(output_path)
```

Notification có nút **Hủy** dùng `WorkManager.createCancelPendingIntent(workerId)`. `onStopped()` gọi `FFmpegKit.cancel()` và hủy callback scope. Lỗi tạm thời được retry tối đa hai lần; sau đó trả `Result.failure` kèm thông báo lỗi.

## Android 14+

Project khai báo `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_DATA_SYNC`, đồng thời merge `SystemForegroundService` của WorkManager với `android:foregroundServiceType="dataSync"`. Android yêu cầu foreground service type khi app target API 34 trở lên; `dataSync` phù hợp với local file processing/import/export.[2]

## Lưu ý thực tế

WorkManager bảo đảm lập lịch bền vững tốt hơn Activity, nhưng không phải cơ chế khôi phục giữa mọi trường hợp process bị kill cứng hoặc người dùng force-stop app. Video đầu vào phải được copy vào vùng cache/app-readable; file output nên được chuyển sang MediaStore hoặc Storage Access Framework sau khi worker thành công.

Đối với video rất dài, nên chuyển `FfmpegRenderer` sang API trả về session ID để `onStopped()` hủy đúng phiên FFmpeg thay vì dùng `FFmpegKit.cancel()` toàn cục. Trên Android 16, long-running worker dùng foreground service có thể gặp job quota; nếu workload đạt mức đó, cân nhắc foreground service trực tiếp hoặc user-initiated data transfer job theo hướng dẫn nền tảng.[1]

### Tài liệu tham khảo

[1]: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running "Android long-running Workers"
[2]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android foreground service types"
