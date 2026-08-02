package com.sms.textmessages.messenger.utils

import android.content.Context

object PreferenceManager {

    private const val PREF_NAME = "messages_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_ARCHIVED_NUMBERS = "archived_numbers"
    private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
    private const val KEY_PINNED_NUMBERS = "pinned_numbers"
    private const val KEY_MUTED_NUMBERS = "muted_numbers"
    private const val KEY_REACTION_PREFIX = "reaction_"
    private const val KEY_BLOCKED_IMPORT_DONE = "blocked_import_done"
    private const val KEY_ARCHIVE_SUGGESTION_CHECKED = "archive_suggestion_checked"
    private const val KEY_CALL_END_ENABLED = "call_end_enabled"
    private const val KEY_CALL_LOG_DISCLOSURE_SHOWN = "call_log_disclosure_shown"
    private const val KEY_OVERLAY_PERMISSION_REQUESTED = "overlay_permission_requested"
    private const val KEY_CALL_SCREENING_ROLE_REQUESTED = "call_screening_role_requested"
    private const val KEY_BLOCK_CALLS_FROM_BLOCKED = "block_calls_from_blocked"
    private const val KEY_SILENCE_UNKNOWN_CALLERS = "silence_unknown_callers"
    private const val KEY_OVERLAY_LAST_RESORT = "overlay_last_resort_offered"
    private const val KEY_OEM_BATTERY_HELP_DISMISSED = "oem_battery_help_dismissed"
    private const val KEY_OEM_BATTERY_HELP_COUNT = "oem_battery_help_count"
    private const val KEY_OEM_BATTERY_HELP_LAST_MS = "oem_battery_help_last_ms"

    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchDone(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun getArchivedNumbers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ARCHIVED_NUMBERS, emptySet()) ?: emptySet()
    }

    fun archiveNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_ARCHIVED_NUMBERS, emptySet()) ?: emptySet())
        updated.add(number.takeLast(10))
        prefs.edit().putStringSet(KEY_ARCHIVED_NUMBERS, updated).apply()
    }

    fun unarchiveNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_ARCHIVED_NUMBERS, emptySet()) ?: emptySet())
        updated.remove(number.takeLast(10))
        prefs.edit().putStringSet(KEY_ARCHIVED_NUMBERS, updated).apply()
    }

    fun getBlockedNumbers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet()
    }

    fun blockNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet())
        updated.add(number.takeLast(10))
        prefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, updated).apply()
    }

    fun unblockNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_BLOCKED_NUMBERS, emptySet()) ?: emptySet())
        updated.remove(number.takeLast(10))
        prefs.edit().putStringSet(KEY_BLOCKED_NUMBERS, updated).apply()
    }

    fun getPinnedNumbers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PINNED_NUMBERS, emptySet()) ?: emptySet()
    }

    fun pinNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_PINNED_NUMBERS, emptySet()) ?: emptySet())
        updated.add(number.takeLast(10))
        prefs.edit().putStringSet(KEY_PINNED_NUMBERS, updated).apply()
    }

    fun unpinNumber(context: Context, number: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_PINNED_NUMBERS, emptySet()) ?: emptySet())
        updated.remove(number.takeLast(10))
        prefs.edit().putStringSet(KEY_PINNED_NUMBERS, updated).apply()
    }

    fun isNotificationsMuted(context: Context, phoneNumber: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val muted = prefs.getStringSet(KEY_MUTED_NUMBERS, emptySet()) ?: emptySet()
        return muted.contains(phoneNumber.takeLast(10))
    }

    fun setNotificationsMuted(context: Context, phoneNumber: String, muted: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = HashSet(prefs.getStringSet(KEY_MUTED_NUMBERS, emptySet()) ?: emptySet())
        if (muted) {
            updated.add(phoneNumber.takeLast(10))
        } else {
            updated.remove(phoneNumber.takeLast(10))
        }
        prefs.edit().putStringSet(KEY_MUTED_NUMBERS, updated).apply()
    }

    // Message reactions are a local-only annotation on this device - plain SMS
    // has no protocol to transmit a reaction to the other party, so this is
    // never sent, only stored and displayed locally.

    fun getReaction(context: Context, messageId: Long): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REACTION_PREFIX + messageId, null)
    }

    fun setReaction(context: Context, messageId: Long, emoji: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REACTION_PREFIX + messageId, emoji).apply()
    }

    fun clearReaction(context: Context, messageId: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_REACTION_PREFIX + messageId).apply()
    }

    // One-time migration flags - see SmsMigrationManager. Each guards a single
    // best-effort import run once after this app becomes the default SMS app.

    fun isBlockedImportDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCKED_IMPORT_DONE, false)
    }

    fun setBlockedImportDone(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLOCKED_IMPORT_DONE, true).apply()
    }

    fun isArchiveSuggestionChecked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ARCHIVE_SUGGESTION_CHECKED, false)
    }

    fun setArchiveSuggestionChecked(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ARCHIVE_SUGGESTION_CHECKED, true).apply()
    }

    fun isCallEndEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CALL_END_ENABLED, true)
    }

    fun setCallEndEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALL_END_ENABLED, enabled).apply()
    }

    fun isCallLogDisclosureShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CALL_LOG_DISCLOSURE_SHOWN, false)
    }

    fun setCallLogDisclosureShown(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALL_LOG_DISCLOSURE_SHOWN, true).apply()
    }

    fun isOverlayPermissionRequested(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OVERLAY_PERMISSION_REQUESTED, false)
    }

    fun setOverlayPermissionRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION_REQUESTED, true).apply()
    }

    // One-shot Home prompt for ROLE_CALL_SCREENING (Android 11+). Settings
    // can still request the role manually after a denial.
    fun isCallScreeningRoleRequested(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CALL_SCREENING_ROLE_REQUESTED, false)
    }

    fun setCallScreeningRoleRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CALL_SCREENING_ROLE_REQUESTED, true).apply()
    }

    // When true (default), CallScreeningService rejects numbers on the app
    // blocked list so SMS block and phone-call block stay aligned.
    fun isBlockCallsFromBlockedEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCK_CALLS_FROM_BLOCKED, true)
    }

    fun setBlockCallsFromBlockedEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLOCK_CALLS_FROM_BLOCKED, enabled).apply()
    }

    // Opt-in: silence (do not block) callers who are not in Contacts, and
    // silence numbers whose network verification failed.
    fun isSilenceUnknownCallersEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SILENCE_UNKNOWN_CALLERS, false)
    }

    fun setSilenceUnknownCallersEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SILENCE_UNKNOWN_CALLERS, enabled).apply()
    }

    fun isOverlayLastResortOffered(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OVERLAY_LAST_RESORT, false)
    }

    fun setOverlayLastResortOffered(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OVERLAY_LAST_RESORT, true).apply()
    }

    fun isOemBatteryHelpDismissedForever(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OEM_BATTERY_HELP_DISMISSED, false)
    }

    fun setOemBatteryHelpDismissedForever(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OEM_BATTERY_HELP_DISMISSED, true).apply()
    }

    fun oemBatteryHelpPromptCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_OEM_BATTERY_HELP_COUNT, 0)
    }

    fun oemBatteryHelpLastPromptMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_OEM_BATTERY_HELP_LAST_MS, 0L)
    }

    fun markOemBatteryHelpPrompted(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_OEM_BATTERY_HELP_COUNT, prefs.getInt(KEY_OEM_BATTERY_HELP_COUNT, 0) + 1)
            .putLong(KEY_OEM_BATTERY_HELP_LAST_MS, System.currentTimeMillis())
            .apply()
    }
}