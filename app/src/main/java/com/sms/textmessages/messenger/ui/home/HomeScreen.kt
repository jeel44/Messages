@file:OptIn(ExperimentalMaterial3Api::class)

package com.sms.textmessages.messenger.ui.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.widget.FrameLayout
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdExpiry
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import com.sms.textmessages.messenger.ui.chat.ChatMessage
import com.sms.textmessages.messenger.ui.chat.ChatScreen
import com.sms.textmessages.messenger.ui.contactinfo.ContactInfoScreen
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sms.textmessages.messenger.ui.settings.SettingsScreen
import com.sms.textmessages.messenger.ui.archived.ArchivedScreen
import com.sms.textmessages.messenger.ui.blocked.BlockedNumbersScreen
import androidx.compose.material.icons.filled.*
import com.sms.textmessages.messenger.ui.newchat.NewConversationScreen
import com.sms.textmessages.messenger.ui.media.SharedMediaScreen
import android.widget.Toast
import com.sms.textmessages.messenger.utils.PreferenceManager
import com.sms.textmessages.messenger.utils.SmsMigrationManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.saveable.rememberSaveable
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.data.db.GroupEntity
import com.sms.textmessages.messenger.ui.groupchat.GroupChatScreen
import com.sms.textmessages.messenger.ui.newgroup.NewGroupScreen
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import com.sms.textmessages.messenger.ui.theme.PrimaryBlue
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.OverlayOtpIndigo
import com.sms.textmessages.messenger.ui.theme.OverlayOfferAmber
import com.sms.textmessages.messenger.ui.theme.OverlayCreditGreen
import com.sms.textmessages.messenger.receiver.NotificationCategory
import com.sms.textmessages.messenger.receiver.classifyNotification

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

////////////////////////////////////////////////////////
// 🔵 HOME SCREEN
////////////////////////////////////////////////////////

@Composable
fun HomeScreen(onRequestDefault: () -> Unit, onSearchClick: () -> Unit = {}) {

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var isDefault by remember {
        mutableStateOf(isDefaultSmsApp(context))
    }

    DisposableEffect(lifecycleOwner) {

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefault = isDefaultSmsApp(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isDefault) {
        DefaultUI(onRequestDefault)
    } else {
        InboxUI(onSearchClick)
    }
}

////////////////////////////////////////////////////////
// 🔵 DEFAULT SCREEN
////////////////////////////////////////////////////////

@Composable
fun DefaultUI(onRequestDefault: () -> Unit) {

    val transition = rememberInfiniteTransition(label = "shine")

    val shimmerX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineAnim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2F6BDE),
            Color(0xFF4A90E2),
            Color(0xFF2F6BDE)
        ),
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + 300f, 0f)
    )

    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(Unit) {
        AdCache.ensure(AdPlacement.HOME_NATIVE, activity)
        AdCache.ensure(AdPlacement.DEFAULT_BANNER, activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Messages",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontFamily = GeneralSansSemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3E6AE1)
                )
            )
        },
        bottomBar = {
            if (RemoteConfigManager.defaultBannerEnabled()) {
                BannerAdSection()
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF4F6FA)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(R.drawable.ic_get_started_illustration),
                contentDescription = null,
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "To use messages, make it",
                fontSize = 16.sp,
                fontFamily = GeneralSansMedium
            )

            Text(
                text = "default app",
                fontSize = 16.sp,
                fontFamily = GeneralSansMedium
            )

            Spacer(modifier = Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(brush)
                    .clickable { onRequestDefault() }
                    .padding(horizontal = 60.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Set as default app",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = GeneralSansSemiBold
                )
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 INBOX UI
////////////////////////////////////////////////////////

@Composable
fun InboxUI(onSearchClick: () -> Unit = {}) {
    var selectedChat by remember { mutableStateOf<SmsThread?>(null) }
    // Hoisted alongside selectedChat (not remembered inside the
    // `if (selectedChat != null)` block) and explicitly preloaded together
    // with selectedChat at every call site that opens a thread, in the same
    // action, before the async load starts. This guarantees the previous
    // thread's messages can never be the ones rendered for the newly
    // selected thread, even for one frame. Already the full SMS+MMS timeline
    // - see SmsRepository.loadThreadMessages() - not just the SMS half, so
    // there's no separate attachments list to load/assign alongside it.
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    val context = LocalContext.current
    val activity = context as Activity
    var searchText by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }
    var openArchived by remember { mutableStateOf(false) }
    var openBlocked by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<Long>()) } // stores threadId
    var openNewChat by remember { mutableStateOf(false) }
    var openNewGroup by remember { mutableStateOf(false) }
    var activeGroup by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var showContactInfo by remember { mutableStateOf(false) }
    var openSharedMediaPhone by remember { mutableStateOf<String?>(null) }
    var startChatSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val smsList by viewModel.smsList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Trigger initial sync from content://sms into Room the moment InboxUI is composed.
    // Without this, Room is empty on first launch (after becoming default app) and the
    // Flow never emits non-empty because nothing has written to it yet.
    LaunchedEffect(Unit) {
        viewModel.refreshInbox()
    }

    // Live-refresh the inbox thread list on a new MMS. SMS doesn't need an
    // observer here - SmsReceiver writes the new/updated ThreadEntity directly
    // into Room on arrival, and smsList (below) is a reactive Room Flow, so it
    // picks that up on its own. MmsDownloadCompleteReceiver only inserts into
    // content://mms (by design - see MmsProvider), so Room never learns about
    // a new or newly-bumped MMS thread unless something re-syncs it; this
    // observer is that trigger, calling the same refreshInbox() the initial
    // sync above uses.
    DisposableEffect(Unit) {

        val mmsInboxObserver = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                viewModel.refreshInbox()
            }
        }

        context.contentResolver.registerContentObserver(
            android.provider.Telephony.Mms.CONTENT_URI,
            true,
            mmsInboxObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(mmsInboxObserver)
        }
    }

    // One-time migration from whatever app was previously the default SMS
    // handler - see SmsMigrationManager. Blocked numbers are imported
    // silently (platform-level data, reliable); possibly-archived threads
    // are only ever surfaced as a dismissible suggestion below, never
    // auto-archived. Both are guarded by their own persisted flag so this
    // only ever runs once, regardless of how many times InboxUI recomposes.
    var archiveSuggestionThreads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var showArchiveSuggestion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        withContext(Dispatchers.IO) {

            if (!PreferenceManager.isBlockedImportDone(context)) {
                SmsMigrationManager.importBlockedNumbers(context)
                PreferenceManager.setBlockedImportDone(context)
            }

            if (!PreferenceManager.isArchiveSuggestionChecked(context)) {

                val alreadyArchived = PreferenceManager.getArchivedNumbers(context)

                val candidates = SmsMigrationManager.findPossiblyArchivedThreads(context)
                    .filter { !alreadyArchived.contains(it.phone.takeLast(10)) }

                PreferenceManager.setArchiveSuggestionChecked(context)

                if (candidates.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        archiveSuggestionThreads = candidates
                        showArchiveSuggestion = true
                    }
                }
            }
        }
    }

    LaunchedEffect(smsList) {
        Log.d("TRACE_UI", "UI list updated size=${smsList.size}")

        smsList.take(5).forEach {
            Log.d("TRACE_UI", "UI TOP -> id=${it.threadId} date=${it.date}")
        }
    }

    // Auto-scroll to a new top conversation (new SMS bumping a thread to #1),
    // but only when the user is already near the top - never yank their scroll
    // position while they're reading further down the list.
    var previousTopThreadId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(smsList) {
        val newTopThreadId = smsList.firstOrNull()?.threadId
        val priorTopThreadId = previousTopThreadId
        previousTopThreadId = newTopThreadId

        if (priorTopThreadId != null &&
            newTopThreadId != null &&
            newTopThreadId != priorTopThreadId &&
            listState.firstVisibleItemIndex <= 1
        ) {
            listState.animateScrollToItem(0)
        }
    }



    val contentResolver = context.contentResolver

    // Interstitials need Remote Config IDs; reload when config becomes ready
    // so a cold start that reaches the inbox before fetch completes still
    // picks up real unit IDs without delaying the message list.
    val remoteConfigReadyForInterstitials = RemoteConfigManager.isReady
    LaunchedEffect(remoteConfigReadyForInterstitials) {
        if (!remoteConfigReadyForInterstitials) return@LaunchedEffect
        AdCache.ensure(AdPlacement.OPEN_CHAT_INTERSTITIAL, activity)
        AdCache.ensure(AdPlacement.NEW_CHAT_INTERSTITIAL, activity)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->

            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!RemoteConfigManager.isReady) return@LifecycleEventObserver

                AdCache.ensure(AdPlacement.HOME_NATIVE, activity)
                AdCache.ensure(AdPlacement.HOME_BANNER, activity)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }



    val nativeAd = AdCache.nativeState(AdPlacement.HOME_NATIVE).value
    val filters = listOf("All SMS", "Personal", "Transaction", "OTPs", "Offers")
    var selectedFilter by remember { mutableStateOf("All SMS") }

    // Resolved on Dispatchers.IO into a plain local map, then flushed to the
    // Compose state map in batches of 50 - not one Main-thread commit per
    // contact. With 2000+ threads, writing to contactNames per-lookup forced
    // 1000+ individual recomposition passes over the entire InboxUI scope in
    // a row, right as the list first appears - exactly when the user starts
    // scrolling - which measured as a 4x jump in janky frames (gfxinfo) versus
    // scrolling once resolution had finished. Batching collapses that to a
    // couple dozen commits while still revealing names progressively.
    val contactNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(smsList) {

        withContext(Dispatchers.IO) {

            val pending = HashMap<String, String>()

            smsList.forEach { sms ->

                if (!contactNames.containsKey(sms.phone) && !pending.containsKey(sms.phone)) {

                    pending[sms.phone] = getContactName(context, sms.phone)

                    if (pending.size >= 50) {
                        val batch = HashMap(pending)
                        pending.clear()
                        withContext(Dispatchers.Main) {
                            contactNames.putAll(batch)
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    contactNames.putAll(pending)
                }
            }
        }
    }

    LaunchedEffect(selectedFilter) {
        listState.animateScrollToItem(0)
    }

    var debouncedSearch by remember { mutableStateOf("") }

    LaunchedEffect(searchText) {
        kotlinx.coroutines.delay(400)
        debouncedSearch = searchText
    }

    val searchIndex: List<Triple<SmsThread, String, String>> =
        remember(smsList, contactNames) {

            smsList.map { sms ->

                val name = contactNames[sms.phone] ?: sms.phone

                Triple(
                    sms,
                    name,
                    sms.lastMessage.lowercase(Locale.getDefault())
                )
            }
        }

    // archived/blocked exclusion and pinned-first/date-desc ordering are both
    // enforced by ThreadDao's WHERE/ORDER BY (single reactive source of truth
    // - see ThreadDao.getThreadsFlow()), so smsList already arrives filtered
    // and sorted; this only layers the category/search UI filters on top,
    // preserving that order.
    val filteredList = remember(searchIndex, selectedFilter, debouncedSearch) {
        var result = if (selectedFilter == "All SMS") {
            searchIndex
        } else {
            searchIndex.filter { triple ->
                filterMessages(listOf(triple.first), selectedFilter).isNotEmpty()
            }
        }

        if (debouncedSearch.isNotEmpty()) {
            val query = debouncedSearch.lowercase(Locale.getDefault())
            result = result.filter { triple ->
                triple.second.lowercase(Locale.getDefault()).contains(query) ||
                        triple.first.phone.contains(query) ||
                        triple.third.contains(query)
            }
        }

        result.map { it.first }
    }



    if (selectedChat != null) {

        BackHandler {
            if (openSharedMediaPhone != null) {
                openSharedMediaPhone = null
            } else if (showContactInfo) {
                showContactInfo = false
            } else {
                selectedChat = null
            }
        }

        val contactName = getContactName(context, selectedChat!!.phone)

        if (showContactInfo) {

            ContactInfoScreen(
                contactName = contactName,
                phoneNumber = selectedChat!!.phone,
                onBack = {
                    showContactInfo = false
                },
                onBlockContact = {
                    showContactInfo = false
                    selectedChat = null
                },
                onOpenSharedMedia = {
                    openSharedMediaPhone = selectedChat!!.phone
                    showContactInfo = false
                },
                onSearchInChat = {
                    startChatSearch = true
                    showContactInfo = false
                }
            )

            return
        }

        openSharedMediaPhone?.let { sharedMediaPhone ->

            SharedMediaScreen(
                phoneNumber = sharedMediaPhone,
                onBack = {
                    openSharedMediaPhone = null
                },
                onMediaClick = {}
            )

            return
        }

        LaunchedEffect(selectedChat) {

            messages = withContext(Dispatchers.IO) {
                SmsRepository.loadThreadMessages(
                    context,
                    selectedChat!!.threadId
                )
            }
        }

        val scope = rememberCoroutineScope()

        DisposableEffect(selectedChat) {

            val observer = object : android.database.ContentObserver(
                android.os.Handler(android.os.Looper.getMainLooper())
            ) {

                override fun onChange(selfChange: Boolean) {

                    if (selectedChat == null) return

                    scope.launch(Dispatchers.IO) {

                        val updated = SmsRepository.loadThreadMessages(
                            context,
                            selectedChat!!.threadId
                        )

                        withContext(Dispatchers.Main) {
                            messages = updated
                        }
                    }
                }
            }

            context.contentResolver.registerContentObserver(
                android.provider.Telephony.Sms.CONTENT_URI,
                true,
                observer
            )

            // Same observer instance, registered on content://mms as well - a
            // new MMS in this thread should reload it exactly like a new SMS
            // does. unregisterContentObserver below removes it from both URIs.
            context.contentResolver.registerContentObserver(
                android.provider.Telephony.Mms.CONTENT_URI,
                true,
                observer
            )

            onDispose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }

        ChatScreen(
            contactName = contactName,
            phoneNumber = selectedChat!!.phone,
            threadId = selectedChat!!.threadId,
            messages = messages,
            onBackClick = {
                selectedChat = null
            },
            onContactClick = {
                showContactInfo = true
            },
            onOpenSharedMedia = {
                openSharedMediaPhone = selectedChat!!.phone
            },
            onBlockContact = {
                showContactInfo = false
                selectedChat = null
            },
            startWithSearchOpen = startChatSearch
        )

        LaunchedEffect(selectedChat) {
            // Consumed as ChatScreen's initial state above - reset so the next
            // chat opened (a different thread, or this one reopened later)
            // doesn't inherit a stale search-open flag.
            startChatSearch = false
        }

        return
    }

    activeGroup?.let { (groupId, phones) ->

        BackHandler {
            activeGroup = null
        }

        GroupChatScreen(
            groupId = groupId,
            participantNumbers = phones,
            onBackClick = {
                activeGroup = null
            }
        )

        return
    }

    // Neither a chat nor a group chat is open at this point (both branches
    // above already returned) - this is the true Inbox root. There's nothing
    // left for Back to close, so the default Activity-finish behavior would
    // destroy the whole task instead of just backgrounding it, forcing every
    // later reopen through SplashActivity's full startup sequence again
    // (it's the app's only LAUNCHER entry point). moveTaskToBack keeps the
    // task and process alive so the next reopen resumes instantly instead.
    BackHandler {
        activity.moveTaskToBack(false)
    }

    // Everything below this point only composes when neither a chat nor a
    // group chat is open (both branches above end in `return`). Because of
    // that, this whole Scaffold subtree is torn down while a chat is open
    // and freshly mounted - not just recomposed - every time the user
    // returns here, the same way ChatScreen's own LaunchedEffect(Unit)
    // (ChatScreen.kt) already re-fires on every chat entry. Placing the
    // reload-on-return effects here (rather than near the top of InboxUI,
    // where OpenChatAdManager/NewChatAdManager's LaunchedEffect(Unit) lives)
    // is what makes them actually re-run on return-from-chat instead of only
    // once per InboxUI's lifetime.

    // Native / banner: ensure when Remote Config is ready. Ads fill into the
    // already-visible inbox; never block the conversation list.
    val remoteConfigReady = RemoteConfigManager.isReady
    LaunchedEffect(remoteConfigReady) {
        if (!remoteConfigReady) return@LaunchedEffect
        AdCache.ensure(AdPlacement.HOME_NATIVE, activity)
    }

    // Banner: ensure once RC is ready, then force-refresh on a 60s cadence
    // while visible (AdMob console refresh range is 30–150s; 60s is not a
    // documented "expiry", only our on-screen refresh preference).
    LaunchedEffect(remoteConfigReady) {
        if (!remoteConfigReady) return@LaunchedEffect
        while (true) {
            AdCache.ensure(AdPlacement.HOME_BANNER, activity, forceRefresh = true)
            delay(AdExpiry.BANNER_ON_SCREEN_REFRESH_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(

                title = {

                    if (selectedMessages.isNotEmpty()) {

                        Text(
                            text = "${selectedMessages.size} Selected",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = GeneralSansSemiBold
                        )

                    } else {

                        Text(
                            text = "Messages",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontFamily = GeneralSansSemiBold
                        )

                    }
                },

                navigationIcon = {

                    if (selectedMessages.isNotEmpty()) {

                        IconButton(
                            onClick = { selectedMessages = emptySet() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                    } else {

                        val context = LocalContext.current

                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ads),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },

                actions = {

                    if (selectedMessages.isNotEmpty()) {

                        IconButton(onClick = {

                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    markSelectedAsRead(context, selectedMessages)
                                }
                            }

                            selectedMessages = emptySet()

                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mark_read),
                                contentDescription = "Mark Read",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = {

                            val selectedThreads =
                                smsList.filter { selectedMessages.contains(it.threadId) }
                            val shouldPin = selectedThreads.any { !it.pinned }

                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    selectedThreads.forEach { thread ->
                                        SmsRepository.setPinned(context, thread.phone, shouldPin)
                                    }
                                }
                            }

                            selectedMessages = emptySet()

                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pin),
                                contentDescription = "null",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = {

                            val senders =
                                smsList
                                    .filter { selectedMessages.contains(it.threadId) }
                                    .map { it.phone }

                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    senders.forEach { SmsRepository.archiveThread(context, it) }
                                }
                            }

                            selectedMessages = emptySet()

                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_archive),
                                contentDescription = "Archive",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = {

                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    deleteMessages(context, selectedMessages)
                                }
                            }

                            selectedMessages = emptySet()

                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                    } else {

                        val isFiltered = selectedFilter != "All SMS" || debouncedSearch.isNotEmpty()

                        if (isFiltered) {
                            Text(
                                text = if (selectedFilter != "All SMS") selectedFilter else "Filtered",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = GeneralSansMedium,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = { showFilterSheet = true }
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = "Filter conversations",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )

                                if (isFiltered) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF5252))
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search all messages",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { openSettings = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_custom),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3E6AE1)
                )
                )        },
        floatingActionButton = {
            // Existing "New chat" FAB left untouched below; a smaller "New
            // group" FAB is stacked above it rather than restructuring the
            // single-FAB click handling into a menu/dropdown.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                SmallFloatingActionButton(
                    onClick = { openNewGroup = true },
                    containerColor = Color(0xFF3E6AE1)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "New group",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                FloatingActionButton(
                  onClick = {

                      AdCache.onClickGated(activity, AdPlacement.NEW_CHAT_INTERSTITIAL) {

                          openNewChat = true

                      }
                    },
                  containerColor = Color(0xFF3E6AE1),
                    modifier = Modifier.size(60.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        bottomBar = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {

                if (RemoteConfigManager.homeBannerEnabled()) {
                    HomeBannerAdSection()
                }

            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // Native ad always visible
            if (RemoteConfigManager.homeNativeEnabled()) {
                NativeAdSection(nativeAd)
            }

            if (isLoading && smsList.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3E6AE1))
                }

            } else if (filteredList.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchText.isNotEmpty()) "No messages found"
                               else "No messages in this category",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        fontFamily = GeneralSansMedium
                    )
                }

            } else {

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {

                    items(
                        items = filteredList,
                        key = { it.threadId },
                        contentType = { "message" }
                    ) { sms ->
                        val time = remember(sms.date) { formatMessageDate(sms.date) }
                        val copyableCode = remember(sms.lastMessage) { extractCopyableCode(sms.lastMessage) }
                        val category = remember(sms.phone, sms.lastMessage) {
                            classifyNotification(sms.phone, sms.lastMessage)
                        }

                        // Fade + slide-in entrance, once per item per this list's
                        // lifetime. rememberSaveable (not plain remember) is what
                        // makes "once" actually stick - this item's composition is
                        // discarded when it scrolls far enough out, so a plain
                        // remember would reset to false and replay the animation
                        // every time it scrolls back into view. items(key = ...)
                        // above gives each threadId its own saveable slot, so this
                        // survives that discard/recompose cycle correctly.
                        var hasAnimatedIn by rememberSaveable { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            hasAnimatedIn = true
                        }

                        val density = LocalDensity.current
                        val slideDistancePx = remember(density) { with(density) { 14.dp.toPx() } }

                        // Kept as State<Float> (not read via `by` here) so the
                        // per-frame value is only sampled inside the graphicsLayer
                        // block below, at draw time - that's what makes graphicsLayer
                        // cheap. Reading .value via `by` at this level instead
                        // subscribes this whole item scope to every animation tick
                        // and forces a full recomposition per frame per animating
                        // row - measured firsthand: dropped this exact list from
                        // ~1.7% janky frames to 14-57% during a fling through
                        // freshly-revealed rows.
                        val entranceAlphaState = animateFloatAsState(
                            targetValue = if (hasAnimatedIn) 1f else 0f,
                            animationSpec = tween(durationMillis = 320),
                            label = "rowEntranceAlpha"
                        )
                        val entranceOffsetState = animateFloatAsState(
                            targetValue = if (hasAnimatedIn) 0f else slideDistancePx,
                            animationSpec = tween(durationMillis = 320),
                            label = "rowEntranceOffset"
                        )

                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = entranceAlphaState.value
                                translationY = entranceOffsetState.value
                            }
                        ) {
                            MessageItem(
                                sender = contactNames[sms.phone] ?: sms.phone,
                                message = sms.lastMessage,
                                time = time,
                                isRead = if (selectedChat?.phone == sms.phone) true else sms.isRead,
                                isSelected = selectedMessages.contains(sms.threadId),
                                isPinned = sms.pinned,
                                isServiceSender = isServiceSenderPhone(sms.phone),
                                category = category,
                                copyableCode = copyableCode,

                                onClick = {

                                    if (selectedMessages.isNotEmpty()) {

                                        selectedMessages =
                                            if (selectedMessages.contains(sms.threadId))
                                                selectedMessages - sms.threadId
                                            else
                                                selectedMessages + sms.threadId

                                    } else {

                                        scope.launch {

                                            withContext(Dispatchers.IO) {
                                                SmsRepository.markThreadAsRead(context, sms.threadId)
                                            }

                                            // Loaded here, before the open-chat ad gate fires,
                                            // so ChatScreen's very first composition already has the
                                            // full history - messages and selectedChat are assigned
                                            // together below, so there's no frame where ChatScreen
                                            // mounts with an empty list.
                                            val loadedMessages = withContext(Dispatchers.IO) {
                                                SmsRepository.loadThreadMessages(context, sms.threadId)
                                            }

                                            AdCache.onClickGated(activity, AdPlacement.OPEN_CHAT_INTERSTITIAL) {
                                                messages = loadedMessages
                                                selectedChat = sms
                                            }
                                        }
                                    }
                                },

                                onLongClick = {
                                    selectedMessages = selectedMessages + sms.threadId
                                }
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E0E0))
                    }
                }
            }
        }
    }
    if (openSettings) {

        SettingsScreen(
            onBack = { openSettings = false },
            onOpenArchived = { openArchived = true },
            onOpenBlocked = { openBlocked = true }
        )

    }

    if (openArchived) {

        ArchivedScreen(
            onBack = {
                openArchived = false
            },
            onOpenChat = { phoneNumber, threadId ->

                openArchived = false
                openSettings = false

                scope.launch {

                    // Preloaded before selectedChat is set, same as the inbox row's
                    // onClick above - avoids a blank-then-populate flash.
                    val loadedMessages = withContext(Dispatchers.IO) {
                        SmsRepository.loadThreadMessages(context, threadId)
                    }

                    messages = loadedMessages
                    selectedChat = SmsThread(
                        phone = phoneNumber,
                        lastMessage = "",
                        date = System.currentTimeMillis(),
                        isRead = true,
                        threadId = threadId
                    )
                }
            }
        )
    }

    if (openBlocked) {

        BlockedNumbersScreen(
            onBack = {
                openBlocked = false
            }
        )
    }

    if (showArchiveSuggestion) {

        val count = archiveSuggestionThreads.size

        AlertDialog(
            onDismissRequest = { showArchiveSuggestion = false },
            title = { Text("Archive suggestion") },
            text = {
                Text(
                    "We found $count conversation${if (count == 1) "" else "s"} that may " +
                        "have been archived in your previous messaging app. Archive " +
                        "${if (count == 1) "it" else "them"} here too?"
                )
            },
            confirmButton = {
                TextButton(onClick = {

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            archiveSuggestionThreads.forEach {
                                SmsRepository.archiveThread(context, it.phone)
                            }
                        }
                    }

                    showArchiveSuggestion = false
                }) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveSuggestion = false }) {
                    Text("Not now")
                }
            }
        )
    }

    if (openNewChat) {

        NewConversationScreen(
            onBack = { openNewChat = false },
            onStartChat = { name, phone ->

                scope.launch {

                    val existingThreadId = withContext(Dispatchers.IO) {
                        SmsRepository.findExistingThreadId(context, phone)
                    }

                    // DisposableEffect(selectedChat) below only registers the
                    // ContentObserver for future changes - it does not do an
                    // initial load. Load here first so ChatScreen's first
                    // composition already has the existing thread's history,
                    // same as the inbox row tap and Archived-screen open paths.
                    val loadedMessages = if (existingThreadId != null) {
                        withContext(Dispatchers.IO) {
                            SmsRepository.loadThreadMessages(context, existingThreadId)
                        }
                    } else {
                        emptyList()
                    }

                    messages = loadedMessages
                    selectedChat = SmsThread(
                        phone = phone,
                        lastMessage = "",
                        date = System.currentTimeMillis(),
                        isRead = true,
                        threadId = existingThreadId ?: 0L
                    )

                    openNewChat = false
                }
            }
        )
    }

    if (openNewGroup) {

        NewGroupScreen(
            onBack = { openNewGroup = false },
            onCreateGroup = { phones ->

                val newGroupId = java.util.UUID.randomUUID().toString()

                scope.launch {

                    withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(context).groupDao().insertGroup(
                            GroupEntity(
                                groupId = newGroupId,
                                participantNumbers = phones.joinToString(","),
                                groupName = null,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }

                    activeGroup = newGroupId to phones
                    openNewGroup = false
                }
            }
        )
    }

    if (showFilterSheet) {

        val sheetState = rememberModalBottomSheetState()

        fun closeFilterSheet() {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    showFilterSheet = false
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "Filter conversations",
                        fontSize = 18.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.Black
                    )

                    IconButton(onClick = { closeFilterSheet() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontFamily = GeneralSansMedium
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchText.isEmpty()) {
                                Text(
                                    text = "Search by name or number",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 16.sp,
                                    fontFamily = GeneralSansMedium
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchText.isNotEmpty()) {
                        IconButton(
                            onClick = { searchText = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Category",
                    fontSize = 13.sp,
                    fontFamily = GeneralSansMedium,
                    color = Color(0xFF757575)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    filters.forEach {
                        FilterChipItem(
                            text = it,
                            selected = selectedFilter == it,
                            onClick = { selectedFilter = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF3E6AE1))
                        .clickable { closeFilterSheet() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        fontSize = 16.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


////////////////////////////////////////////////////////
// 🔵 MESSAGE ITEM
////////////////////////////////////////////////////////

private data class CategoryPillStyle(
    val label: String,
    val textColor: Color,
    val backgroundColor: Color
)

// Reuses classifyNotification (NotificationClassifier.kt) - the same
// classifier SmsReceiver's notifications and CategoryOverlayCard use - so the
// inbox pill always agrees with what the notification/overlay called this
// message. Colors reuse the same role-to-color mapping as
// CategoryOverlayCard.visualsFor (AccentBlue/personal, indigo/OTP, amber/offer,
// green/transaction) rather than inventing a separate palette.
// SERVICE_DEFAULT (a service sender that isn't OTP/transaction/offer) shows
// no pill - it doesn't correspond to any of the inbox's filter categories.
private fun categoryPillStyle(category: NotificationCategory): CategoryPillStyle? = when (category) {
    NotificationCategory.PERSONAL -> CategoryPillStyle(
        label = "Personal",
        textColor = AccentBlue,
        backgroundColor = AccentBlue.copy(alpha = 0.12f)
    )
    NotificationCategory.OTP -> CategoryPillStyle(
        label = "OTP",
        textColor = OverlayOtpIndigo,
        backgroundColor = OverlayOtpIndigo.copy(alpha = 0.14f)
    )
    NotificationCategory.OFFER -> CategoryPillStyle(
        label = "Offer",
        textColor = OverlayOfferAmber,
        backgroundColor = OverlayOfferAmber.copy(alpha = 0.16f)
    )
    NotificationCategory.TRANSACTION_DEBIT,
    NotificationCategory.TRANSACTION_CREDIT -> CategoryPillStyle(
        label = "Transaction",
        textColor = OverlayCreditGreen,
        backgroundColor = OverlayCreditGreen.copy(alpha = 0.14f)
    )
    NotificationCategory.SERVICE_DEFAULT -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    sender: String,
    message: String,
    time: String,
    isRead: Boolean,
    isSelected: Boolean,
    isPinned: Boolean,
    isServiceSender: Boolean,
    category: NotificationCategory,
    copyableCode: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected)
                    PrimaryBlue.copy(alpha = 0.13f)
                else
                    Color.Transparent
            )
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }

            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val circleColor = remember(sender) {
            generateColorFromName(sender)
        }

        val initial = sender.trim().firstOrNull()
        val hasSensibleLetter = initial?.isLetter() == true

        // Avatar Circle - initial-based color for every sender, personal or
        // service/DLT alike; falls back to an icon when there's no usable letter.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center

        ) {
            if (!isServiceSender || hasSensibleLetter) {
                Text(
                    text = initial?.uppercase()?.toString() ?: "#",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }


        Spacer(modifier = Modifier.width(12.dp))

        // Message Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = sender,
                    fontSize = 17.sp,
                    fontFamily = GeneralSansSemiBold,
                    fontWeight = if (!isRead) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isRead) Color(0xFF1A1A1A) else Color(0xFF5F6368),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val pill = categoryPillStyle(category)

                if (pill != null) {

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pill.backgroundColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pill.label,
                            fontSize = 11.sp,
                            fontFamily = GeneralSansMedium,
                            fontWeight = FontWeight.Medium,
                            color = pill.textColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                fontFamily = GeneralSansMedium,
                fontWeight = if (!isRead) FontWeight.SemiBold else FontWeight.Normal,
                color = if (!isRead) Color(0xFF1A1A1A) else Color(0xFF5F6368),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Copy button - only shown when the preview text yields a detected
        // OTP/reference code (OTP and Transaction categories only).
        if (copyableCode != null) {

            IconButton(
                onClick = { copyCodeToClipboard(context, copyableCode) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        // Time + Unread Dot (RIGHT SIDE)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.Top)
        ) {

            Text(
                text = time,
                fontSize = 13.sp,
                fontFamily = GeneralSansMedium,
                color = Color(0xFF757575)
            )

            if (isPinned) {

                Spacer(modifier = Modifier.height(4.dp))

                Icon(
                    painter = painterResource(R.drawable.ic_pin_small),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = PrimaryBlue
                )
            }

            if (!isRead) {

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                )
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 FILTER CHIP
////////////////////////////////////////////////////////

@Composable
fun FilterChipItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected)
                    Color(0xFF3E6AE1)
                else
                    Color(0xFFF1F3F6)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else Color.Black
        )
    }
}

////////////////////////////////////////////////////////
// 🔵 NATIVE AD
////////////////////////////////////////////////////////

// Binds every asset view a NativeAdView needs registered per AdMob's contract
// (headline, body, CTA, icon, media) and hides the view for any field that's
// null on this particular ad, instead of leaving an empty gap or stale text.
// Called from both `factory` (first bind) and `update` (rebind) below so a
// freshly-loaded ad on resume actually replaces what's on screen, not just
// what's held in nativeAdState.
private fun NativeAdView.bindHomeNativeAd(nativeAd: NativeAd) {

    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView>(R.id.ad_body)
    val cta = findViewById<Button>(R.id.ad_call_to_action)
    val icon = findViewById<ImageView>(R.id.ad_icon)
    val media = findViewById<MediaView>(R.id.ad_media)

    headlineView = headline
    bodyView = body
    callToActionView = cta
    iconView = icon
    mediaView = media

    headline.text = nativeAd.headline

    val adBody = nativeAd.body
    if (adBody.isNullOrEmpty()) {
        body.visibility = View.GONE
    } else {
        body.text = adBody
        body.visibility = View.VISIBLE
    }

    val adCta = nativeAd.callToAction
    if (adCta.isNullOrEmpty()) {
        cta.visibility = View.GONE
    } else {
        cta.text = adCta
        cta.visibility = View.VISIBLE
    }

    val adIcon = nativeAd.icon
    if (adIcon == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(adIcon.drawable)
        icon.visibility = View.VISIBLE
    }

    val mediaContent = nativeAd.mediaContent
    if (mediaContent == null) {
        media.visibility = View.GONE
    } else {
        media.mediaContent = mediaContent
        media.visibility = View.VISIBLE
    }

    setNativeAd(nativeAd)
}

@Composable
fun NativeAdSection(nativeAd: NativeAd?) {

    if (nativeAd != null) {

        AndroidView(
            factory = { context ->
                val inflater = LayoutInflater.from(context)
                val adView =
                    inflater.inflate(R.layout.native_small_ad_layout, null) as NativeAdView
                adView.bindHomeNativeAd(nativeAd)
                adView
            },
            update = { adView ->
                adView.bindHomeNativeAd(nativeAd)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 12.dp)
        )

    } else {
        AdShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 12.dp),
            variant = AdShimmerVariant.COMPACT_ROW
        )
    }
}

////////////////////////////////////////////////////////
// 🔵 BANNER AD
////////////////////////////////////////////////////////

@Composable
fun BannerAdSection() {

    val bannerView = AdCache.bannerState(AdPlacement.DEFAULT_BANNER).value

    if (bannerView != null) {

        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    addView(bannerView)
                }
            },
            update = { container ->
                if (container.getChildAt(0) !== bannerView) {
                    container.removeAllViews()
                    container.addView(bannerView)
                }
            },
            onRelease = { container ->
                container.removeAllViews()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
    } else {
        AdShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            variant = AdShimmerVariant.BANNER
        )
    }
}

@Composable
fun HomeBannerAdSection() {

    val bannerView = AdCache.bannerState(AdPlacement.HOME_BANNER).value

    if (bannerView != null) {

        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    addView(bannerView)
                }
            },
            update = { container ->
                if (container.getChildAt(0) !== bannerView) {
                    container.removeAllViews()
                    container.addView(bannerView)
                }
            },
            onRelease = { container ->
                container.removeAllViews()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
    } else {
        AdShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            variant = AdShimmerVariant.BANNER
        )
    }
}
////////////////////////////////////////////////////////
// 🔵 DEFAULT CHECK
////////////////////////////////////////////////////////

private fun isDefaultSmsApp(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager =
            context.getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }
    return false
}

private val FMT_TIME = SimpleDateFormat("hh:mm a", Locale.getDefault())
private val FMT_DAY  = SimpleDateFormat("EEE",     Locale.getDefault())
private val FMT_DATE = SimpleDateFormat("dd MMM",  Locale.getDefault())

private fun formatMessageDate(timestamp: Long): String {

    val messageDate = Date(timestamp)
    val now = Date()

    val diff = now.time - messageDate.time
    val oneDay = 24 * 60 * 60 * 1000

    val calendarNow = Calendar.getInstance()
    val calendarMsg = Calendar.getInstance()
    calendarMsg.time = messageDate

    return when {
        isSameDay(calendarNow, calendarMsg) -> FMT_TIME.format(messageDate)

        diff < oneDay * 2 && calendarNow.get(Calendar.DAY_OF_YEAR) - calendarMsg.get(Calendar.DAY_OF_YEAR) == 1 ->
            "Yesterday"

        diff < oneDay * 7 -> FMT_DAY.format(messageDate)

        else -> FMT_DATE.format(messageDate)
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun filterMessages(
    list: List<SmsThread>,
    filter: String
): List<SmsThread> {

    return when (filter) {

        "Personal" -> {

            list.filter { thread ->
                !isServiceSenderPhone(thread.phone)
            }
        }

        "Transaction" -> {
            list.filter { isTransactionMessage(it.lastMessage) }
        }

        "OTPs" -> {
            list.filter { isOtpMessage(it.lastMessage) }
        }

        "Offers" -> {
            list.filter {
                it.lastMessage.contains("offer", true) ||
                        it.lastMessage.contains("sale", true) ||
                        it.lastMessage.contains("discount", true) ||
                        it.lastMessage.contains("cashback", true)
            }
        }

        else -> list
    }
}

// A phone containing letters is a service/DLT sender ID (e.g. "VM-HDFCBK"),
// never a real dialable personal number.
private fun isServiceSenderPhone(phone: String): Boolean {
    return phone.any { it.isLetter() }
}

private fun isTransactionMessage(text: String): Boolean {
    return text.contains("debited", true) ||
            text.contains("credited", true) ||
            text.contains("transaction", true) ||
            text.contains("payment", true)
}

private fun isOtpMessage(text: String): Boolean {
    return text.contains("otp", true) ||
            text.contains("verification code", true) ||
            text.contains("one time password", true)
}

// Matches a standalone 4-8 digit code, e.g. the "482913" in "482913 is your OTP".
private val otpCodeRegex = Regex("""\b\d{4,8}\b""")

// Matches a labelled alphanumeric reference/UTR/txn id, capturing the value only.
private val referenceCodeRegex = Regex(
    """(?:ref(?:erence)?\.?\s*(?:no\.?|number|id)?|utr|txn\s*id|transaction\s*id)\s*[:\-]?\s*([A-Za-z0-9]{6,})""",
    RegexOption.IGNORE_CASE
)

// Only OTP and Transaction messages carry a copyable code; Personal/Offers never do.
// Public so the chat screen (ChatScreen.kt) can reuse the same detection logic.
fun extractCopyableCode(message: String): String? {
    return when {
        isOtpMessage(message) -> otpCodeRegex.find(message)?.value
        isTransactionMessage(message) -> referenceCodeRegex.find(message)?.groupValues?.getOrNull(1)
        else -> null
    }
}

// Shared clipboard-copy + confirmation, reused by both the inbox row Copy
// button and the chat bubble Copy button so the behavior stays identical.
fun copyCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun generateColorFromName(name: String): Color {

    val colors = listOf(
        Color(0xFF3E6AE1),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFF009688),
        Color(0xFF795548),
        Color(0xFF607D8B)
    )

    val index = kotlin.math.abs(name.hashCode()) % colors.size
    return colors[index]
}

private fun markMessagesAsRead(context: Context, sender: String) {

    val values = android.content.ContentValues().apply {
        put(android.provider.Telephony.Sms.READ, 1)
    }

    val normalized = sender.takeLast(10)

    context.contentResolver.update(
        android.provider.Telephony.Sms.CONTENT_URI,
        values,
        "address LIKE ? AND read = 0",
        arrayOf("%$normalized%")
    )

    // notify UI to refresh inbox
    context.sendBroadcast(Intent("SMS_INBOX_UPDATED"))
}

fun deleteMessages(context: Context, threadIds: Set<Long>) {

    threadIds.forEach { threadId ->

        context.contentResolver.delete(
            android.provider.Telephony.Sms.CONTENT_URI,
            "thread_id = ?",
            arrayOf(threadId.toString())
        )
    }
}

fun markSelectedAsRead(context: Context, threadIds: Set<Long>) {

    val values = android.content.ContentValues().apply {
        put(android.provider.Telephony.Sms.READ, 1)
    }

    threadIds.forEach { threadId ->

        context.contentResolver.update(
            android.provider.Telephony.Sms.CONTENT_URI,
            values,
            "thread_id = ? AND read = 0",
            arrayOf(threadId.toString())
        )
    }
}
fun getContactName(context: Context, phoneNumber: String): String {

    if (phoneNumber.isBlank()) {
        return ""
    }

    val uri = android.net.Uri.withAppendedPath(
        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        android.net.Uri.encode(phoneNumber)
    )

    return try {

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(
                    android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME
                )
                if (nameIndex != -1) {
                    return it.getString(nameIndex)
                }
            }
        }

        phoneNumber

    } catch (e: Exception) {
        phoneNumber
    }
}

