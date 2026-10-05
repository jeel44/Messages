package com.sms.textmessages.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.overlay.CategoryOverlayService
import com.sms.textmessages.messenger.utils.PreferenceManager
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) return

        val senderRaw = messages[0].displayOriginatingAddress ?: "Unknown"
        val sender = senderRaw.replace("\\s".toRegex(), "")

        val message = messages.joinToString("") { it.displayMessageBody ?: "" }

        // Overlay popup only - no system notification is posted for incoming SMS.
        showOverlay(context, sender, message)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                // As the default SMS app we must write the message to content://sms ourselves —
                // Android 4.4+ does not do this for the default handler. Do it first so that
                // recordMessage can read the real thread_id off the inserted row, and so
                // that the chat screen's loadMessages query can see this message immediately.
                val date = System.currentTimeMillis()
                val smsValues = android.content.ContentValues().apply {
                    put("address", sender)
                    put("body", message)
                    put("date", date)
                    put("read", 0)
                    put("type", 1) // 1 = inbox / received
                }
                val insertedUri = context.contentResolver.insert(
                    android.net.Uri.parse("content://sms/inbox"),
                    smsValues
                )

                // Shared with sendSms() - bumps the thread's Room row (unread)
                // with archived/blocked/pinned preserved.
                SmsRepository.recordMessage(
                    context,
                    phone = sender,
                    body = message,
                    date = date,
                    isRead = false,
                    insertedUri = insertedUri
                )

                val updateIntent = Intent("SMS_INBOX_UPDATED")
                updateIntent.setPackage(context.packageName)
                context.sendBroadcast(updateIntent)

                val chatIntent = Intent("NEW_SMS_RECEIVED")
                chatIntent.setPackage(context.packageName)
                chatIntent.putExtra("sender", sender)
                chatIntent.putExtra("message", message)
                context.sendBroadcast(chatIntent)

            } catch (e: Exception) {
                Log.e("SMS_RECEIVER", "Failed to process incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
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