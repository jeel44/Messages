package com.sms.textmessages.messenger.calling

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sms.textmessages.messenger.MainActivity
import com.sms.textmessages.messenger.ui.home.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shared action handlers for call-end Activities (no WindowManager). */
object CallEndActions {

    private const val TAG = "CALLEND_DEBUG"
    private const val NUMBER_UNAVAILABLE = "Number not available"
    private val scope = CoroutineScope(Dispatchers.Main)

    fun openChat(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_chat_sender", phoneNumber)
                putExtra("open_chat_autofocus", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    fun callBack(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        try {
            context.startActivity(
                Intent(action).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: SecurityException) {
            context.startActivity(
                Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    fun saveContact(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        context.startActivity(
            Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    fun blockNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        scope.launch { SmsRepository.blockThread(context, phoneNumber) }
    }

    fun copyNumber(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", phoneNumber))
        toast(context, "Copied")
    }

    fun viewContact(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        val lookupUri = getContactLookupUri(context, phoneNumber)
        if (lookupUri == null) {
            toast(context, "Contact not found")
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, lookupUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    fun reportSpam(context: Context, phoneNumber: String?) {
        if (phoneNumber == null) {
            toast(context, NUMBER_UNAVAILABLE)
            return
        }
        scope.launch { SmsRepository.blockThread(context, phoneNumber) }
        toast(context, "Reported and blocked")
    }

    fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    private fun getContactLookupUri(context: Context, phoneNumber: String): Uri? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                if (idIndex == -1 || lookupKeyIndex == -1) return null
                ContactsContract.Contacts.getLookupUri(
                    cursor.getLong(idIndex),
                    cursor.getString(lookupKeyIndex)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactLookupUri failed: ${e.message}", e)
            null
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
