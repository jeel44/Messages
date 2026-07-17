package com.sms.textmessages.messenger.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms.textmessages.messenger.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = AppDatabase.getDatabase(context).threadDao()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val smsList: StateFlow<List<SmsThread>> =
        dao.getThreadsFlow()
            .map { list ->
                list.map {
                    SmsThread(
                        phone = it.phone,
                        lastMessage = it.lastMessage,
                        date = it.date,
                        isRead = it.isRead,
                        threadId = it.threadId,
                        pinned = it.pinned
                    )
                }
            }
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun refreshInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            SmsRepository.refreshThreads(context)
            _isLoading.value = false
        }
    }
}