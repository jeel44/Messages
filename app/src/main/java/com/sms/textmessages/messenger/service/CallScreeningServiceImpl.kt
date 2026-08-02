package com.sms.textmessages.messenger.service

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayManager
import com.sms.textmessages.messenger.ui.overlay.DuringCallPhase
import com.sms.textmessages.messenger.utils.ContactLookup
import com.sms.textmessages.messenger.utils.PreferenceManager

// Screens incoming calls while this app holds ROLE_CALL_SCREENING.
// Also starts the Truecaller-style during-call overlay (caller ID bubble)
// for incoming and outgoing so the process stays alive until hangup.
class CallScreeningServiceImpl : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        val allowOverlay = if (isIncoming) {
            respondIncoming(callDetails, number)
        } else {
            respondToCall(callDetails, allowResponse())
            true
        }

        if (allowOverlay && PreferenceManager.isCallEndEnabled(this)) {
            val phase = if (isIncoming) DuringCallPhase.RINGING else DuringCallPhase.OUTGOING
            Handler(Looper.getMainLooper()).post {
                try {
                    CallEndOverlayManager.showDuringCall(applicationContext, number, phase)
                } catch (e: Exception) {
                    Log.e(TAG, "showDuringCall from screening failed: ${e.message}", e)
                }
            }
        }
    }

    // Returns false when the call is fully blocked (no caller-ID overlay).
    private fun respondIncoming(callDetails: Call.Details, number: String?): Boolean {
        val last10 = number?.takeLast(10)
        val isBlocked = last10 != null &&
            PreferenceManager.getBlockedNumbers(this).contains(last10)
        val isContact = number != null && ContactLookup.isKnownContact(this, number)
        val verificationFailed = isCallerVerificationFailed(callDetails)

        var disallow = false
        var reject = false
        var silence = false

        if (PreferenceManager.isBlockCallsFromBlockedEnabled(this) && isBlocked) {
            disallow = true
            reject = true
        } else if (PreferenceManager.isSilenceUnknownCallersEnabled(this)) {
            if (number == null || !isContact || verificationFailed) {
                silence = true
            }
        }

        Log.d(
            TAG,
            "onScreenCall INCOMING: number=${number ?: "null"} isBlocked=$isBlocked " +
                "disallow=$disallow silence=$silence"
        )

        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(disallow)
                .setRejectCall(reject)
                .setSilenceCall(silence && !disallow)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        )
        return !disallow
    }

    private fun allowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .build()

    private fun isCallerVerificationFailed(callDetails: Call.Details): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return callDetails.callerNumberVerificationStatus ==
            Connection.VERIFICATION_STATUS_FAILED
    }
}
