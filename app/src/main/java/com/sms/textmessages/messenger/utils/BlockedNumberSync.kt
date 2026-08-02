package com.sms.textmessages.messenger.utils

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log

// Keeps the platform BlockedNumberContract in sync with this app's blocked
// list when the OS allows it (typically while this app is the default SMS
// handler). Failures are logged and ignored - app prefs remain the source of
// truth for CallScreeningService and the inbox.
object BlockedNumberSync {

    private const val TAG = "BlockedNumberSync"

    fun addToSystemBlocked(context: Context, number: String) {
        if (number.isBlank()) return
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            Log.d(TAG, "addToSystemBlocked: skipped - user cannot block numbers")
            return
        }

        try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            Log.d(TAG, "addToSystemBlocked: inserted $number")
        } catch (e: Exception) {
            Log.w(TAG, "addToSystemBlocked: failed for $number - ${e.message}")
        }
    }

    fun removeFromSystemBlocked(context: Context, number: String) {
        if (number.isBlank()) return
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            Log.d(TAG, "removeFromSystemBlocked: skipped - user cannot block numbers")
            return
        }

        try {
            BlockedNumberContract.unblock(context, number)
            Log.d(TAG, "removeFromSystemBlocked: unblocked $number")
        } catch (e: Exception) {
            Log.w(TAG, "removeFromSystemBlocked: failed for $number - ${e.message}")
        }
    }
}
