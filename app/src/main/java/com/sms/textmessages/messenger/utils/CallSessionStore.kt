package com.sms.textmessages.messenger.utils

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

// Survives process death between PHONE_STATE broadcasts so RINGING → IDLE
// still reconstructs when the OEM kills and restarts the process mid-call.
object CallSessionStore {

    private const val TAG = "CALLEND_DEBUG"
    private const val PREF = "call_session_prefs"
    private const val KEY_LAST_STATE = "last_state"
    private const val KEY_SAW_RINGING = "saw_ringing"
    private const val KEY_SAW_OFFHOOK = "saw_offhook"
    private const val KEY_OFFHOOK_START_MS = "offhook_start_ms"
    private const val KEY_NUMBER = "captured_number"
    private const val KEY_WAS_BLOCKED = "was_blocked"
    private const val KEY_UPDATED_MS = "updated_ms"

    // Sessions older than this are treated as stale (abandoned calls).
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    data class Session(
        val lastState: Int,
        val sawRinging: Boolean,
        val sawOffhook: Boolean,
        val offhookStartMs: Long,
        val capturedNumber: String?,
        val wasBlocked: Boolean = false
    )

    fun save(
        context: Context,
        lastState: Int,
        sawRinging: Boolean,
        sawOffhook: Boolean,
        offhookStartMs: Long,
        capturedNumber: String?,
        wasBlocked: Boolean = false
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LAST_STATE, lastState)
            .putBoolean(KEY_SAW_RINGING, sawRinging)
            .putBoolean(KEY_SAW_OFFHOOK, sawOffhook)
            .putLong(KEY_OFFHOOK_START_MS, offhookStartMs)
            .putString(KEY_NUMBER, capturedNumber)
            .putBoolean(KEY_WAS_BLOCKED, wasBlocked)
            .putLong(KEY_UPDATED_MS, System.currentTimeMillis())
            .apply()
    }

    fun markBlocked(context: Context, number: String?) {
        val existing = load(context)
        save(
            context,
            lastState = existing?.lastState ?: TelephonyManager.CALL_STATE_RINGING,
            sawRinging = existing?.sawRinging ?: true,
            sawOffhook = existing?.sawOffhook ?: false,
            offhookStartMs = existing?.offhookStartMs ?: 0L,
            capturedNumber = number?.takeIf { it.isNotBlank() } ?: existing?.capturedNumber,
            wasBlocked = true
        )
        Log.d(TAG, "CallSessionStore.markBlocked number=${number ?: "null"}")
    }

    fun load(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val updated = prefs.getLong(KEY_UPDATED_MS, 0L)
        if (updated == 0L) return null
        val age = System.currentTimeMillis() - updated
        if (age !in 0..MAX_AGE_MS) {
            Log.d(TAG, "CallSessionStore.load: stale session ageMs=$age - clearing")
            clear(context)
            return null
        }
        return Session(
            lastState = prefs.getInt(KEY_LAST_STATE, TelephonyManager.CALL_STATE_IDLE),
            sawRinging = prefs.getBoolean(KEY_SAW_RINGING, false),
            sawOffhook = prefs.getBoolean(KEY_SAW_OFFHOOK, false),
            offhookStartMs = prefs.getLong(KEY_OFFHOOK_START_MS, 0L),
            capturedNumber = prefs.getString(KEY_NUMBER, null),
            wasBlocked = prefs.getBoolean(KEY_WAS_BLOCKED, false)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
