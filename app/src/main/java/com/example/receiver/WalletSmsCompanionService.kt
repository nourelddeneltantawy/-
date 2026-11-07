package com.example.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.WalletRoutingRepository

/**
 * Android Foreground Service
 * Keeps the SMS collector companion active in background on the admin's device,
 * ensuring automatic processing of incoming transfer messages even when the app is closed.
 */
class WalletSmsCompanionService : Service() {

    override fun onCreate() {
        super.onCreate()
        WalletRoutingRepository.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مزامنة تحويلات المحافظ الذكية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة رصد رسائل فودافون كاش وإنستاباي لتحديث سعة المحافظ أوتوماتيكياً"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("رصد تحويلات المحافظ الذكية نشط ⚡")
            .setContentText("جاري مراقبة رسائل الدفع وتحديث السعة والأرصدة تلقائياً...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "wallet_sms_companion_channel"
        const val NOTIFICATION_ID = 9021

        fun startService(context: Context) {
            val intent = Intent(context, WalletSmsCompanionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WalletSmsCompanionService::class.java)
            context.stopService(intent)
        }
    }
}
