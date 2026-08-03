package com.sms.textmessages.messenger.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.calling.CallEndEvent
import com.sms.textmessages.messenger.calling.CallEndGatekeeper
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.CallSessionStore
import com.sms.textmessages.messenger.utils.PreferenceManager

// Secondary call-end path when PHONE_STATE / POST_CALL never ran.
class CallLogCallEndObserver(
    private val appContext: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
        private const val DEBOUNCE_MS = 500L
        private const val MAX_AGE_MS = 20_000L

        @Volatile
        private var registered = false

        fun registerIfNeeded(context: Context) {
            if (registered) return
            val app = context.applicationContext
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALL_LOG) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "CallLogCallEndObserver: READ_CALL_LOG missing - skip register")
                return
            }
            try {
                app.contentResolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    CallLogCallEndObserver(app)
                )
                registered = true
                Log.d(TAG, "CallLogCallEndObserver: registered")
            } catch (e: Exception) {
                Log.w(TAG, "CallLogCallEndObserver register failed: ${e.message}")
            }
        }
    }

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (!PreferenceManager.isCallEndEnabled(appContext)) return

        pending?.let { debounceHandler.removeCallbacks(it) }
        val task = Runnable { handleLatestCall() }
        pending = task
        debounceHandler.postDelayed(task, DEBOUNCE_MS)
    }

    private fun handleLatestCall() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        try {
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val age = System.currentTimeMillis() - date
                if (age !in 0..MAX_AGE_MS) {
                    Log.d(TAG, "CallLogCallEndObserver: row too old ageMs=$age")
                    return
                }
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val durationSec = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val callType = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE ->
                        if (durationSec > 0) CallEndType.INCOMING else CallEndType.MISSED
                    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallEndType.MISSED
                    CallLog.Calls.OUTGOING_TYPE -> CallEndType.OUTGOING
                    else -> return
                }
                val key = "${number.orEmpty()}|$typeInt|$date"
                if (CallEndGatekeeper.wasCallHandled(key)) {
                    Log.d(TAG, "CallLogCallEndObserver: already handled $key")
                    return
                }
                CallEndGatekeeper.markCallHandled(key)
                val session = CallSessionStore.load(appContext)
                Log.d(
                    TAG,
                    "CallLogCallEndObserver → Gatekeeper type=$callType number=$number " +
                        "durationSec=$durationSec ageMs=$age wasBlocked=${session?.wasBlocked}"
                )
                CallEndGatekeeper.onCallEnded(
                    appContext,
                    CallEndEvent(
                        number = number?.takeIf { it.isNotBlank() },
                        displayName = "Unknown",
                        type = callType,
                        durationMs = durationSec * 1000L,
                        timestampMs = date,
                        wasBlocked = session?.wasBlocked == true,
                        source = CallEndEvent.Source.CALL_LOG
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "CallLogCallEndObserver handle failed: ${e.message}", e)
            CallEndMetrics.recordMiss(appContext, "calllog_observer_error")
        }
    }
}
