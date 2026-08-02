package com.sms.textmessages.messenger.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayManager
import com.sms.textmessages.messenger.ui.overlay.DuringCallPhase
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.CallSessionStore
import com.sms.textmessages.messenger.utils.PreferenceManager

enum class CallEndType { INCOMING, OUTGOING, MISSED }

class CallStateListener : BroadcastReceiver() {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
        private const val CALL_LOG_MATCH_WINDOW_MS = 30_000L
        // When the process was just born for a late OFFHOOK near hangup, delay
        // the during-call bubble briefly so a following IDLE can cancel it and
        // go straight to call-end (avoids "popup then instantly call-end").
        private const val FRESH_PROCESS_MS = 2_500L
        private const val COLD_DURING_CALL_DEBOUNCE_MS = 350L

        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var sawRinging = false
        private var sawOffhook = false
        private var offhookStartMs = 0L
        private var capturedNumber: String? = null
        private var sessionLoaded = false

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingDuringCall: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val appContext = context.applicationContext
        ensureSessionLoaded(appContext)

        val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val state = when (stateExtra) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> {
                Log.w(TAG, "onReceive: unrecognized EXTRA_STATE=$stateExtra - ignoring")
                return
            }
        }

        @Suppress("DEPRECATION")
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "onReceive: EXTRA_STATE=$stateExtra rawIncomingNumber=${describeNumber(phoneNumber)}")
        handleState(appContext, state, phoneNumber)
    }

    private fun isFreshProcess(): Boolean =
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < FRESH_PROCESS_MS

    private fun cancelPendingDuringCall() {
        pendingDuringCall?.let { mainHandler.removeCallbacks(it) }
        pendingDuringCall = null
    }

    private fun scheduleDuringCall(
        context: Context,
        phoneNumber: String?,
        phase: DuringCallPhase
    ) {
        cancelPendingDuringCall()
        val delay = if (isFreshProcess()) COLD_DURING_CALL_DEBOUNCE_MS else 0L
        val task = Runnable {
            pendingDuringCall = null
            try {
                CallEndOverlayManager.showDuringCall(context, phoneNumber, phase)
            } catch (e: Exception) {
                Log.e(TAG, "showDuringCall $phase failed: ${e.message}", e)
            }
        }
        pendingDuringCall = task
        if (delay == 0L) {
            task.run()
        } else {
            Log.d(TAG, "scheduleDuringCall: cold-start debounce ${delay}ms phase=$phase")
            mainHandler.postDelayed(task, delay)
        }
    }

    private fun ensureSessionLoaded(context: Context) {
        if (sessionLoaded) return
        sessionLoaded = true
        val session = CallSessionStore.load(context) ?: return
        lastState = session.lastState
        sawRinging = session.sawRinging
        sawOffhook = session.sawOffhook
        offhookStartMs = session.offhookStartMs
        capturedNumber = session.capturedNumber
        Log.d(
            TAG,
            "CallSessionStore restored: lastState=$lastState sawRinging=$sawRinging " +
                "sawOffhook=$sawOffhook number=${describeNumber(capturedNumber)}"
        )
    }

    private fun persist(context: Context) {
        CallSessionStore.save(
            context,
            lastState = lastState,
            sawRinging = sawRinging,
            sawOffhook = sawOffhook,
            offhookStartMs = offhookStartMs,
            capturedNumber = capturedNumber
        )
    }

    private fun describeNumber(number: String?): String = when {
        number == null -> "null"
        number.isEmpty() -> "empty"
        else -> number
    }

    private fun resolveIncomingNumberFromCallLog(context: Context, approxCallEndMs: Long): String? {
        val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCallLogPermission) return null

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val entryDate = if (dateIndex != -1) cursor.getLong(dateIndex) else -1L
                val ageMs = approxCallEndMs - entryDate
                if (numberIndex == -1 || entryDate < 0 || ageMs !in 0..CALL_LOG_MATCH_WINDOW_MS) {
                    return null
                }
                cursor.getString(numberIndex)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveIncomingNumberFromCallLog failed: ${e.message}", e)
            null
        }
    }

    private fun handleState(context: Context, state: Int, phoneNumber: String?) {
        if (!phoneNumber.isNullOrBlank()) {
            capturedNumber = phoneNumber
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                sawRinging = true
                lastState = state
                persist(context)
                if (PreferenceManager.isCallEndEnabled(context)) {
                    scheduleDuringCall(context, capturedNumber, DuringCallPhase.RINGING)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!sawOffhook) offhookStartMs = System.currentTimeMillis()
                sawOffhook = true
                lastState = state
                persist(context)
                if (PreferenceManager.isCallEndEnabled(context)) {
                    val phase = if (sawRinging) DuringCallPhase.IN_CALL else DuringCallPhase.OUTGOING
                    scheduleDuringCall(context, capturedNumber, phase)
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                cancelPendingDuringCall()
                if (sawRinging || sawOffhook) {
                    val type = when {
                        sawOffhook && sawRinging -> CallEndType.INCOMING
                        sawOffhook -> CallEndType.OUTGOING
                        else -> CallEndType.MISSED
                    }
                    val durationMs = if (sawOffhook && offhookStartMs > 0)
                        (System.currentTimeMillis() - offhookStartMs).coerceAtLeast(0)
                    else 0L

                    val callEndMs = System.currentTimeMillis()
                    var resolvedNumber = capturedNumber
                    if (type == CallEndType.INCOMING && resolvedNumber.isNullOrBlank()) {
                        resolvedNumber = resolveIncomingNumberFromCallLog(context, callEndMs)
                    }

                    if (PreferenceManager.isCallEndEnabled(context)) {
                        CallEndMetrics.recordAttempt(context)
                        Log.d(
                            TAG,
                            "Call ended -> type=$type durationMs=$durationMs number=${describeNumber(resolvedNumber)} " +
                                "duringShowing=${CallEndOverlayManager.isShowingDuringCall()}"
                        )
                        try {
                            CallEndOverlayManager.show(context, type, resolvedNumber, durationMs)
                        } catch (e: Exception) {
                            Log.e(TAG, "CallEndOverlayManager.show failed: ${e.message}", e)
                            CallEndMetrics.recordMiss(context, "show_threw")
                        }
                    }
                }

                sawRinging = false
                sawOffhook = false
                offhookStartMs = 0L
                capturedNumber = null
                lastState = TelephonyManager.CALL_STATE_IDLE
                CallSessionStore.clear(context)
            }
        }

        if (state != TelephonyManager.CALL_STATE_IDLE) {
            lastState = state
            persist(context)
        }
    }
}
