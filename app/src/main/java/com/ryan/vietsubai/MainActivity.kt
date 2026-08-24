package com.ryan.vietsubai

import android.Manifest
import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryan.vietsubai.ui.VietsubAIApp
import com.ryan.vietsubai.ui.VietsubAIViewModel

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("processing", "Vietsub AI processing", NotificationManager.IMPORTANCE_LOW))
        }
        setContent {
            MaterialTheme {
                Surface { VietsubAIApp(viewModel<VietsubAIViewModel>()) }
            }
        }
        // Android 13+: notifications are runtime permission. Request it only after the
        // activity has attached its UI, and avoid repeatedly prompting after the user
        // has already made a choice. Foreground services still require a notification
        // even when this permission is denied.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("permission_state", MODE_PRIVATE)
            if (!prefs.getBoolean("notification_requested", false)) {
                prefs.edit().putBoolean("notification_requested", true).apply()
                window.decorView.post {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
