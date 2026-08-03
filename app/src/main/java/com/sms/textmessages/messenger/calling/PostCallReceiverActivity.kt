package com.sms.textmessages.messenger.calling

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.utils.CallSessionStore
import com.sms.textmessages.messenger.utils.PreferenceManager

/**
 * Handles [TelecomManager.ACTION_POST_CALL] (API 29+) when this app is the
 * call-screening app (or otherwise eligible). Builds a [CallEndEvent] and
 * hands it to [CallEndGatekeeper]; never shows UI itself.
 *
 * Note: [TelecomManager.EXTRA_CALL_DURATION] is a duration *bucket*
 * (VERY_SHORT/SHORT/MEDIUM/LONG), not seconds — prefer session-measured
 * duration when available.
 */
class PostCallReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handlePostCall(intent)
        } catch (e: Exception) {
            Log.e(TAG, "POST_CALL handle failed: ${e.message}", e)
        } finally {
            finish()
        }
    }

    private fun handlePostCall(intent: Intent?) {
        if (intent?.action != TelecomManager.ACTION_POST_CALL) {
            Log.d(TAG, "POST_CALL bail: unexpected action=${intent?.action}")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(TAG, "POST_CALL bail: API < 29")
            return
        }
        // Whole dedup vs InCallService/dialer path: if we ever hold ROLE_DIALER,
        // POST_CALL must not also fire the after-call screen.
        if (DialerRole.isHeld(this)) {
            Log.d(TAG, "POST_CALL bail: ROLE_DIALER held")
            return
        }
        if (!PreferenceManager.isCallEndEnabled(this)) {
            Log.d(TAG, "POST_CALL bail: user pref disabled")
            return
        }
        if (!RemoteConfigManager.afterCallScreenEnabled()) {
            Log.d(TAG, "POST_CALL bail: RC master off")
            return
        }
        if (!RemoteConfigManager.afterCallPostCallPathEnabled()) {
            Log.d(TAG, "POST_CALL bail: RC post_call path off")
            return
        }

        val handle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(TelecomManager.EXTRA_HANDLE, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(TelecomManager.EXTRA_HANDLE)
        }
        val number = handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val disconnectCause = intent.getIntExtra(
            TelecomManager.EXTRA_DISCONNECT_CAUSE,
            DisconnectCause.UNKNOWN
        )
        val durationBucket = intent.getIntExtra(
            TelecomManager.EXTRA_CALL_DURATION,
            TelecomManager.DURATION_VERY_SHORT
        )

        val session = CallSessionStore.load(this)
        val durationMs = resolveDurationMs(session, durationBucket)
        val type = resolveType(disconnectCause, durationMs, session)

        Log.d(
            TAG,
            "POST_CALL: number=${number ?: "null"} cause=$disconnectCause " +
                "durationBucket=$durationBucket durationMs=$durationMs type=$type " +
                "wasBlocked=${session?.wasBlocked}"
        )

        CallEndGatekeeper.onCallEnded(
            this,
            CallEndEvent(
                number = number ?: session?.capturedNumber,
                displayName = "Unknown",
                type = type,
                durationMs = durationMs,
                wasBlocked = session?.wasBlocked == true,
                disconnectCause = disconnectCause,
                source = CallEndEvent.Source.POST_CALL
            )
        )
    }

    private fun resolveDurationMs(
        session: CallSessionStore.Session?,
        durationBucket: Int
    ): Long {
        if (session != null && session.sawOffhook && session.offhookStartMs > 0L) {
            return (System.currentTimeMillis() - session.offhookStartMs).coerceAtLeast(0L)
        }
        // Approximate midpoints for Telecom duration buckets.
        return when (durationBucket) {
            TelecomManager.DURATION_VERY_SHORT -> 1_500L
            TelecomManager.DURATION_SHORT -> 30_000L
            TelecomManager.DURATION_MEDIUM -> 90_000L
            TelecomManager.DURATION_LONG -> 180_000L
            else -> 0L
        }
    }

    private fun resolveType(
        disconnectCause: Int,
        durationMs: Long,
        session: CallSessionStore.Session?
    ): CallEndType {
        when (disconnectCause) {
            DisconnectCause.MISSED,
            DisconnectCause.REJECTED -> return CallEndType.MISSED
        }
        if (session != null) {
            return when {
                session.sawOffhook && session.sawRinging -> CallEndType.INCOMING
                session.sawOffhook -> CallEndType.OUTGOING
                session.sawRinging && durationMs <= 0L -> CallEndType.MISSED
                session.sawRinging -> CallEndType.INCOMING
                else -> if (durationMs > 0L) CallEndType.OUTGOING else CallEndType.MISSED
            }
        }
        return if (durationMs > 0L) CallEndType.INCOMING else CallEndType.MISSED
    }

    companion object {
        private const val TAG = "CALLEND_DEBUG"
    }
}
