package com.sms.textmessages.messenger.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.R

/**
 * Short-lived foreground service while the during-call overlay is visible.
 * Keeps process priority mid-call without a permanent keep-alive.
 */
class CallOverlayForegroundService : Service() {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
        private const val CHANNEL_ID = "call_overlay_channel"
        private const val NOTIFICATION_ID = 7101

        fun start(context: Context) {
            val intent = Intent(context, CallOverlayForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "CallOverlayForegroundService.start requested")
            } catch (e: Exception) {
                Log.w(TAG, "CallOverlayForegroundService.start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallOverlayForegroundService::class.java))
                Log.d(TAG, "CallOverlayForegroundService.stop requested")
            } catch (e: Exception) {
                Log.w(TAG, "CallOverlayForegroundService.stop failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "CallOverlayForegroundService: startForeground ok")
        } catch (e: Exception) {
            Log.e(TAG, "CallOverlayForegroundService: startForeground failed - ${e.message}", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallOverlayForegroundService.onDestroy")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps caller ID visible during calls"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Caller ID active")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
