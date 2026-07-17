package com.sms.textmessages.messenger.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaAttachment(
    val uri: Uri,
    val mimeType: String,
    val date: Long,
    val messageId: Long,
    // Not part of the original four-field spec, but MMS's msg_box column is
    // the only place sent-vs-received direction lives, and ChatScreen.kt's
    // merge step needs it to render the bubble on the correct side alongside
    // SMS text bubbles. Defaults to false (received) so any other caller that
    // constructs one without it still compiles.
    val isMe: Boolean = false
)

////////////////////////////////////////////////////////
// 🔵 MMS ATTACHMENT LOADER
////////////////////////////////////////////////////////

suspend fun loadMmsAttachments(context: Context, phoneNumber: String): List<MediaAttachment> {

    return withContext(Dispatchers.IO) {

        val attachments = mutableListOf<MediaAttachment>()

        // SMS and MMS share the same conversation thread_id, resolvable from
        // the recipient the same way the platform Messages app does. This
        // avoids needing to resolve per-message recipients from the addr
        // table (see the comment below) just to filter to "this thread".
        val threadId = try {
            Telephony.Threads.getOrCreateThreadId(context, setOf(phoneNumber))
        } catch (e: Exception) {
            return@withContext emptyList<MediaAttachment>()
        }

        val mmsUri = Uri.parse("content://mms")

        val mmsCursor = context.contentResolver.query(
            mmsUri,
            arrayOf("_id", "date", "msg_box"),
            "thread_id = ?",
            arrayOf(threadId.toString()),
            "date ASC"
        )

        mmsCursor?.use { cursor ->

            val idIndex = cursor.getColumnIndex("_id")
            val dateIndex = cursor.getColumnIndex("date")
            val msgBoxIndex = cursor.getColumnIndex("msg_box")

            while (cursor.moveToNext()) {

                val messageId = cursor.getLong(idIndex)

                // MMS stores `date` in seconds, unlike SMS's milliseconds -
                // the same seconds-vs-millis quirk SmsRepository already
                // guards against for SMS future-dates.
                var date = cursor.getLong(dateIndex)
                if (date < 1_000_000_000_000L) {
                    date *= 1000
                }

                // msg_box: 1 = inbox (received), 2 = sent - mirrors SMS's type column.
                val isMe = cursor.getInt(msgBoxIndex) == 2

                // 🔶 GENUINELY COMPLEX PART: a message's binary attachments
                // are NOT columns on the content://mms row itself - each part
                // (which can be a text part, an SMIL layout part, AND image/
                // video parts, all mixed together) lives in a per-message
                // sub-table at content://mms/{id}/part. A real MMS group
                // thread would additionally need content://mms/{id}/addr to
                // resolve which of several recipients a given message is
                // from/to - that per-message recipient resolution is NOT
                // implemented here since this loader only needs a single
                // thread's worth of attachments, not per-sender attribution.
                val partUri = Uri.parse("content://mms/$messageId/part")

                val partCursor = context.contentResolver.query(
                    partUri,
                    arrayOf("_id", "ct"),
                    null,
                    null,
                    null
                )

                partCursor?.use { parts ->

                    val partIdIndex = parts.getColumnIndex("_id")
                    val ctIndex = parts.getColumnIndex("ct")

                    while (parts.moveToNext()) {

                        val contentType = parts.getString(ctIndex) ?: continue

                        if (!contentType.startsWith("image/") && !contentType.startsWith("video/")) {
                            continue
                        }

                        val partId = parts.getLong(partIdIndex)

                        attachments.add(
                            MediaAttachment(
                                uri = Uri.parse("content://mms/part/$partId"),
                                mimeType = contentType,
                                date = date,
                                messageId = messageId,
                                isMe = isMe
                            )
                        )
                    }
                }
            }
        }

        attachments.sortedBy { it.date }
    }
}

////////////////////////////////////////////////////////
// 🔵 BITMAP LOADING (no image-loading library dependency exists in this
// project yet - see build.gradle.kts - so thumbnails/full images decode
// directly via BitmapFactory/MediaMetadataRetriever instead of adding one).
////////////////////////////////////////////////////////

suspend fun loadMmsBitmap(context: Context, uri: Uri, sampleSize: Int = 1): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
}

suspend fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.frameAtTime
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

suspend fun loadMediaThumbnail(context: Context, attachment: MediaAttachment, sampleSize: Int = 4): Bitmap? {
    return if (attachment.mimeType.startsWith("video/")) {
        loadVideoThumbnail(context, attachment.uri)
    } else {
        loadMmsBitmap(context, attachment.uri, sampleSize)
    }
}
