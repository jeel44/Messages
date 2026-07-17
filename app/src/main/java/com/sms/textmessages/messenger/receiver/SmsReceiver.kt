package com.sms.textmessages.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sms.textmessages.messenger.ui.overlay.CategoryOverlayService
import com.sms.textmessages.messenger.utils.PreferenceManager
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("SMS_DEBUG", "Step1: onReceive fired action=${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) return

        val senderRaw = messages[0].displayOriginatingAddress ?: "Unknown"
        val sender = senderRaw.replace("\\s".toRegex(), "")

        val message = messages.joinToString("") { it.displayMessageBody ?: "" }

        Log.d("TRACE_SMS", "SMS received from=$sender msg=$message time=${System.currentTimeMillis()}")

        Log.d("SMS_RECEIVER", "Sender: $sender Message: $message")

        // Overlay popup only - no system notification is posted for incoming SMS.
        showOverlay(context, sender, message)

        Log.d("SMS_DEBUG", "SMS RECEIVED from $sender")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val db = com.sms.textmessages.messenger.data.db.AppDatabase.getDatabase(context)
                val dao = db.threadDao()

                // As the default SMS app we must write the message to content://sms ourselves —
                // Android 4.4+ does not do this for the default handler. Do it first so that
                // lookUpThreadId (which queries content://sms) finds the real thread_id, and so
                // that the chat screen's loadMessages query can see this message immediately.
                val smsValues = android.content.ContentValues().apply {
                    put("address", sender)
                    put("body", message)
                    put("date", System.currentTimeMillis())
                    put("read", 0)
                    put("type", 1) // 1 = inbox / received
                }
                context.contentResolver.insert(
                    android.net.Uri.parse("content://sms/inbox"),
                    smsValues
                )

                val realThreadId = lookUpThreadId(context, sender)

                // insertThreads uses REPLACE, so archived/blocked/pinned must be
                // re-stamped from PreferenceManager here or this new message would
                // silently un-archive/un-pin the thread it belongs to.
                val last10 = sender.takeLast(10)
                val thread = com.sms.textmessages.messenger.data.db.ThreadEntity(
                    phone = sender,
                    lastMessage = message,
                    date = System.currentTimeMillis(),
                    isRead = false,
                    threadId = realThreadId,
                    archived = PreferenceManager.getArchivedNumbers(context).contains(last10),
                    blocked = PreferenceManager.getBlockedNumbers(context).contains(last10),
                    pinned = PreferenceManager.getPinnedNumbers(context).contains(last10)
                )
                dao.insertThreads(listOf(thread))

                Log.d("SMS_DEBUG", "Step2: INSERTED sender=$sender threadId=$realThreadId")

                val updateIntent = Intent("SMS_INBOX_UPDATED")
                updateIntent.setPackage(context.packageName)
                context.sendBroadcast(updateIntent)

                val chatIntent = Intent("NEW_SMS_RECEIVED")
                chatIntent.setPackage(context.packageName)
                chatIntent.putExtra("sender", sender)
                chatIntent.putExtra("message", message)
                Log.d("SMS_DEBUG", "Step3: sending NEW_SMS_RECEIVED broadcast sender=$sender message=$message pkg=${context.packageName}")
                context.sendBroadcast(chatIntent)

            } catch (e: Exception) {
                Log.e("SMS_RECEIVER", "Failed to process incoming SMS from $sender", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun lookUpThreadId(context: Context, sender: String): Long {
        val cursor = context.contentResolver.query(
            android.provider.Telephony.Sms.CONTENT_URI,
            arrayOf(android.provider.Telephony.Sms.THREAD_ID),
            "${android.provider.Telephony.Sms.ADDRESS} = ?",
            arrayOf(sender),
            "${android.provider.Telephony.Sms.DATE} DESC"
        )
        return cursor?.use { if (it.moveToFirst()) it.getLong(0) else sender.hashCode().toLong() }
            ?: sender.hashCode().toLong()
    }

    ////////////////////////////////////////////////////////
    // 🔔 SHOW OVERLAY - CategoryOverlayCard is the only visible alert for a
    // classified SMS. No system notification is posted for incoming SMS.
    ////////////////////////////////////////////////////////

    private fun showOverlay(context: Context, sender: String, message: String) {

        if (PreferenceManager.isNotificationsMuted(context, sender)) {
            return
        }

        val category = classifyNotification(sender, message)
        val notificationId = sender.hashCode()

        CategoryOverlayService.start(context, category, sender, message, notificationId)
    }
}