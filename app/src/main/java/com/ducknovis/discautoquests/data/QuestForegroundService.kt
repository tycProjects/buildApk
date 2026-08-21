package com.ducknovis.discautoquests.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ducknovis.discautoquests.MainActivity
import com.ducknovis.discautoquests.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service used to keep the process alive while quests run.
 * - If EXTRA_KEEP_ALIVE=true: only shows notification (UI/ViewModel runs the logic).
 * - Otherwise: runs Runner itself (e.g. after boot).
 */
class QuestForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keepAliveOnly = intent?.getBooleanExtra(EXTRA_KEEP_ALIVE, false) == true
        val token = intent?.getStringExtra(EXTRA_TOKEN)
            ?: SecureTokenStore(this).getToken()

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Đang chạy Discord Auto Quest..."))

        if (keepAliveOnly) {
            // UI owns the Runner; this service just prevents process death.
            return START_STICKY
        }

        if (token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        job?.cancel()
        job = scope.launch {
            try {
                val runner = Runner(token) { event ->
                    when (event) {
                        is ProgressEvent.Log -> updateNotification(event.message)
                        is ProgressEvent.Status -> updateNotification("Quest ${event.questId.take(6)}: ${event.status}")
                        is ProgressEvent.Balance -> updateNotification("Orbs: ${event.orbs}")
                        else -> {}
                    }
                }
                runner.init()
                val pending = runner.pending()
                if (pending.isEmpty()) {
                    updateNotification("Không còn quest nào")
                } else {
                    runner.run()
                    updateNotification("Hoàn thành")
                }
            } catch (e: Exception) {
                updateNotification("Lỗi: ${e.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DAQ Quest Runner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Thông báo khi đang chạy auto quest"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Discord Auto Quest")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "daq_quest_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TOKEN = "token"
        const val EXTRA_KEEP_ALIVE = "keep_alive"

        fun start(context: Context, token: String, keepAliveOnly: Boolean = true) {
            val intent = Intent(context, QuestForegroundService::class.java).apply {
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_KEEP_ALIVE, keepAliveOnly)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QuestForegroundService::class.java))
        }
    }
}
