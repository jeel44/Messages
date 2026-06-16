package com.sms.textmessages.messenger.ui.newchat

import android.content.Context
import android.provider.ContactsContract


object ContactsRepository {


    private var cachedContacts: List<Contact>? = null

    fun loadContacts(context: Context): List<Contact> {

        // return cached contacts if already loaded
        cachedContacts?.let { return it }

        val contacts = mutableListOf<Contact>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {

            val nameIndex =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

            val phoneIndex =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {

                val name = it.getString(nameIndex) ?: ""
                val phone = it.getString(phoneIndex) ?: ""

                contacts.add(Contact(name, phone))
            }
        }
        android.util.Log.d("CONTACT_DEBUG", "Loaded contacts: ${contacts.size}")
        cachedContacts = contacts.distinctBy { it.phone }

        return cachedContacts!!
    }
}