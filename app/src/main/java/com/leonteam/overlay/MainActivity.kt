package com.leonteam.overlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshUi()
            if (Settings.canDrawOverlays(this)) requestNotificationPermissionIfNeeded()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15 (targetSdk 35) bắt buộc edge-to-edge → chừa chỗ cho status/nav bar
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        btnStart.setOnClickListener { startOverlay() }
        btnStop.setOnClickListener { stopOverlay() }

        // Vừa mở app → xin quyền "Hiển thị trên các ứng dụng khác"
        ensureOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            requestNotificationPermissionIfNeeded()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_grant) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton(R.string.perm_later, null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_permission, Toast.LENGTH_SHORT).show()
            ensureOverlayPermission()
            return
        }
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        handler.postDelayed({ refreshUi() }, 300)
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        handler.postDelayed({ refreshUi() }, 300)
    }

    private fun refreshUi() {
        val running = OverlayService.isRunning
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        btnStart.alpha = if (running) 0.4f else 1f
        btnStop.alpha = if (running) 1f else 0.4f
        tvStatus.setText(if (running) R.string.status_running else R.string.status_stopped)
    }
}
