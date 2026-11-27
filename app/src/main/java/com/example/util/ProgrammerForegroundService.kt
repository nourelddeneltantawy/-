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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground Service + Partial WakeLock + Dedicated Background Worker Scope for USB OTG MCU flashing.
 * Runs Read / Write / Erase / Verify / Rescue independently of UI / Activity lifecycle so
 * screen-off or switching apps never throttles or aborts transfers, while keeping live ETA & Pause state.
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

        val paused = _isPausedFlow.value
        val eta = _etaTextFlow.value
        val title = buildString {
            append(if (paused) "البرمجة متوقفة مؤقتاً" else "مبرمجة المعالجات نشطة")
            if (progressPercent in 0..100) {
                append(" ($progressPercent%)")
            }
            if (!paused && eta.isNotBlank() && eta != "--:--") {
                append(" • المتبقي $eta")
            }
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
        private const val RENEW_THROTTLE_MS = 950L
        private const val ACTION_START_OR_UPDATE = "com.example.action.PROGRAMMER_FGS_START"
        private const val ACTION_STOP = "com.example.action.PROGRAMMER_FGS_STOP"
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"

        // Dedicated service-owned worker scope that survives screen lock, backgrounding, or ViewModel churn
        private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var activeWorkerJob: Job? = null

        @Volatile
        private var fallbackWakeLock: PowerManager.WakeLock? = null
        @Volatile
        private var lastRenewUpdateMs: Long = 0L
        @Volatile
        private var lastProgressPercent: Int = -1
        @Volatile
        private var lastStatusText: String = ""
        private val lockGuard = Any()

        // Timing & ETA tracking
        @Volatile
        private var operationStartMs: Long = 0L
        @Volatile
        private var baselineBytesDone: Int = -1
        @Volatile
        private var pausedStartedMs: Long = 0L
        @Volatile
        private var accumulatedPausedMs: Long = 0L

        private val _isWorkerActiveFlow = MutableStateFlow(false)
        val isWorkerActiveFlow: StateFlow<Boolean> = _isWorkerActiveFlow.asStateFlow()

        private val _isPausedFlow = MutableStateFlow(false)
        val isPausedFlow: StateFlow<Boolean> = _isPausedFlow.asStateFlow()

        private val _etaTextFlow = MutableStateFlow("")
        val etaTextFlow: StateFlow<String> = _etaTextFlow.asStateFlow()

        fun launchFlashTask(
            context: Context,
            initialStatus: String,
            block: suspend () -> Unit
        ): Job {
            val appCtx = context.applicationContext
            activeWorkerJob?.cancel()
            resetPauseAndEtaState()
            _isWorkerActiveFlow.value = true
            acquireSessionLock(appCtx, initialStatus, 0)
            val job = workerScope.launch {
                try {
                    block()
                } catch (ce: CancellationException) {
                    throw ce
                } finally {
                    resetPauseAndEtaState()
                    _isWorkerActiveFlow.value = false
                    releaseSessionLock(appCtx)
                }
            }
            activeWorkerJob = job
            return job
        }

        fun requestPause(context: Context) {
            if (!_isWorkerActiveFlow.value || _isPausedFlow.value) return
            pausedStartedMs = System.currentTimeMillis()
            _isPausedFlow.value = true
            acquireSessionLock(
                context,
                "متوقف مؤقتاً — اضغط استكمال للمتابعة بأمان",
                lastProgressPercent
            )
        }

        fun requestResume(context: Context) {
            if (!_isPausedFlow.value) return
            if (pausedStartedMs > 0L) {
                accumulatedPausedMs += (System.currentTimeMillis() - pausedStartedMs).coerceAtLeast(0L)
                pausedStartedMs = 0L
            }
            _isPausedFlow.value = false
            acquireSessionLock(
                context,
                lastStatusText.ifBlank { "استكمال البرمجة..." },
                lastProgressPercent
            )
        }

        fun togglePauseResume(context: Context): Boolean {
            if (!_isWorkerActiveFlow.value) return false
            if (_isPausedFlow.value) {
                requestResume(context)
            } else {
                requestPause(context)
            }
            return _isPausedFlow.value
        }

        fun requestCancel() {
            _isPausedFlow.value = false
            activeWorkerJob?.cancel()
        }

        /**
         * Safe checkpoint gate called between frames/blocks across all MCU protocols.
         * Suspends cleanly without closing USB or losing state until resumed or cancelled.
         */
        suspend fun awaitIfPaused() {
            while (_isPausedFlow.value) {
                delay(120L)
            }
        }

        fun updateThroughputAndEta(bytesDone: Int, totalBytes: Int) {
            if (totalBytes <= 0 || bytesDone <= 0) {
                _etaTextFlow.value = if (totalBytes > 0) "--:--" else ""
                return
            }
            val now = System.currentTimeMillis()
            if (operationStartMs == 0L) {
                operationStartMs = now
                baselineBytesDone = bytesDone
                _etaTextFlow.value = "--:--"
                return
            }
            if (baselineBytesDone < 0) {
                baselineBytesDone = bytesDone
            }
            val processedSinceStart = (bytesDone - baselineBytesDone).coerceAtLeast(0)
            val remainingBytes = (totalBytes - bytesDone).coerceAtLeast(0)
            if (remainingBytes == 0) {
                _etaTextFlow.value = "00:00"
                return
            }
            val activeElapsedMs = (now - operationStartMs - accumulatedPausedMs).coerceAtLeast(1L)
            if (activeElapsedMs < 1200L || processedSinceStart < 256) {
                _etaTextFlow.value = "--:--"
                return
            }
            val bytesPerMs = processedSinceStart.toDouble() / activeElapsedMs.toDouble()
            if (bytesPerMs <= 0.0001) {
                _etaTextFlow.value = "--:--"
                return
            }
            val etaSeconds = ((remainingBytes.toDouble() / bytesPerMs) / 1000.0).toLong().coerceIn(0L, 86400L)
            _etaTextFlow.value = formatEtaSeconds(etaSeconds)
        }

        private fun formatEtaSeconds(totalSeconds: Long): String {
            val hours = totalSeconds / 3600L
            val mins = (totalSeconds % 3600L) / 60L
            val secs = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(Locale.US, "%02d:%02d", mins, secs)
            }
        }

        private fun resetPauseAndEtaState() {
            _isPausedFlow.value = false
            _etaTextFlow.value = ""
            operationStartMs = 0L
            baselineBytesDone = -1
            pausedStartedMs = 0L
            accumulatedPausedMs = 0L
        }

        fun acquireSessionLock(context: Context, statusText: String, progressPercent: Int = -1) {
            val appCtx = context.applicationContext
            synchronized(lockGuard) {
                lastRenewUpdateMs = System.currentTimeMillis()
                lastProgressPercent = progressPercent
                lastStatusText = statusText
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
            if (pctChanged && (now - lastRenewUpdateMs < 650L)) return
            acquireSessionLock(context, statusText, progressPercent)
        }

        fun releaseSessionLock(context: Context) {
            val appCtx = context.applicationContext
            synchronized(lockGuard) {
                lastRenewUpdateMs = 0L
                lastProgressPercent = -1
                lastStatusText = ""
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
