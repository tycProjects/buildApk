package com.app.vietsubai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

object ExportNotification {
    private const val CHANNEL = "video_export_queue"
    private const val ID = 2601
    fun create(context: Context, workId: UUID, text: String): ForegroundInfo {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Video export queue", NotificationManager.IMPORTANCE_LOW))
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Vietsub AI").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_delete, "Hủy video này", cancel).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(ID, notification)
    }
}
