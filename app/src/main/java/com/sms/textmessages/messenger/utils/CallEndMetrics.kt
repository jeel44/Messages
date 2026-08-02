package com.sms.textmessages.messenger.utils

import android.content.Context
import android.util.Log

// Show/miss counters + gentle OEM-help trigger. Prompt is never forced at
// install — only after real misses, on aggressive OEMs, when the user returns.
object CallEndMetrics {

    private const val TAG = "CALLEND_DEBUG"
    private const val PREF = "call_end_metrics"
    private const val KEY_SHOW = "show_count"
    private const val KEY_MISS = "miss_count"
    private const val KEY_DURING = "during_call_show_count"
    private const val KEY_MORPH = "morph_count"
    private const val KEY_ATTEMPTS = "call_end_attempts"
    private const val KEY_PENDING_OEM_PROMPT = "pending_oem_prompt"

    // After this many misses (and some usage), gently suggest OEM battery help.
    private const val MISS_THRESHOLD = 2

    fun recordDuringCallShown(context: Context) {
        bump(context, KEY_DURING)
        Log.d(TAG, "CallEndMetrics: during-call shown total=${get(context, KEY_DURING)}")
    }

    fun recordMorph(context: Context) {
        bump(context, KEY_MORPH)
        bump(context, KEY_SHOW)
        Log.d(TAG, "CallEndMetrics: morph total=${get(context, KEY_MORPH)} show=${get(context, KEY_SHOW)}")
    }

    fun recordShow(context: Context) {
        bump(context, KEY_SHOW)
        Log.d(TAG, "CallEndMetrics: call-end show total=${get(context, KEY_SHOW)}")
    }

    fun recordAttempt(context: Context) {
        bump(context, KEY_ATTEMPTS)
    }

    fun recordMiss(context: Context, reason: String) {
        bump(context, KEY_MISS)
        Log.w(TAG, "CallEndMetrics: miss reason=$reason totalMiss=${get(context, KEY_MISS)}")
        if (shouldArmOemPrompt(context)) {
            setPendingOemPrompt(context, true)
            Log.d(TAG, "CallEndMetrics: armed gentle OEM battery prompt for next app open")
        }
    }

    fun missCount(context: Context): Int = get(context, KEY_MISS)

    fun attemptCount(context: Context): Int = get(context, KEY_ATTEMPTS)

    fun isOemPromptPending(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_OEM_PROMPT, false)

    fun setPendingOemPrompt(context: Context, pending: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_OEM_PROMPT, pending)
            .apply()
    }

    fun summary(context: Context): String =
        "during=${get(context, KEY_DURING)} morph=${get(context, KEY_MORPH)} " +
            "show=${get(context, KEY_SHOW)} miss=${get(context, KEY_MISS)} attempts=${get(context, KEY_ATTEMPTS)}"

    private fun shouldArmOemPrompt(context: Context): Boolean {
        if (!OemBatteryGuide.isAggressiveOem()) return false
        if (!PreferenceManager.isCallEndEnabled(context)) return false
        if (PreferenceManager.isOemBatteryHelpDismissedForever(context)) return false
        if (PreferenceManager.oemBatteryHelpPromptCount(context) >= 2) return false
        val last = PreferenceManager.oemBatteryHelpLastPromptMs(context)
        if (last > 0 && System.currentTimeMillis() - last < 7L * 24 * 60 * 60 * 1000) return false
        return get(context, KEY_MISS) >= MISS_THRESHOLD
    }

    private fun bump(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun get(context: Context, key: String): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(key, 0)
}
