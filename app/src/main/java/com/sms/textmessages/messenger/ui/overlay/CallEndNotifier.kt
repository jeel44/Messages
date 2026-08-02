package com.sms.textmessages.messenger.ui.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.ui.home.getContactName

object CallEndNotifier {

    private const val TAG = "CALLEND_DEBUG"
    private const val CHANNEL_ID = "call_end_fallback"
    private const val NOTIFICATION_ID = 7102

    fun notifyCallEnded(
        context: Context,
        callType: CallEndType,
        phoneNumber: String?,
        durationMs: Long
    ) {
        val displayName = phoneNumber?.let { getContactName(context, it) }?.takeIf { it.isNotBlank() }
            ?: phoneNumber
            ?: "Unknown"

        createChannel(context)

        val intent = Intent(context, CallEndActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallEndActivity.EXTRA_PHONE, phoneNumber)
            putExtra(CallEndActivity.EXTRA_TYPE, callType.name)
            putExtra(CallEndActivity.EXTRA_DURATION, durationMs)
            putExtra(CallEndActivity.EXTRA_DISPLAY_NAME, displayName)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (callType) {
            CallEndType.MISSED -> "Missed call"
            CallEndType.INCOMING -> "Call ended"
            CallEndType.OUTGOING -> "Call ended"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setContentTitle(title)
            .setContentText(displayName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "CallEndNotifier: posted fallback notification for $displayName")
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Call ended",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
