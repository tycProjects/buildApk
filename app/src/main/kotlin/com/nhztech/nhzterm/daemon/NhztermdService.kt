package com.nhztech.nhzterm.daemon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.nhztech.nhzterm.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * nhztermd — the daemon. A real Android Foreground Service (concept §3),
 * and that choice is structural, not cosmetic:
 *
 *  - The persistent notification shown THE INSTANT the service starts is
 *    what exempts nhztermd from Android 12+'s phantom-process-killer
 *    (§7.3) — a service that delays its notification is unprotected
 *    during that window, so notification comes before ALL other boot work.
 *  - START_STICKY means Android itself restarts the service if it is ever
 *    killed (§7.2) — no watchdog process, no keep-alive hacks.
 *
 * Boot order (build plan Part 1 Phases 0-5, concept §12.3 checklist):
 *   notification -> runtime dirs -> auth token -> stage nhzsh ->
 *   PTY probe -> control channel -> api server.
 */
class NhztermdService : Service() {

    private var apiServer: ApiServer? = null
    private var controlServer: ControlServer? = null
    private var sessionManager: SessionManager? = null
    private var processManager: ProcessManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var dirs: RuntimeDirs? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        showNotificationImmediately() // §7.3/§12.3 — before anything else
        Thread { bootDaemon() }.apply { isDaemon = true }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // §7.2: Android restarts us after a kill — sessions are restored
        // from metadata and reattached where the PTY tool allows.
        return START_STICKY
    }

    override fun onDestroy() {
        try { apiServer?.stop() } catch (ignored: Exception) {}
        try { controlServer?.stop() } catch (ignored: Exception) {}
        releaseWakeLock()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // boot
    // ------------------------------------------------------------------

    private fun showNotificationImmediately() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DaemonConfig.CHANNEL_ID,
            "nhztermd",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Terminal daemon status"
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)

        val n = Notification.Builder(this, DaemonConfig.CHANNEL_ID)
            .setContentTitle("nhztermd running")
            .setContentText("Terminal sessions alive — " + DaemonConfig.MAX_SESSIONS + " max")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                DaemonConfig.NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(DaemonConfig.NOTIFICATION_ID, n)
        }
    }

    private fun bootDaemon() {
        try {
            val d = RuntimeDirs.ensure(this)
            dirs = d
            log(d, "boot: dirs ready")

            val token = AuthToken.ensure(d.tokenFile)
            log(d, "boot: token ready")

            // §12.3: re-verify staging on EVERY startup — this exact step
            // was broken once before; never assume it silently.
            val staged = ShellStager.stage(this, d)
            log(d, "boot: nhzsh staged=$staged path=" + d.stagedShell.absolutePath)

            val sm = SessionManager(this, d) { ev -> apiServer?.broadcast(ev) }
            sm.onWakeLockRelevantChange = { updateWakeLock() }
            sm.start()
            val pm = ProcessManager(d, sm)
            sessionManager = sm
            processManager = pm
            log(d, "boot: pty tool=" + (sm.tool ?: "none"))

            controlServer = ControlServer { sid, pid -> sm.onForegroundPid(sid, pid) }
                .also { it.start() }

            apiServer = ApiServer(token, sm, pm).also { it.start() }

            updateWakeLock()
            log(d, "boot: daemon ready")
        } catch (e: Exception) {
            // §7.2 in-process crash recovery: log honestly, reset tracking
            // state. Full process death is covered by START_STICKY.
            Log.e(TAG, "boot failed", e)
            try { dirs?.let { log(it, "boot FAILED: " + e.message) } } catch (ignored: Exception) {}
            sessionManager = null
            processManager = null
            apiServer = null
            controlServer = null
        }
    }

    // ------------------------------------------------------------------
    // wake lock — §8: opt-in, OFF by default. Its purpose is keeping the
    // CPU awake during active work with the screen off (long builds),
    // NOT surviving process death — the Foreground Service already
    // guarantees that on its own.
    // ------------------------------------------------------------------

    private fun updateWakeLock() {
        try {
            val enabled = getSharedPreferences("nhzterm_prefs", Context.MODE_PRIVATE)
                .getBoolean("wake_lock", DaemonConfig.WAKE_LOCK_DEFAULT)
            val active = sessionManager?.anyForegroundRunning() == true
            if (enabled && active) acquireWakeLock() else releaseWakeLock()
        } catch (ignored: Exception) {
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nhzterm:daemon")
            wl.setReferenceCounted(false)
            wl.acquire()
            wakeLock = wl
        } catch (ignored: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {
        }
        wakeLock = null
    }

    // ------------------------------------------------------------------
    // crash handling & logging (§7.2, var/log/nhztermd.log per §12.2)
    // ------------------------------------------------------------------

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val d = dirs ?: RuntimeDirs.ensure(this)
                log(d, "FATAL on ${thread.name}: $throwable")
            } catch (ignored: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun log(d: RuntimeDirs, message: String) {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            d.logFile.appendText("$stamp $message\n")
        } catch (ignored: Exception) {
        }
        Log.i(TAG, message)
    }

    companion object {
        private const val TAG = "nhztermd"

        /** §7.1 autostart entry point — no user ever starts the daemon by hand. */
        fun start(context: Context) {
            val intent = Intent(context, NhztermdService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
