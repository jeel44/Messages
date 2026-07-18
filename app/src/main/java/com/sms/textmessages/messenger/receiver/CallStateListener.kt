package com.sms.textmessages.messenger.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayService

enum class CallEndType { INCOMING, OUTGOING, MISSED }

// Detects call start/end purely from TelephonyManager's 3-state call machine
// (IDLE/RINGING/OFFHOOK) - no CALL_LOG permission, no default-dialer role.
// That constraint has a real consequence: the caller/callee *number* is only
// ever available for an incoming call on API < 29 (Android Q+ blanks the
// legacy listener's phoneNumber param without READ_CALL_LOG, and the modern
// TelephonyCallback replacement never carries a number at all, on any API
// level). Outgoing numbers are never exposed this way regardless of API,
// since this app isn't the dialer placing the call. CallEndOverlayService/
// CallEndOverlayCard fall back to "Unknown" when the number is null.
object CallStateListener {

    private const val TAG = "CallStateListener"

    private var registered = false

    private var sawRinging = false
    private var sawOffhook = false
    private var offhookStartMs = 0L
    private var capturedNumber: String? = null

    // Kept as strong field references, not just passed inline - TelephonyManager
    // is documented to hold registered listeners weakly on some OEM builds, so
    // an anonymous instance with no other live reference risks being GC'd
    // mid-call, silently killing detection.
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    // Safe to call multiple times (App.onCreate at startup, then again from
    // MainActivity once READ_PHONE_STATE is actually granted) - the
    // `registered` guard makes every call after the first a no-op. Needed
    // because this permission isn't requested until MainActivity.onCreate,
    // which runs after App.onCreate; without a second call post-grant, a
    // fresh install would never activate call detection until the next
    // process start.
    fun register(context: Context) {
        if (registered) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleState(appContext, state, null)
                }
            }
            modernCallback = callback
            telephonyManager.registerTelephonyCallback(ContextCompat.getMainExecutor(appContext), callback)

        } else {

            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleState(appContext, state, phoneNumber)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }

        registered = true
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

                    Log.d(TAG, "Call ended: type=$type durationMs=$durationMs hasNumber=${capturedNumber != null}")

                    CallEndOverlayService.start(context, type, capturedNumber, durationMs)
                }

                sawRinging = false
                sawOffhook = false
                offhookStartMs = 0L
                capturedNumber = null
            }
        }
    }
}
