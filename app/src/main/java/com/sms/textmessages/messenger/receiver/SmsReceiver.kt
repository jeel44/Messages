package com.sms.textmessages.messenger.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sms.textmessages.messenger.ui.ads.OverlayAdService
import kotlinx.coroutines.*
import android.app.PendingIntent

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) return

        val senderRaw = messages[0].displayOriginatingAddress ?: "Unknown"
        val sender = senderRaw.replace("\\s".toRegex(), "")

        val message = messages.joinToString("") { it.displayMessageBody ?: "" }

        val db = com.sms.textmessages.messenger.data.db.AppDatabase.getDatabase(context)
        val dao = db.threadDao()

        val thread = com.sms.textmessages.messenger.data.db.ThreadEntity(
            phone = sender,
            lastMessage = message,
            date = System.currentTimeMillis(),
            isRead = false,
            threadId = System.currentTimeMillis() // temporary unique id
        )
        dao.insertThreads(listOf(thread))

        Log.d("TRACE_SMS", "SMS received from=$sender msg=$message time=${System.currentTimeMillis()}")

        Log.d("SMS_RECEIVER", "Sender: $sender Message: $message")

        // Overlay
        val serviceIntent = Intent(context, OverlayAdService::class.java)
        serviceIntent.putExtra("sender", sender)
        serviceIntent.putExtra("message", message)
        context.startService(serviceIntent)

        // Notification
        showNotification(context, sender, message)

        Log.d("SMS_DEBUG", "SMS RECEIVED from $sender")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val intent = Intent("SMS_INBOX_UPDATED")
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)

            } finally {
                pendingResult.finish()
            }
        }
    }

    ////////////////////////////////////////////////////////
    // 🔔 SHOW NOTIFICATION WITH SOUND
    ////////////////////////////////////////////////////////

    private fun showNotification(context: Context, sender: String, message: String) {

        val channelId = "sms_channel"

        val soundUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Intent to open app
        val intent = Intent(context, com.sms.textmessages.messenger.MainActivity::class.java).apply {
            putExtra("open_chat_sender", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "SMS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(soundUri, null)
            }

            val manager =
                context.getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}