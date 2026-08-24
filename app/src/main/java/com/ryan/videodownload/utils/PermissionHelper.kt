package com.ryan.videodownload.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    const val REQUEST_CODE = 1001

    /** Danh sách quyền cần request runtime theo API level */
    fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()

        // Notification Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Storage chỉ cần API <= 28 (WRITE), đọc <= 32
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Android 10-12: app-specific dir không cần permission,
            // nhưng nếu muốn đọc media public có thể request READ
            // list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 13+: READ_MEDIA_VIDEO nếu cần scan thư viện
            // list.add(Manifest.permission.READ_MEDIA_VIDEO)
        }

        return list.toTypedArray()
    }

    fun hasAllPermissions(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestIfNeeded(activity: Activity) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQUEST_CODE)
        }
    }

    fun isStorageWritable(context: Context): Boolean {
        // Từ Android 10 trở đi dùng app-specific external → luôn ghi được
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
