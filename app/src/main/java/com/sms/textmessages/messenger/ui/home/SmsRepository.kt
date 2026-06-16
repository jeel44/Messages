package com.sms.textmessages.messenger.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.data.db.ThreadEntity
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
    val threadId: Long
)

////////////////////////////////////////////////////////
// SMS REPOSITORY
////////////////////////////////////////////////////////

object SmsRepository {

    private var memoryCache: List<SmsThread>? = null
    private val contactCache = HashMap<String, String>()

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

    private fun loadThreads(context: Context): List<SmsThread> {

        val list = mutableListOf<SmsThread>()

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
                val isRead = it.getInt(readIndex) == 1

                Log.d("TRACE_REPO", "THREAD -> id=$threadId date=$date body=$body")

                list.add(
                    SmsThread(
                        phone = phone,
                        lastMessage = body,
                        date = date,
                        isRead = isRead,
                        threadId = threadId
                    )
                )
            }
        }

        return list
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

        val threads = loadThreads(context)
            .sortedByDescending { it.date }

        val db = AppDatabase.getDatabase(context)
        val dao = db.threadDao()



        dao.insertThreads(
            threads.map {
                ThreadEntity(
                    phone = it.phone,
                    lastMessage = it.lastMessage,
                    date = it.date,
                    isRead = it.isRead,
                    threadId = it.threadId
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
        Log.d("SMS_DEBUG", "Loading SMS from database")

        val threads = loadThreads(context)
            .sortedByDescending { it.date }

        Log.d("SMS_DEBUG", "Total threads loaded: ${threads.size}")

        val db = AppDatabase.getDatabase(context)
        val dao = db.threadDao()

        // Clear old cached threads first
        dao.clearThreads()

        dao.insertThreads(
            threads.map {
                ThreadEntity(
                    phone = it.phone,
                    lastMessage = it.lastMessage,
                    date = it.date,
                    isRead = it.isRead,
                    threadId = it.threadId
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
    // NORMALIZE PHONE NUMBER
    ////////////////////////////////////////////////////////

    private fun normalizeNumber(number: String?): String {

        if (number == null) return ""

        return number
            .replace("\\s".toRegex(), "")
            .replace("-", "")
    }

    fun markThreadAsRead(context: Context, threadId: Long) {

        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }

        // 1️⃣ Update Android SMS database
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "thread_id = ? AND read = 0",
            arrayOf(threadId.toString())
        )

        // 2️⃣ Update memory cache
        val updatedThreads = memoryCache?.map {
            if (it.threadId == threadId) {
                it.copy(isRead = true)
            } else {
                it
            }
        }

        memoryCache = updatedThreads

        // 3️⃣ Update Room database safely
        updatedThreads?.let { threads ->

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {

                val db = AppDatabase.getDatabase(context)
                val dao = db.threadDao()

                dao.insertThreads(
                    threads.map {
                        ThreadEntity(
                            phone = it.phone,
                            lastMessage = it.lastMessage,
                            date = it.date,
                            isRead = it.isRead,
                            threadId = it.threadId
                        )
                    }
                )
            }
        }
    }

}

