package com.sms.textmessages.messenger.receiver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.sms.textmessages.messenger.ui.home.extractCopyableCode

class CopyOtpReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY_OTP = "com.sms.textmessages.messenger.ACTION_COPY_OTP"
        const val EXTRA_MESSAGE_BODY = "extra_message_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val body = intent.getStringExtra(EXTRA_MESSAGE_BODY)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val code = body?.let { extractCopyableCode(it) }

        if (code != null) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP", code))
            Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
        }

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
