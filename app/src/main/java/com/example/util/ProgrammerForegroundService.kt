package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * Foreground Service + Partial WakeLock guardian for USB OTG MCU flashing/extraction.
 * Prevents CPU sleep / screen-lock throttling during long 78K0 / R8C operations so UART
 * transfers and atomic checkpoints (78k0_rescue.json + 78k0_partial.bin) never stall.
 */
class ProgrammerForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        acquireLocalWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseLocalWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "جاري تأمين اتصال USB OTG ومنع السكون..."
        val progressPct = intent?.getIntExtra(EXTRA_PROGRESS_PERCENT, -1) ?: -1
        val notification = buildNotification(statusText, progressPct)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireLocalWakeLock()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocalWakeLock()
        super.onDestroy()
    }

    private fun acquireLocalWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (_: Throwable) {}
    }

    private fun releaseLocalWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "جلسة برمجة المعالجات USB OTG",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "يحافظ على استقرار المنفذ ومنع سكون النظام أثناء القراءة أو الكتابة"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(statusText: String, progressPercent: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingFlags)

        val title = if (progressPercent in 0..100) {
            "مبرمجة المعالجات نشطة ($progressPercent%)"
        } else {
            "مبرمجة المعالجات نشطة (USB OTG)"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "mcu_programmer_session_channel"
        private const val NOTIFICATION_ID = 4780
        private const val WAKE_LOCK_TAG = "WasherMotors::McuFlashWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L
        private const val RENEW_THROTTLE_MS = 1200L
        private const val ACTION_START_OR_UPDATE = "com.example.action.PROGRAMMER_FGS_START"
        private const val ACTION_STOP = "com.example.action.PROGRAMMER_FGS_STOP"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"

        @Volatile
        private var fallbackWakeLock: PowerManager.WakeLock? = null
        @Volatile
        private var lastRenewUpdateMs: Long = 0L
        @Volatile
        private var lastProgressPercent: Int = -1
        private val lockGuard = Any()

        fun acquireSessionLock(context: Context, statusText: String, progressPercent: Int = -1) {
            val appCtx = context.applicationContext
            synchronized(lockGuard) {
                lastRenewUpdateMs = System.currentTimeMillis()
                lastProgressPercent = progressPercent
                try {
                    val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (fallbackWakeLock == null) {
                        fallbackWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                            setReferenceCounted(false)
                        }
                    }
                    fallbackWakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
                } catch (_: Throwable) {}
            }
            try {
                val intent = Intent(appCtx, ProgrammerForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_STATUS_TEXT, statusText)
                    putExtra(EXTRA_PROGRESS_PERCENT, progressPercent)
                }
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (_: Throwable) {}
        }

        fun renewSessionLock(context: Context, statusText: String, progressPercent: Int = -1) {
            val now = System.currentTimeMillis()
            val pctChanged = progressPercent in 0..100 && progressPercent != lastProgressPercent
            if (!pctChanged && (now - lastRenewUpdateMs < RENEW_THROTTLE_MS)) return
            if (pctChanged && (now - lastRenewUpdateMs < 750L)) return
            acquireSessionLock(context, statusText, progressPercent)
        }

        fun releaseSessionLock(context: Context) {
            val appCtx = context.applicationContext
            synchronized(lockGuard) {
                lastRenewUpdateMs = 0L
                lastProgressPercent = -1
                try {
                    if (fallbackWakeLock?.isHeld == true) {
                        fallbackWakeLock?.release()
                    }
                } catch (_: Throwable) {}
                fallbackWakeLock = null
            }
            try {
                val intent = Intent(appCtx, ProgrammerForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                appCtx.startService(intent)
            } catch (_: Throwable) {}
        }
    }
}
