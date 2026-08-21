package com.ducknovis.discautoquests.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional: after reboot, if a token is stored, can restart background work.
 * Currently only a placeholder – enable if you want auto-resume.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Intentionally no-op for safety (user must start manually).
        // Uncomment below if you want auto-start after boot:
        // val token = SecureTokenStore(context).getToken()
        // if (!token.isNullOrBlank()) {
        //     QuestForegroundService.start(context, token)
        // }
    }
}
