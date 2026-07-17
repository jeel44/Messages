package com.sms.textmessages.messenger.receiver

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

private const val TAG = "MMS_PROVIDER"

/**
 * Writes a parsed MMS (M-Retrieve.conf) into Android's system content://mms
 * provider (and its part/addr sub-tables), matching the structure
 * MmsRepository.loadMmsAttachments() already reads live from that provider.
 *
 * This app has no per-message Room entity by design - messages are read live
 * from content://sms / content://mms by the UI - so there is deliberately no
 * Room write here, matching that architecture.
 */
internal object MmsProvider {

    // Reuses Telephony.Threads.getOrCreateThreadId - the same system API
    // MmsRepository.loadMmsAttachments() already calls to resolve a thread_id
    // before querying content://mms. Using the same resolution mechanism here
    // is required for the inserted row to actually be found by that query;
    // SmsRepository's own thread lookup (a manual last-10-digit scan over
    // content://sms with a `sender.hashCode()` fallback when no match exists)
    // was considered instead, but its fallback value would not agree with
    // Telephony.Threads.getOrCreateThreadId's canonical thread_id, which would
    // silently orphan the inserted MMS from the thread the UI actually queries.
    fun resolveThreadId(context: Context, address: String?): Long {
        val number = address?.takeIf { it.isNotBlank() } ?: "Unknown"
        return try {
            Telephony.Threads.getOrCreateThreadId(context, setOf(number))
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateThreadId failed for address=$number", e)
            number.hashCode().toLong()
        }
    }

    /** Returns the inserted message's row id, or null if the insert failed. */
    fun insertRetrievedMessage(context: Context, msg: MmsRetrieveConf): Long? {
        val resolver = context.contentResolver
        val threadId = resolveThreadId(context, msg.from ?: msg.to.firstOrNull())

        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, msg.dateSeconds ?: (System.currentTimeMillis() / 1000))
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.MESSAGE_TYPE, MmsMessageType.RETRIEVE_CONF)
            put(Telephony.Mms.MMS_VERSION, msg.mmsVersion ?: 0x12)
            msg.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            msg.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            msg.subject?.let { put(Telephony.Mms.SUBJECT, it) }
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            val hasMedia = msg.parts.any {
                it.contentType.startsWith("image/") || it.contentType.startsWith("video/")
            }
            put(Telephony.Mms.TEXT_ONLY, if (hasMedia) 0 else 1)
        }

        val msgUri = try {
            resolver.insert(Telephony.Mms.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert MMS message row (threadId=$threadId, txId=${msg.transactionId})", e)
            null
        }

        if (msgUri == null) {
            Log.e(TAG, "MMS message insert returned null uri (threadId=$threadId, txId=${msg.transactionId})")
            return null
        }

        val msgId = ContentUris.parseId(msgUri)
        Log.d(TAG, "Inserted MMS message id=$msgId threadId=$threadId parts=${msg.parts.size} from=${msg.from}")

        insertParts(context, msgId, msg.parts)
        insertAddresses(context, msgId, msg.from, msg.to, msg.cc)

        return msgId
    }

    private fun insertParts(context: Context, msgId: Long, parts: List<MmsPart>) {
        val partUri = Uri.parse("content://mms/$msgId/part")
        parts.forEachIndexed { index, part ->
            try {
                val values = ContentValues().apply {
                    put(Telephony.Mms.Part.MSG_ID, msgId)
                    put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                    put(Telephony.Mms.Part.NAME, part.name ?: "part$index")
                }
                val insertedUri = context.contentResolver.insert(partUri, values)
                if (insertedUri == null) {
                    Log.e(TAG, "Part insert returned null uri msgId=$msgId index=$index type=${part.contentType}")
                    return@forEachIndexed
                }
                context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                    out.write(part.data)
                } ?: Log.e(TAG, "Could not open output stream for part uri=$insertedUri (msgId=$msgId index=$index)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist MMS part index=$index type=${part.contentType} msgId=$msgId", e)
            }
        }
    }

    private fun insertAddresses(context: Context, msgId: Long, from: String?, to: List<String>, cc: List<String>) {
        val addrUri = Uri.parse("content://mms/$msgId/addr")

        fun insertAddr(address: String, type: Int) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Mms.Addr.MSG_ID, msgId)
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, type)
                }
                if (context.contentResolver.insert(addrUri, values) == null) {
                    Log.e(TAG, "Addr insert returned null uri msgId=$msgId address=$address type=$type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert addr row address=$address type=$type msgId=$msgId", e)
            }
        }

        from?.let { insertAddr(it, MmsAddrType.FROM) }
        to.forEach { insertAddr(it, MmsAddrType.TO) }
        cc.forEach { insertAddr(it, MmsAddrType.CC) }
    }
}
