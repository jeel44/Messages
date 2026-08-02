package com.sms.textmessages.messenger.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.data.db.ThreadEntity
import com.sms.textmessages.messenger.ui.chat.ChatMessage
import com.sms.textmessages.messenger.ui.media.MediaAttachment
import com.sms.textmessages.messenger.utils.BlockedNumberSync
import com.sms.textmessages.messenger.utils.PreferenceManager
import kotlinx.coroutines.launch

////////////////////////////////////////////////////////
// DATA MODELS
////////////////////////////////////////////////////////

data class SmsMessage(
    val phone: String,
    val body: String,
    val date: Long,
    val isRead: Boolean,
    val type: Int
)

data class SmsThread(
    val phone: String,
    val lastMessage: String,
    val date: Long,
    val isRead: Boolean,
    val threadId: Long,
    val pinned: Boolean = false
)

////////////////////////////////////////////////////////
// SMS REPOSITORY
////////////////////////////////////////////////////////

object SmsRepository {

    private var memoryCache: List<SmsThread>? = null
    private val contactCache = HashMap<String, String>()

    // archived/blocked/pinned live in PreferenceManager (durable across the
    // full clear+reinsert that refreshThreads does on every sync), so any
    // ThreadEntity written to Room must be stamped from that source of truth
    // to keep the DAO's WHERE/ORDER BY clauses correct.
    private fun stampFlags(context: Context, phone: String): Triple<Boolean, Boolean, Boolean> {
        val last10 = phone.takeLast(10)
        val archived = PreferenceManager.getArchivedNumbers(context).contains(last10)
        val blocked = PreferenceManager.getBlockedNumbers(context).contains(last10)
        val pinned = PreferenceManager.getPinnedNumbers(context).contains(last10)
        return Triple(archived, blocked, pinned)
    }

    private fun ThreadEntity.toSmsThread() = SmsThread(
        phone = phone,
        lastMessage = lastMessage,
        date = date,
        isRead = isRead,
        threadId = threadId,
        pinned = pinned
    )

    ////////////////////////////////////////////////////////
    // LOAD CONTACTS
    ////////////////////////////////////////////////////////

    private fun loadContacts(context: Context) {

        if (contactCache.isNotEmpty()) return

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {

            val numberIndex =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            val nameIndex =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

            while (it.moveToNext()) {

                val number = it.getString(numberIndex)
                val name = it.getString(nameIndex)

                val cleanNumber = normalizeNumber(number)

                contactCache[cleanNumber] = name
            }
        }
    }

    ////////////////////////////////////////////////////////
    // LOAD THREADS (FAST INBOX)
    ////////////////////////////////////////////////////////

    // Preserves Room's already-correct isRead across a resync UNLESS this
    // resync is reporting a message Room hasn't seen before (freshDate newer
    // than what's stored) - that's the one legitimate "a new message arrived"
    // case, which should still flip the thread unread, same as
    // SmsReceiver's targeted per-thread upsert already does for a live SMS
    // (it hardcodes isRead=false on insert for exactly this reason). Any
    // other resync - triggered by unrelated MMS provider activity, or a
    // second resync for a thread that hasn't actually changed - preserves
    // Room's value instead of re-deriving it from a live provider query,
    // which is what let a concurrent markThreadAsRead()/archiveThread()-style
    // targeted write get clobbered by replaceAllThreads().
    //
    // A brand new thread (existing == null, Room has never seen it) has
    // nothing to preserve, so the fresh provider read is trusted outright -
    // same as the very first sync when Room is empty.
    private fun resolveIsRead(existing: ThreadEntity?, freshDate: Long, freshIsRead: Boolean): Boolean {
        if (existing == null) return freshIsRead
        if (freshDate > existing.date) return freshIsRead
        return existing.isRead
    }

    private fun loadThreads(context: Context, existingByThreadId: Map<Long, ThreadEntity>): List<SmsThread> {

        // Keyed by threadId (not a plain list) so the MMS pass below can merge
        // into it in place - an MMS-only thread has no row in content://sms at
        // all, and a thread with both types needs its snippet/date replaced
        // when the MMS side is more recent. content://sms and content://mms
        // are separate tables with unrelated id spaces (same fact
        // MmsRepository.loadMmsAttachments' comment already notes), so this
        // can't be expressed as a single query the way the SMS-only loop below
        // reads content://sms alone.
        val threadMap = LinkedHashMap<Long, SmsThread>()

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            ),
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )

        val seenThreads = HashSet<Long>()

        cursor?.use {

            val threadIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {

                val threadId = it.getLong(threadIndex)

                // 🔥 Only take FIRST message per thread (latest)
                if (seenThreads.contains(threadId)) continue

                seenThreads.add(threadId)

                val phone = normalizeNumber(it.getString(addressIndex) ?: "")
                val body = it.getString(bodyIndex) ?: ""
                var date = it.getLong(dateIndex)

// 1️⃣ Fix seconds → milliseconds
                if (date < 1_000_000_000_000L) {
                    date *= 1000
                }

// 2️⃣ Fix future timestamps (MAIN ISSUE)
                val now = System.currentTimeMillis()

                if (date > now) {
                    Log.d("FIX_DATE", "Future date detected: $date → fixing")

                    date = now - 1000 // keep slightly behind current time
                }
                val isRead = resolveIsRead(existingByThreadId[threadId], date, it.getInt(readIndex) == 1)

                Log.d("TRACE_REPO", "THREAD -> id=$threadId date=$date body=$body")

                threadMap[threadId] = SmsThread(
                    phone = phone,
                    lastMessage = body,
                    date = date,
                    isRead = isRead,
                    threadId = threadId
                )
            }
        }

        mergeMmsThreads(context, threadMap, existingByThreadId)

        return threadMap.values.sortedByDescending { it.date }
    }

    ////////////////////////////////////////////////////////
    // MERGE MMS INTO THE THREAD LIST
    ////////////////////////////////////////////////////////

    // MMS "type" value for the addr-table row identifying the sender, per the
    // OMA-WAP-MMS-ENC From header field code (see receiver/MmsPdu.kt's
    // MmsFieldCode.FROM, which MmsProvider.kt writes into this same column
    // when it inserts a retrieved MMS - kept as a local literal rather than a
    // cross-package reference since it's the one value this file needs).
    private const val MMS_ADDR_TYPE_FROM = 0x89

    private fun mergeMmsThreads(
        context: Context,
        threadMap: MutableMap<Long, SmsThread>,
        existingByThreadId: Map<Long, ThreadEntity>
    ) {

        val cursor = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "thread_id", "date", "read"),
            null,
            null,
            "date DESC"
        )

        val seenThreads = HashSet<Long>()

        cursor?.use {

            val idIndex = it.getColumnIndex("_id")
            val threadIndex = it.getColumnIndex("thread_id")
            val dateIndex = it.getColumnIndex("date")
            val readIndex = it.getColumnIndex("read")

            while (it.moveToNext()) {

                val threadId = it.getLong(threadIndex)

                // Only take the first (latest) MMS per thread - same
                // "first row wins" pattern as the SMS loop above.
                if (seenThreads.contains(threadId)) continue
                seenThreads.add(threadId)

                val msgId = it.getLong(idIndex)

                // MMS stores date in seconds, unlike SMS's milliseconds - the
                // same quirk MmsRepository.loadMmsAttachments already guards.
                var date = it.getLong(dateIndex)
                if (date < 1_000_000_000_000L) {
                    date *= 1000
                }

                val existing = threadMap[threadId]
                if (existing != null && existing.date >= date) {
                    // This thread's SMS side is already newer - nothing to update.
                    continue
                }

                // Phone is already known if this thread had an SMS row; only
                // an MMS-only thread needs it resolved from the addr table.
                val phone = existing?.phone
                    ?: resolveMmsFromAddress(context, msgId)?.let(::normalizeNumber)

                if (phone == null) {
                    Log.w("TRACE_REPO", "MMS thread $threadId has no resolvable from-address, skipping")
                    continue
                }

                // Compared against Room's PRIOR persisted state (existingByThreadId,
                // snapshotted before this resync's provider queries ran) -
                // deliberately NOT threadMap[threadId], which is this same
                // resync's in-progress SMS-pass result and doesn't tell us
                // whether anything actually changed since Room was last written.
                val isRead = resolveIsRead(existingByThreadId[threadId], date, it.getInt(readIndex) == 1)

                threadMap[threadId] = SmsThread(
                    phone = phone,
                    lastMessage = mmsSnippet(context, msgId),
                    date = date,
                    isRead = isRead,
                    threadId = threadId
                )
            }
        }
    }

    private fun resolveMmsFromAddress(context: Context, msgId: Long): String? {
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms/$msgId/addr"),
            arrayOf("address", "type"),
            "type = ?",
            arrayOf(MMS_ADDR_TYPE_FROM.toString()),
            null
        )
        return cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    // Best-effort inbox preview text for an MMS: its text/plain part if it has
    // one, otherwise a content-type-based label - mirrors how most SMS apps
    // preview a picture/video-only message, since MMS often has no text part
    // at all (loadMmsAttachments, which renders the actual attachments, only
    // looks at image/video parts and has no equivalent text extraction).
    private fun mmsSnippet(context: Context, msgId: Long): String {

        val cursor = context.contentResolver.query(
            Uri.parse("content://mms/$msgId/part"),
            arrayOf("ct", "text"),
            null,
            null,
            null
        )

        var textSnippet: String? = null
        var hasImage = false
        var hasVideo = false

        cursor?.use {
            val ctIndex = it.getColumnIndex("ct")
            val textIndex = it.getColumnIndex("text")

            while (it.moveToNext()) {
                val contentType = it.getString(ctIndex) ?: continue
                when {
                    contentType == "text/plain" -> {
                        val text = if (textIndex >= 0) it.getString(textIndex) else null
                        if (!text.isNullOrBlank()) textSnippet = text
                    }
                    contentType.startsWith("image/") -> hasImage = true
                    contentType.startsWith("video/") -> hasVideo = true
                }
            }
        }

        return textSnippet ?: when {
            hasImage -> "📷 Photo"
            hasVideo -> "🎥 Video"
            else -> "MMS message"
        }
    }

    private fun getLatestMessage(context: Context, threadId: Long): Pair<String, String> {

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY
            ),
            "thread_id = ?",
            arrayOf(threadId.toString()),
            "date DESC LIMIT 1"
        )

        cursor?.use {

            if (it.moveToFirst()) {

                val address = it.getString(
                    it.getColumnIndex(Telephony.Sms.ADDRESS)
                ) ?: ""

                val body = it.getString(
                    it.getColumnIndex(Telephony.Sms.BODY)
                ) ?: ""

                return Pair(normalizeNumber(address), body)
            }
        }

        return Pair("", "")
    }

    ////////////////////////////////////////////////////////
    // ROOM CACHED INBOX
    ////////////////////////////////////////////////////////

    suspend fun getInbox(context: Context): List<SmsThread> {

        val db = AppDatabase.getDatabase(context)
        val dao = db.threadDao()

        val existingByThreadId = dao.getAllThreadsOnce().associateBy { it.threadId }

        val threads = loadThreads(context, existingByThreadId)
            .sortedByDescending { it.date }

        dao.insertThreads(
            threads.map {
                val (archived, blocked, pinned) = stampFlags(context, it.phone)
                ThreadEntity(
                    phone = it.phone,
                    lastMessage = it.lastMessage,
                    date = it.date,
                    isRead = it.isRead,
                    threadId = it.threadId,
                    archived = archived,
                    blocked = blocked,
                    pinned = pinned
                )
            }
        )

        return threads
    }

    ////////////////////////////////////////////////////////
    // REFRESH THREADS
    ////////////////////////////////////////////////////////

    suspend fun refreshThreads(context: Context): List<SmsThread> {
        Log.d("TRACE_REPO", "refreshThreads() called")

        val db = AppDatabase.getDatabase(context)
        val dao = db.threadDao()

        // Snapshotted before the (slower) provider queries in loadThreads()
        // run, so mergeMmsThreads()/the SMS loop can tell "genuinely new
        // message" apart from "unrelated resync" - see resolveIsRead().
        val existingByThreadId = dao.getAllThreadsOnce().associateBy { it.threadId }

        val threads = loadThreads(context, existingByThreadId)
            .sortedByDescending { it.date }

        dao.replaceAllThreads(
            threads.map {
                val (archived, blocked, pinned) = stampFlags(context, it.phone)
                ThreadEntity(
                    phone = it.phone,
                    lastMessage = it.lastMessage,
                    date = it.date,
                    isRead = it.isRead,
                    threadId = it.threadId,
                    archived = archived,
                    blocked = blocked,
                    pinned = pinned
                )
            }
        )

        memoryCache = threads
        threads.take(5).forEach {
            Log.d("TRACE_REPO", "AFTER SORT -> id=${it.threadId} date=${it.date}")
        }
        return threads
    }

    ////////////////////////////////////////////////////////
    // LOAD CHAT MESSAGES (THREAD_ID FAST)
    ////////////////////////////////////////////////////////

    fun loadMessages(context: Context, threadId: Long): List<SmsMessage> {

        loadContacts(context)

        val list = mutableListOf<SmsMessage>()

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE
            ),
            "thread_id = ?",
            arrayOf(threadId.toString()),
            Telephony.Sms.DATE + " ASC"
        )

        cursor?.use {

            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {

                val address = it.getString(addressIndex) ?: ""
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                Log.d("TRACE_REPO", "THREAD raw -> threadId=$threadId date=$date")
                val isRead = it.getInt(readIndex) == 1
                val type = it.getInt(typeIndex)

                list.add(
                    SmsMessage(
                        phone = address,
                        body = body,
                        date = date,
                        isRead = isRead,
                        type = type
                    )
                )
            }
        }

        list.take(5).forEach {
            Log.d("TRACE_REPO", "CHAT MSG -> date=${it.date} body=${it.body}")
        }

        return list
    }

    ////////////////////////////////////////////////////////
    // LOAD UNIFIED THREAD MESSAGES (SMS + MMS, ONE SORTED LIST)
    ////////////////////////////////////////////////////////

    // content://sms and content://mms are separate provider tables with
    // unrelated id spaces (see mergeMmsThreads() above, which hits the same
    // fact for the inbox list) - this is the single place a whole
    // conversation timeline gets assembled from both, sorted, and handed
    // back as one list. Previously each half was loaded independently
    // (SmsRepository.loadMessages for text, MmsRepository.loadMmsAttachments
    // for attachments) and merged client-side in ChatScreen right before
    // rendering; whenever the two halves loaded at different speeds, the
    // slower one would pop in and reflow the already-visible timeline (the
    // "flash" bug, twice now in two different forms). Merging here instead
    // means every caller - the one-shot initial load AND every ContentObserver
    // refresh - gets one already-correct list, so there's no client-side
    // merge left anywhere to race.
    fun loadThreadMessages(context: Context, threadId: Long): List<ChatMessage> {

        val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val result = mutableListOf<ChatMessage>()

        // --- SMS half ---
        val smsCursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            "thread_id = ?",
            arrayOf(threadId.toString()),
            Telephony.Sms.DATE + " ASC"
        )

        smsCursor?.use {

            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {

                val date = it.getLong(dateIndex)

                result.add(
                    ChatMessage(
                        text = it.getString(bodyIndex) ?: "",
                        time = timeFormat.format(java.util.Date(date)),
                        date = date,
                        isMe = it.getInt(typeIndex) == 2,
                        id = it.getLong(idIndex)
                    )
                )
            }
        }

        // --- MMS half - image/video attachments only, one ChatMessage stub
        // per attachment part (an MMS row can carry more than one), matching
        // ChatBubble which renders a single MediaAttachment per bubble. Same
        // part-table walk MmsRepository.loadMmsAttachments does, but scoped
        // directly by the threadId already known here instead of resolving
        // it from a phone number - loadMmsAttachments still exists as-is for
        // its other callers (SharedMediaScreen, MediaViewerScreen). ---
        val mmsCursor = context.contentResolver.query(
            Uri.parse("content://mms"),
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
                // same quirk loadMmsAttachments already guards.
                var date = cursor.getLong(dateIndex)
                if (date < 1_000_000_000_000L) {
                    date *= 1000
                }

                val isMe = cursor.getInt(msgBoxIndex) == 2

                val partCursor = context.contentResolver.query(
                    Uri.parse("content://mms/$messageId/part"),
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

                        result.add(
                            ChatMessage(
                                text = "",
                                time = timeFormat.format(java.util.Date(date)),
                                date = date,
                                isMe = isMe,
                                // SMS and MMS _id columns are separate id
                                // spaces on the provider - offset MMS-derived
                                // ids so they can never collide with a real
                                // SMS _id when used as the reaction-storage key.
                                id = -1_000_000_000L - messageId,
                                attachment = MediaAttachment(
                                    uri = Uri.parse("content://mms/part/$partId"),
                                    mimeType = contentType,
                                    date = date,
                                    messageId = messageId,
                                    isMe = isMe
                                )
                            )
                        )
                    }
                }
            }
        }

        return result.sortedBy { it.date }
    }

    ////////////////////////////////////////////////////////
    // NORMALIZE PHONE NUMBER
    ////////////////////////////////////////////////////////

    private fun normalizeNumber(number: String?): String {

        if (number == null) return ""

        return number
            .replace("\\s".toRegex(), "")
            .replace("-", "")
    }

    ////////////////////////////////////////////////////////
    // FIND EXISTING THREAD FOR A PHONE NUMBER
    ////////////////////////////////////////////////////////

    fun findExistingThreadId(context: Context, phone: String): Long? {

        // Matched by trailing digits, not an exact string - a contact's number
        // (e.g. "+1 415-555-0100" from ContactsContract) and the address an SMS
        // thread was actually stored under (e.g. "4155550100") frequently differ
        // in country code/formatting. Same last-10-digit convention already used
        // for archived/blocked/pinned matching elsewhere in this app.
        val target = normalizeNumber(phone).takeLast(10)
        if (target.isEmpty()) return null

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS
            ),
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )

        cursor?.use {

            val threadIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)

            while (it.moveToNext()) {
                if (normalizeNumber(it.getString(addressIndex)).takeLast(10) == target) {
                    return it.getLong(threadIndex)
                }
            }
        }

        return null
    }

    suspend fun markThreadAsRead(context: Context, threadId: Long) {

        // READ *and* SEEN, via the combined mms-sms conversations URI (not just
        // Telephony.Sms.CONTENT_URI) so this also covers MMS messages in the
        // thread, not only SMS - matches QKSMS's markRead system-provider write.
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }

        context.contentResolver.update(
            ContentUris.withAppendedId(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, threadId),
            values,
            "read = 0",
            null
        )

        val db = AppDatabase.getDatabase(context)
        db.threadDao().markThreadAsRead(threadId)

        memoryCache = memoryCache?.map {
            if (it.threadId == threadId) it.copy(isRead = true) else it
        }
    }

    ////////////////////////////////////////////////////////
    // ARCHIVE / BLOCK / PIN - write PreferenceManager (durable across a full
    // resync) and the Room row (so the reactive Flow re-emits immediately)
    // together, so there's a single call site for each action.
    ////////////////////////////////////////////////////////

    suspend fun archiveThread(context: Context, phone: String) {
        PreferenceManager.archiveNumber(context, phone)
        AppDatabase.getDatabase(context).threadDao().setArchivedForNumber(phone.takeLast(10), true)
    }

    suspend fun unarchiveThread(context: Context, phone: String) {
        PreferenceManager.unarchiveNumber(context, phone)
        AppDatabase.getDatabase(context).threadDao().setArchivedForNumber(phone.takeLast(10), false)
    }

    suspend fun blockThread(context: Context, phone: String) {
        PreferenceManager.blockNumber(context, phone)
        AppDatabase.getDatabase(context).threadDao().setBlockedForNumber(phone.takeLast(10), true)
        // Platform block list so the system dialer and CallScreeningService
        // stay aligned when this app is allowed to write BlockedNumberContract.
        BlockedNumberSync.addToSystemBlocked(context, phone)
    }

    suspend fun unblockThread(context: Context, phone: String) {
        PreferenceManager.unblockNumber(context, phone)
        AppDatabase.getDatabase(context).threadDao().setBlockedForNumber(phone.takeLast(10), false)
        BlockedNumberSync.removeFromSystemBlocked(context, phone)
    }

    suspend fun setPinned(context: Context, phone: String, pinned: Boolean) {
        if (pinned) PreferenceManager.pinNumber(context, phone) else PreferenceManager.unpinNumber(context, phone)
        AppDatabase.getDatabase(context).threadDao().setPinnedForNumber(phone.takeLast(10), pinned)
    }

}

