package com.sms.textmessages.messenger.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.data.db.ThreadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = AppDatabase.getDatabase(context).threadDao()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // phone -> resolved name, so each number is resolved once per ViewModel
    // rather than on every Room emission.
    private val displayNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Numbers already handed to the background PhoneLookup pass, so repeat
    // emissions don't queue the same miss twice.
    private val queuedLookups = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Bumped when the background pass turns a placeholder into a real name,
    // or when contactPreloadDone completes, re-running the map below so the
    // list picks up names that are now in contactNameCache.
    private val namesVersion = MutableStateFlow(0)

    init {
        // If the preload finishes after Room's first emission, contacts would
        // otherwise sit as raw numbers until the whole background PhoneLookup
        // pass ends - re-map from the now-filled cache as soon as it's done.
        viewModelScope.launch {
            contactPreloadDone.await()
            namesVersion.value++
        }
    }

    // Names are resolved here, before stateIn, so the very first emission the
    // inbox renders already has them - no number-then-name flicker from a
    // second resolution pass in the UI. Emissions gate on Room alone - the
    // map only reads whatever contactNameCache holds right now (no provider
    // queries, no wait on contactPreloadDone); a miss shows the number and
    // is confirmed by PhoneLookup in the background.
    //
    // Starting value is SmsRepository.threadSnapshot (pre-warmed in
    // App.preloadThreads(), kept current below), so cached rows render on
    // the first frame. null = no snapshot yet AND Room's Flow hasn't emitted.
    val smsList: StateFlow<List<SmsThread>?> =
        combine(
            dao.getThreadsFlow().onEach { rows ->
                Log.d("TRACE_ROOM", "Room emission size=${rows.size}")
                SmsRepository.threadSnapshot = rows
            },
            namesVersion
        ) { list, _ -> list }
            .map { list -> toDisplayList(list) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                SmsRepository.threadSnapshot?.let { toDisplayList(it) }
            )

    private fun toDisplayList(list: List<ThreadEntity>): List<SmsThread> {
        val misses = ArrayList<String>()
        val mapped = list.map {
            val name = displayNames[it.phone]
                ?: getCachedContactName(it.phone)?.also { cached -> displayNames[it.phone] = cached }
                ?: it.phone.also { phone -> misses += phone }
            SmsThread(
                phone = it.phone,
                lastMessage = it.lastMessage,
                date = it.date,
                isRead = it.isRead,
                threadId = it.threadId,
                pinned = it.pinned,
                displayName = name
            )
        }
        resolveMissesInBackground(misses)
        return mapped
    }

    private fun resolveMissesInBackground(misses: List<String>) {
        val toLookUp = misses.filter { queuedLookups.add(it) }
        if (toLookUp.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            var changed = 0
            toLookUp.forEach { phone ->
                val name = getContactName(context, phone)
                displayNames[phone] = name
                if (name != phone) changed++
            }
            Log.d("TRACE_NAMES", "background lookup: misses=${toLookUp.size} resolvedToName=$changed")
            if (changed > 0) namesVersion.value++
        }
    }

    fun refreshInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            SmsRepository.refreshThreads(context)
            _isLoading.value = false
        }
    }
}
