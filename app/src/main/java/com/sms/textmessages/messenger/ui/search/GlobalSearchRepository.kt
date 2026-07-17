package com.sms.textmessages.messenger.ui.search

import android.content.Context
import com.sms.textmessages.messenger.ui.home.getContactName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResult(
    val phoneNumber: String,
    val contactName: String,
    val snippet: String,
    val date: Long
)

////////////////////////////////////////////////////////
// 🔵 GLOBAL SEARCH REPOSITORY
////////////////////////////////////////////////////////

suspend fun searchAllMessages(context: Context, query: String): List<SearchResult> {

    if (query.isBlank()) return emptyList()

    return withContext(Dispatchers.IO) {

        val results = mutableListOf<SearchResult>()
        val seenAddresses = mutableSetOf<String>()

        val uri = android.net.Uri.parse("content://sms")

        val cursor = context.contentResolver.query(
            uri,
            arrayOf("address", "body", "type", "date"),
            null,
            null,
            "date DESC"
        )

        cursor?.use {

            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            val dateIndex = it.getColumnIndex("date")

            while (it.moveToNext()) {

                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                if (!body.contains(query, ignoreCase = true)) continue

                // One row per conversation - since the cursor is ordered by
                // date DESC, the first match seen per address is the most
                // recent matching message in that thread.
                if (!seenAddresses.add(address)) continue

                val contactName = getContactName(context, address)

                results.add(
                    SearchResult(
                        phoneNumber = address,
                        contactName = contactName.ifEmpty { address },
                        snippet = body,
                        date = date
                    )
                )
            }
        }

        results
    }
}
