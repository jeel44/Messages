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
import com.sms.textmessages.messenger.calling.CallEndEvent
import com.sms.textmessages.messenger.calling.CallEndGatekeeper
import com.sms.textmessages.messenger.calling.DuringCallOverlayHost
import com.sms.textmessages.messenger.ui.overlay.DuringCallPhase
import com.sms.textmessages.messenger.utils.CallSessionStore
import com.sms.textmessages.messenger.utils.PreferenceManager

enum class CallEndType { INCOMING, OUTGOING, MISSED }

/**
 * Truecaller-style call flow:
 * - RINGING / OFFHOOK → during-call overlay bubble
 * - IDLE → dismiss bubble, then after-call Activity via Gatekeeper
 */
class CallStateListener : BroadcastReceiver() {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
        private const val CALL_LOG_MATCH_WINDOW_MS = 30_000L
        // When the process was just born for a late OFFHOOK near hangup, delay
        // the during-call bubble briefly so a following IDLE can cancel it and
        // go straight to the after-call Activity.
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
        Log.d(TAG, "PHONE_STATE onReceive action=${intent.action}")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            Log.d(TAG, "PHONE_STATE ignore non-PHONE_STATE action")
            return
        }

        val appContext = context.applicationContext
        ensureSessionLoaded(appContext)

        val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val state = when (stateExtra) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> {
                Log.w(TAG, "PHONE_STATE unrecognized EXTRA_STATE=$stateExtra - ignoring")
                return
            }
        }

        @Suppress("DEPRECATION")
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val hasPhoneState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(
            TAG,
            "PHONE_STATE EXTRA_STATE=$stateExtra number=${describeNumber(phoneNumber)} " +
                "permPhoneState=$hasPhoneState permCallLog=$hasCallLog " +
                "flags sawRinging=$sawRinging sawOffhook=$sawOffhook " +
                "captured=${describeNumber(capturedNumber)} callEndEnabled=${PreferenceManager.isCallEndEnabled(appContext)}"
        )
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
            if (CallSessionStore.load(context)?.wasBlocked == true) {
                Log.d(TAG, "DuringCall skip - call was blocked")
                return@Runnable
            }
            try {
                DuringCallOverlayHost.show(context, phoneNumber, phase)
            } catch (e: Exception) {
                Log.e(TAG, "DuringCall show $phase failed: ${e.message}", e)
            }
        }
        pendingDuringCall = task
        if (delay == 0L) {
            task.run()
        } else {
            Log.d(TAG, "DuringCall cold-start debounce ${delay}ms phase=$phase")
            mainHandler.postDelayed(task, delay)
        }
    }

    private fun ensureSessionLoaded(context: Context) {
        if (sessionLoaded) return
        sessionLoaded = true
        val session = CallSessionStore.load(context) ?: run {
            Log.d(TAG, "PHONE_STATE session: none")
            return
        }
        lastState = session.lastState
        sawRinging = session.sawRinging
        sawOffhook = session.sawOffhook
        offhookStartMs = session.offhookStartMs
        capturedNumber = session.capturedNumber
        Log.d(
            TAG,
            "PHONE_STATE session restored: lastState=$lastState sawRinging=$sawRinging " +
                "sawOffhook=$sawOffhook number=${describeNumber(capturedNumber)}"
        )
    }

    private fun persist(context: Context) {
        val existingBlocked = CallSessionStore.load(context)?.wasBlocked == true
        CallSessionStore.save(
            context,
            lastState = lastState,
            sawRinging = sawRinging,
            sawOffhook = sawOffhook,
            offhookStartMs = offhookStartMs,
            capturedNumber = capturedNumber,
            wasBlocked = existingBlocked
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
        if (!hasCallLogPermission) {
            Log.d(TAG, "PHONE_STATE CallLog resolve skipped - no READ_CALL_LOG")
            return null
        }

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    Log.d(TAG, "PHONE_STATE CallLog empty")
                    return null
                }
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val entryDate = if (dateIndex != -1) cursor.getLong(dateIndex) else -1L
                val ageMs = approxCallEndMs - entryDate
                if (numberIndex == -1 || entryDate < 0 || ageMs !in 0..CALL_LOG_MATCH_WINDOW_MS) {
                    Log.d(TAG, "PHONE_STATE CallLog row not in window ageMs=$ageMs")
                    return null
                }
                val n = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() }
                Log.d(TAG, "PHONE_STATE CallLog resolved number=${describeNumber(n)} ageMs=$ageMs")
                n
            }
        } catch (e: Exception) {
            Log.e(TAG, "PHONE_STATE CallLog resolve failed: ${e.message}", e)
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
                Log.d(TAG, "PHONE_STATE → RINGING captured=${describeNumber(capturedNumber)}")
                if (PreferenceManager.isCallEndEnabled(context)) {
                    scheduleDuringCall(context, capturedNumber, DuringCallPhase.RINGING)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!sawOffhook) offhookStartMs = System.currentTimeMillis()
                sawOffhook = true
                lastState = state
                persist(context)
                Log.d(
                    TAG,
                    "PHONE_STATE → OFFHOOK sawRinging=$sawRinging offhookStartMs=$offhookStartMs " +
                        "captured=${describeNumber(capturedNumber)}"
                )
                if (PreferenceManager.isCallEndEnabled(context)) {
                    val phase = if (sawRinging) DuringCallPhase.IN_CALL else DuringCallPhase.OUTGOING
                    scheduleDuringCall(context, capturedNumber, phase)
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(
                    TAG,
                    "PHONE_STATE → IDLE sawRinging=$sawRinging sawOffhook=$sawOffhook " +
                        "captured=${describeNumber(capturedNumber)} duringShowing=${DuringCallOverlayHost.isShowing()}"
                )
                cancelPendingDuringCall()
                // Truecaller sequence: dismiss during-call popup, then Activity.
                DuringCallOverlayHost.dismiss()

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
                    if (resolvedNumber.isNullOrBlank()) {
                        Log.d(TAG, "PHONE_STATE IDLE number blank - querying CallLog")
                        resolvedNumber = resolveIncomingNumberFromCallLog(context, callEndMs)
                    }

                    if (!PreferenceManager.isCallEndEnabled(context)) {
                        Log.w(TAG, "PHONE_STATE IDLE skip - callEndEnabled=false")
                    } else {
                        val session = CallSessionStore.load(context)
                        Log.d(
                            TAG,
                            "PHONE_STATE IDLE → Gatekeeper type=$type durationMs=$durationMs " +
                                "number=${describeNumber(resolvedNumber)} wasBlocked=${session?.wasBlocked}"
                        )
                        CallEndGatekeeper.onCallEnded(
                            context,
                            CallEndEvent(
                                number = resolvedNumber,
                                displayName = "Unknown",
                                type = type,
                                durationMs = durationMs,
                                wasBlocked = session?.wasBlocked == true,
                                source = CallEndEvent.Source.PHONE_STATE
                            )
                        )
                    }
                } else {
                    Log.d(TAG, "PHONE_STATE IDLE ignored - no prior RINGING/OFFHOOK in this process")
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
