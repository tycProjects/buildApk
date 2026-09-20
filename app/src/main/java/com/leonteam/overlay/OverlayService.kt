package com.leonteam.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Foreground Service giữ overlay nổi trên các app khác.
 *
 *  - PIN  : BatteryManager.EXTRA_LEVEL / EXTRA_SCALE từ ACTION_BATTERY_CHANGED (thật).
 *  - GIỜ  : System.currentTimeMillis() cập nhật mỗi giây (thật).
 *  - FPS  : đếm số lần cây View của overlay được vẽ thực tế (OnDrawListener) mỗi giây.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.leonteam.overlay.action.START"
        const val ACTION_STOP = "com.leonteam.overlay.action.STOP"
        private const val CHANNEL_ID = "leonteam_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var overlayRoot: View? = null
    private var statsView: TextView? = null

    // Dữ liệu hiển thị
    private var batteryPercent = -1
    private var fps = -1
    private var timeText = "--:--:--"

    // Đo FPS
    private var drawCount = 0
    private var lastTickNanos = 0L
    private val drawListener = ViewTreeObserver.OnDrawListener { drawCount++ }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var batteryReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            readBattery(intent)
            render()
        }
    }

    /** Chạy mỗi giây (căn theo ranh giới giây của đồng hồ hệ thống). */
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            val delay = 1000L - (System.currentTimeMillis() % 1000L) + 2L
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Phải gọi startForeground sớm sau startForegroundService().
        startInForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayRoot == null) showOverlay()
        isRunning = true
        return START_NOT_STICKY // bị kill thì không tự sống lại
    }

    /** Người dùng vuốt app khỏi Recents → dừng service, gỡ overlay. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        if (batteryReceiverRegistered) {
            runCatching { unregisterReceiver(batteryReceiver) }
            batteryReceiverRegistered = false
        }

        overlayRoot?.let { root ->
            runCatching {
                val vto = root.viewTreeObserver
                if (vto.isAlive) vto.removeOnDrawListener(drawListener)
            }
            runCatching { windowManager.removeViewImmediate(root) }
        }
        overlayRoot = null
        statsView = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Overlay

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        statsView = root.findViewById(R.id.tvOverlayStats)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = statusBarHeight() + dp(4)
        }

        root.setOnTouchListener(DragListener(lp))
        windowManager.addView(root, lp)
        overlayRoot = root

        // Bắt đầu đếm frame thực tế của overlay
        root.viewTreeObserver.addOnDrawListener(drawListener)
        drawCount = 0
        lastTickNanos = System.nanoTime()

        // Căn giữa phía trên sau khi đã biết kích thước
        root.post {
            val (sw, _) = screenSize()
            lp.x = ((sw - root.width) / 2).coerceAtLeast(0)
            runCatching { windowManager.updateViewLayout(root, lp) }
        }

        // PIN: sticky broadcast cho giá trị ngay lập tức + cập nhật khi thay đổi
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        batteryReceiverRegistered = true
        readBattery(sticky)

        tick()
        handler.postDelayed(ticker, 1000L - (System.currentTimeMillis() % 1000L) + 2L)
    }

    private fun readBattery(intent: Intent?) {
        intent ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            batteryPercent = Math.round(level * 100f / scale)
        }
    }

    private fun tick() {
        val now = System.nanoTime()
        val elapsed = now - lastTickNanos
        // FPS = số lần overlay được vẽ / thời gian thực đã trôi qua
        if (elapsed >= 500_000_000L) {
            fps = Math.round(drawCount * 1_000_000_000.0 / elapsed).toInt()
            drawCount = 0
            lastTickNanos = now
        }
        timeFormat.timeZone = TimeZone.getDefault()
        timeText = timeFormat.format(Date())
        render()
    }

    private fun render() {
        val battery = if (batteryPercent >= 0) "$batteryPercent%" else "--%"
        val fpsText = if (fps >= 0) fps.toString() else "--"
        statsView?.text = "🔋 PIN: $battery   🎮 FPS: $fpsText FPS   🕒 GIỜ: $timeText"
    }

    /** Kéo overlay bằng ngón tay, giới hạn trong màn hình. */
    private inner class DragListener(
        private val lp: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (sw, sh) = screenSize()
                    lp.x = (startX + (e.rawX - touchX).toInt())
                        .coerceIn(0, (sw - v.width).coerceAtLeast(0))
                    lp.y = (startY + (e.rawY - touchY).toInt())
                        .coerceIn(0, (sh - v.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(v, lp) }
                    return true
                }
            }
            return false
        }
    }

    // ------------------------------------------------------------ Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_overlay),
            getString(R.string.notif_action_stop), stopPi
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overlay)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }

    // ----------------------------------------------------------------- Helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}
