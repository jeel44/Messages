package com.sms.textmessages.messenger.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactLookup {

    // True only when PhoneLookup finds a contact row. Unlike HomeScreen's
    // getContactName(), this does not fall back to the raw phone number.
    fun isKnownContact(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }
    }
}
