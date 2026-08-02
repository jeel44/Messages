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
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayManager
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.PreferenceManager

// Secondary call-end path when PHONE_STATE / during-call overlay never ran.
// Only fires when call-end is enabled and no overlay is already showing.
class CallLogCallEndObserver(
    private val appContext: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
        // Keep short so CallLog fallback feels like call-end, not a random late popup.
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
        if (CallEndOverlayManager.isShowing()) return

        pending?.let { debounceHandler.removeCallbacks(it) }
        val task = Runnable { handleLatestCall() }
        pending = task
        debounceHandler.postDelayed(task, DEBOUNCE_MS)
    }

    private fun handleLatestCall() {
        if (CallEndOverlayManager.isShowing()) return
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
                if (CallEndOverlayManager.wasCallHandled(key)) {
                    Log.d(TAG, "CallLogCallEndObserver: already handled $key")
                    return
                }
                CallEndOverlayManager.markCallHandled(key)
                // Reaching call-end via CallLog means the primary during-call /
                // PHONE_STATE path missed — strong OEM-kill signal.
                CallEndMetrics.recordMiss(appContext, "primary_missed_calllog_fallback")
                Log.d(TAG, "CallLogCallEndObserver: triggering call-end type=$callType number=$number")
                CallEndOverlayManager.show(
                    appContext,
                    callType,
                    number?.takeIf { it.isNotBlank() },
                    durationSec * 1000L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "CallLogCallEndObserver handle failed: ${e.message}", e)
            CallEndMetrics.recordMiss(appContext, "calllog_observer_error")
        }
    }
}
