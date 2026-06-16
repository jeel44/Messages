package com.sms.textmessages.messenger.ui.newchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewChatViewModel(application: Application) : AndroidViewModel(application) {

    // All contacts
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())

    // Search text
    private val _searchText = MutableStateFlow("")

    // Filtered contacts exposed to UI
    val filteredContacts: StateFlow<List<Contact>> =
        combine(_contacts, _searchText) { contacts, query ->

            if (query.isBlank()) {
                contacts
            } else {
                contacts.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.phone.contains(query)
                }
            }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    init {
        loadContacts()
    }

    private fun loadContacts() {

        viewModelScope.launch {

            val list = withContext(Dispatchers.IO) {
                ContactsRepository.loadContacts(getApplication())
            }

            _contacts.value = list
        }
    }

    fun updateSearch(text: String) {
        _searchText.value = text
    }
}