package com.sms.textmessages.messenger.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.service.OverlayHostService
import com.sms.textmessages.messenger.utils.PreferenceManager

enum class CallEndType { INCOMING, OUTGOING, MISSED }

// Manifest-declared receiver for android.intent.action.PHONE_STATE, the same
// architecture SmsReceiver already uses for SMS_DELIVER: a receiver the
// system can deliver to (and wake the process for) even when the app isn't
// running. This replaced an in-process TelephonyCallback/PhoneStateListener
// registered from Application.onCreate(), which only existed for the
// lifetime of the app's process and never fired once the app was swiped
// from recents.
//
// State (lastState/sawRinging/etc.) lives in the companion object rather
// than as instance fields - the OS creates a fresh receiver instance per
// broadcast, so only a companion/static survives across onReceive() calls.
//
// Detects call start/end purely from TelephonyManager's 3-state call machine
// (IDLE/RINGING/OFFHOOK) - no default-dialer role. The incoming_number extra
// on the PHONE_STATE broadcast is blank on API 29+ without READ_CALL_LOG, so
// for an answered incoming call this falls back to querying the most recent
// CallLog.Calls entry once the call ends (see resolveIncomingNumberFromCallLog
// below) - only if READ_CALL_LOG is granted, otherwise the number stays null.
// Outgoing numbers are never obtainable this way on any API level regardless
// of permissions, since this app isn't the dialer placing the call - that's a
// platform limit, not something READ_CALL_LOG fixes. CallEndOverlayManager/
// CallEndOverlayCard fall back to "Unknown" when the number is null.
class CallStateListener : BroadcastReceiver() {

    companion object {
        // Shared across CallStateListener/CallEndOverlayManager/CallEndAdManager
        // for this debugging pass - filter logcat on this single tag to trace a
        // call end top to bottom.
        private const val TAG = "CALLEND_DEBUG"

        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var sawRinging = false
        private var sawOffhook = false
        private var offhookStartMs = 0L
        private var capturedNumber: String? = null

        // How recent the top CallLog.Calls row has to be, relative to "now"
        // at call-end time, to be trusted as this call's entry rather than a
        // stale/unrelated one (e.g. the OS hasn't written the row yet, or a
        // completely different earlier call is still the most recent row).
        private const val CALL_LOG_MATCH_WINDOW_MS = 30_000L
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

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

        // Deprecated but functional - the modern replacement for this extra
        // never carries a number at all. Only ever populated for an incoming
        // call, and only up to API 28 without READ_CALL_LOG (see class doc).
        @Suppress("DEPRECATION")
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "onReceive: action=${intent.action} EXTRA_STATE=$stateExtra rawIncomingNumber=${describeNumber(phoneNumber)}")

        handleState(context.applicationContext, state, phoneNumber)
    }

    private fun describeNumber(number: String?): String = when {
        number == null -> "null"
        number.isEmpty() -> "empty"
        else -> number
    }

    private fun stateName(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_IDLE -> "IDLE"
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        else -> "UNKNOWN($state)"
    }

    // Fallback for an answered incoming call whose incoming_number extra came
    // back blank. Queries the single most recent CallLog.Calls row - the OS
    // writes that row for this call at roughly the same moment CALL_STATE_IDLE
    // fires, so the newest row should be it - but rejects the row if it's not
    // actually recent, rather than trusting it blindly, since a stale/wrong
    // row would silently misattribute this call to a different number.
    private fun resolveIncomingNumberFromCallLog(context: Context, approxCallEndMs: Long): String? {

        val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

        if (!hasCallLogPermission) {
            Log.d(TAG, "resolveIncomingNumberFromCallLog: READ_CALL_LOG not granted - skipping fallback, number stays unresolved")
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
                    Log.d(TAG, "resolveIncomingNumberFromCallLog: CallLog is empty - no fallback available")
                    return null
                }

                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val entryDate = if (dateIndex != -1) cursor.getLong(dateIndex) else -1L
                val ageMs = approxCallEndMs - entryDate

                if (numberIndex == -1 || entryDate < 0 || ageMs !in 0..CALL_LOG_MATCH_WINDOW_MS) {
                    Log.w(
                        TAG,
                        "resolveIncomingNumberFromCallLog: most recent CallLog entry looks stale/mismatched " +
                            "(entryDate=$entryDate approxCallEndMs=$approxCallEndMs ageMs=$ageMs) - rejecting rather than risk a wrong number"
                    )
                    return null
                }

                val number = cursor.getString(numberIndex)
                Log.d(TAG, "resolveIncomingNumberFromCallLog: matched CallLog entry ageMs=$ageMs number=${describeNumber(number)}")
                number?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveIncomingNumberFromCallLog: query failed - ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }

    // Transition table (no CALL_LOG):
    // - IDLE -> RINGING -> OFFHOOK -> IDLE   = answered incoming call.
    //   Duration is measured from OFFHOOK (pickup) to IDLE (hangup), i.e.
    //   talk time only - matches how a native call log reports duration.
    // - IDLE -> RINGING -> IDLE              = missed/declined incoming call.
    //   Never reaches OFFHOOK, so duration is always 0.
    // - IDLE -> OFFHOOK -> IDLE (no RINGING) = outgoing call. The placing
    //   device never enters RINGING for its own outgoing call - OFFHOOK alone
    //   covers both the ringback-while-dialing portion and (if answered) the
    //   connected portion, and the two can't be told apart from state alone.
    //   So an unanswered outgoing call and an answered one both look like
    //   this same transition; "duration" here can include ringback time.
    // Known limitation: call-waiting (a second RINGING while already OFFHOOK
    // on an existing call) isn't disambiguated and will misreport as
    // INCOMING for the original call - state-only detection can't see the
    // difference without CALL_LOG.
    private fun handleState(context: Context, state: Int, phoneNumber: String?) {

        Log.d(
            TAG,
            "State transition: ${stateName(lastState)} -> ${stateName(state)} | " +
                "sawRinging=$sawRinging sawOffhook=$sawOffhook rawPhoneNumber=${describeNumber(phoneNumber)}"
        )

        if (!phoneNumber.isNullOrBlank()) {
            capturedNumber = phoneNumber
        }

        when (state) {

            TelephonyManager.CALL_STATE_RINGING -> {
                sawRinging = true
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!sawOffhook) offhookStartMs = System.currentTimeMillis()
                sawOffhook = true
            }

            TelephonyManager.CALL_STATE_IDLE -> {

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
                    val numberSource: String

                    if (type == CallEndType.INCOMING && resolvedNumber.isNullOrBlank()) {
                        val fallback = resolveIncomingNumberFromCallLog(context, callEndMs)
                        if (fallback != null) {
                            resolvedNumber = fallback
                            numberSource = "CallLog fallback"
                        } else {
                            numberSource = "unresolved (broadcast extra empty, CallLog fallback unavailable/rejected)"
                        }
                    } else if (type == CallEndType.OUTGOING && resolvedNumber.isNullOrBlank()) {
                        numberSource = "unresolved - outgoing number unavailable, platform limit (not fixed by READ_CALL_LOG)"
                    } else {
                        numberSource = if (resolvedNumber.isNullOrBlank()) "unresolved" else "broadcast extra"
                    }

                    if (PreferenceManager.isCallEndEnabled(context)) {

                        Log.d(
                            TAG,
                            "Call ended -> type=$type durationMs=$durationMs number=${describeNumber(resolvedNumber)} source=$numberSource " +
                                "- showing call end overlay"
                        )

                        // Routes into OverlayHostService, the persistent
                        // foreground service started from App.onCreate() (or
                        // BootCompletedReceiver after a reboot) - it's already
                        // running by the time this broadcast ever fires, so
                        // this only redelivers onStartCommand() to the
                        // existing instance rather than starting a new
                        // foreground service from this (non-exempted)
                        // broadcast receiver. Still wrapped and logged in
                        // full: a WindowManager BadTokenException or similar
                        // downstream in CallEndOverlayManager.show() should
                        // not fail silently.
                        try {
                            OverlayHostService.showCallEndOverlay(context, type, resolvedNumber, durationMs)
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Call ended -> OverlayHostService.showCallEndOverlay() threw ${e.javaClass.name}: ${e.message}",
                                e
                            )
                        }

                    } else {
                        Log.d(TAG, "Call ended -> type=$type but call end screen is disabled in settings - skipping")
                    }

                } else {
                    Log.d(TAG, "IDLE reached with no prior RINGING/OFFHOOK - not a call end, ignoring")
                }

                sawRinging = false
                sawOffhook = false
                offhookStartMs = 0L
                capturedNumber = null
            }
        }

        lastState = state
    }
}
