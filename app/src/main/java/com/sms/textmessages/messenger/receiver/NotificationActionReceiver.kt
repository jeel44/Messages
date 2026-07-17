package com.sms.textmessages.messenger.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.sms.textmessages.messenger.ui.chat.sendSms
import com.sms.textmessages.messenger.utils.PreferenceManager

// Handles the lightweight, non-copy notification actions (reply, mark as
// read, mute sender, dismiss) from one shared receiver rather than a
// separate class per action, since each one is a few lines of work keyed
// off a distinct Intent action string.
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_READ = "com.sms.textmessages.messenger.ACTION_MARK_READ"
        const val ACTION_MUTE_SENDER = "com.sms.textmessages.messenger.ACTION_MUTE_SENDER"
        const val ACTION_DISMISS = "com.sms.textmessages.messenger.ACTION_DISMISS"
        const val ACTION_REPLY = "com.sms.textmessages.messenger.ACTION_REPLY"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_REPLY_TEXT = "key_reply_text"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val sender = intent.getStringExtra(EXTRA_SENDER)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {

            ACTION_MARK_READ -> {

                if (sender != null) {
                    val values = ContentValues().apply { put("read", 1) }
                    context.contentResolver.update(
                        Uri.parse("content://sms/inbox"),
                        values,
                        "address = ? AND read = 0",
                        arrayOf(sender)
                    )
                    context.sendBroadcast(
                        Intent("SMS_INBOX_UPDATED").setPackage(context.packageName)
                    )
                }

                cancelNotification(context, notificationId)
            }

            ACTION_MUTE_SENDER -> {

                if (sender != null) {
                    PreferenceManager.setNotificationsMuted(context, sender, true)
                    Toast.makeText(context, "Notifications muted", Toast.LENGTH_SHORT).show()
                }

                cancelNotification(context, notificationId)
            }

            ACTION_DISMISS -> {
                cancelNotification(context, notificationId)
            }

            ACTION_REPLY -> {

                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)
                    ?.toString()

                if (!replyText.isNullOrBlank() && sender != null) {
                    sendSms(context, sender, replyText)
                }

                cancelNotification(context, notificationId)
            }
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
