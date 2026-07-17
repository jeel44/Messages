package com.sms.textmessages.messenger.utils

import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.util.Log
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.SmsThread

////////////////////////////////////////////////////////
// ONE-TIME MIGRATION FROM THE PREVIOUS DEFAULT SMS APP
////////////////////////////////////////////////////////

// Run once (guarded by PreferenceManager's migration flags, from HomeScreen)
// after this app becomes the default SMS app, to carry over state that
// already exists elsewhere: blocked numbers (reliable, platform-level) and
// possibly-archived threads (best-effort, OEM/app-dependent - never
// authoritative, so callers must surface it as a dismissible suggestion,
// never auto-archive).
object SmsMigrationManager {

    private const val TAG = "SmsMigrationManager"

    ////////////////////////////////////////////////////////
    // BLOCKED NUMBERS - android.provider.BlockedNumberContract
    ////////////////////////////////////////////////////////

    // Reads every number the platform has recorded as blocked (shared,
    // system-level provider - not tied to any one app) and merges it into
    // this app's own blocked-numbers set, skipping numbers already blocked
    // here. Returns the count of newly-imported numbers, or 0 if the
    // provider is unavailable/unsupported on this device.
    suspend fun importBlockedNumbers(context: Context): Int {

        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            Log.i(TAG, "Blocked-number import skipped: current user can't block numbers")
            return 0
        }

        val alreadyBlocked = PreferenceManager.getBlockedNumbers(context)
        var importedCount = 0

        try {

            val cursor = context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null
            )

            cursor?.use {

                val numberIndex = it.getColumnIndex(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                )
                if (numberIndex == -1) return@use

                while (it.moveToNext()) {

                    val number = it.getString(numberIndex) ?: continue

                    if (!alreadyBlocked.contains(number.takeLast(10))) {
                        PreferenceManager.blockNumber(context, number)
                        importedCount++
                    }
                }
            }

        } catch (e: Exception) {
            // Provider not supported on this OS/OEM, or this app isn't
            // recognized as eligible despite canCurrentUserBlockNumbers()
            // returning true - skip the import rather than crash.
            Log.w(TAG, "Blocked-number import failed, skipping", e)
            return 0
        }

        Log.i(TAG, "Blocked-number import complete: $importedCount number(s) imported")
        return importedCount
    }

    ////////////////////////////////////////////////////////
    // POSSIBLY-ARCHIVED THREADS - content://mms-sms/conversations, "archived"
    // column when present. Not every SMS app writes this column and it isn't
    // part of the public Telephony API, so a hit here is a hint, not a fact.
    ////////////////////////////////////////////////////////

    suspend fun findPossiblyArchivedThreads(context: Context): List<SmsThread> {

        val candidateThreadIds = try {

            val uri = Uri.parse("content://mms-sms/conversations")

            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            val ids = mutableSetOf<Long>()

            cursor?.use {

                val archivedIndex = it.getColumnIndex("archived")
                val threadIdIndex = it.getColumnIndex("_id")

                if (archivedIndex == -1 || threadIdIndex == -1) {
                    // Column (or the whole conversations projection) isn't
                    // present on this device/OS version - nothing to detect,
                    // not an error.
                    Log.i(TAG, "Archived-thread detection unavailable: no 'archived' column on this device")
                    return@use
                }

                while (it.moveToNext()) {
                    if (it.getInt(archivedIndex) != 0) {
                        ids.add(it.getLong(threadIdIndex))
                    }
                }
            }

            ids

        } catch (e: Exception) {
            Log.w(TAG, "Archived-thread detection failed, skipping", e)
            emptySet()
        }

        if (candidateThreadIds.isEmpty()) {
            return emptyList()
        }

        // Reuse the same thread listing HomeScreen's inbox is built from,
        // rather than re-querying content://sms ourselves, to resolve each
        // flagged thread id to the phone number this app archives by.
        return SmsRepository.getInbox(context)
            .filter { candidateThreadIds.contains(it.threadId) }
    }
}
