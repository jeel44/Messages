@file:OptIn(ExperimentalMaterial3Api::class)

package com.sms.textmessages.messenger.ui.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.ads.HomeAdManager
import com.sms.textmessages.messenger.ui.ads.DefaultBannerAdManager
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
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sms.textmessages.messenger.ui.settings.SettingsScreen
import androidx.compose.material.icons.filled.*
import com.sms.textmessages.messenger.ui.ads.NewChatAdManager
import com.sms.textmessages.messenger.ui.ads.OpenChatAdManager
import com.sms.textmessages.messenger.ui.newchat.NewConversationScreen
import android.widget.Toast
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.saveable.rememberSaveable
import com.sms.textmessages.messenger.data.db.AppDatabase
import kotlinx.coroutines.delay
import com.sms.textmessages.messenger.utils.OverlayPermission
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_semibold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

////////////////////////////////////////////////////////
// 🔵 HOME SCREEN
////////////////////////////////////////////////////////

@Composable
fun HomeScreen(onRequestDefault: () -> Unit) {

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
        InboxUI()
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

        if (HomeAdManager.nativeAdState.value == null) {
            HomeAdManager.loadNative(activity)
        }

        if (DefaultBannerAdManager.bannerAdState.value == null) {
            DefaultBannerAdManager.loadBanner(activity)
        }
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
fun InboxUI() {
    var selectedChat by remember { mutableStateOf<SmsThread?>(null) }
    val context = LocalContext.current
    val activity = context as Activity
    var isSearching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var openSettings by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<Long>()) }
    var pinnedSenders by remember { mutableStateOf(setOf<String>()) }
    var openNewChat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val smsList by viewModel.smsList.collectAsState()

    LaunchedEffect(smsList) {
        Log.d("TRACE_UI", "UI list updated size=${smsList.size}")

        smsList.take(5).forEach {
            Log.d("TRACE_UI", "UI TOP -> id=${it.threadId} date=${it.date}")
        }
    }



    val contentResolver = context.contentResolver

    LaunchedEffect(Unit) {

        OverlayPermission.requestOverlayPermission(activity)

    }

    LaunchedEffect(Unit) {

        OpenChatAdManager.load(activity)
        NewChatAdManager.load(activity)

    }

    DisposableEffect(Unit) {

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {

                Log.d("SMS_DEBUG", "InboxUI received broadcast")

                if (intent.action == "SMS_INBOX_UPDATED") {

                    scope.launch {

                        viewModel.refreshInbox()

                    }
                }
            }
        }

        val filter = IntentFilter("SMS_INBOX_UPDATED")

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->

            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {

                // Reload native ad when returning to home
                HomeAdManager.loadNative(activity)

                // Reload banner if needed
                DefaultBannerAdManager.loadBanner(activity)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }



    val nativeAd = HomeAdManager.nativeAdState.value
    val filters = listOf("All SMS", "Personal", "Transaction", "OTPs", "Offers")
    var selectedFilter by remember { mutableStateOf("All SMS") }

    val contactNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(smsList) {

        withContext(Dispatchers.IO) {

            smsList.forEach { sms ->

                if (!contactNames.containsKey(sms.phone)) {

                    val name = getContactName(context, sms.phone)

                    withContext(Dispatchers.Main) {
                        contactNames[sms.phone] = name
                    }
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

    val filteredList = smsList

//    val filteredList by remember(
//        smsList, // 🔥 IMPORTANT FIX
//        selectedFilter,
//        debouncedSearch,
//        pinnedSenders,
//        searchIndex
//    ) {
//
//        derivedStateOf {
//
//            var result: List<Triple<SmsThread, String, String>> =
//                if (selectedFilter == "All SMS") {
//                    searchIndex
//                } else {
//                    searchIndex.filter { triple ->
//                        filterMessages(listOf(triple.first), selectedFilter).isNotEmpty()
//                    }
//                }
//
//            if (debouncedSearch.isNotEmpty()) {
//
//                val query = debouncedSearch.lowercase(Locale.getDefault())
//
//                result = result.filter { triple ->
//
//                    val sms = triple.first
//                    val name = triple.second
//                    val message = triple.third
//
//                    name.lowercase(Locale.getDefault()).contains(query) ||
//                            sms.phone.contains(query) ||
//                            message.contains(query)
//                }
//            }
//
//            val pinnedSet = pinnedSenders
//
//            result
//                .map { triple -> triple.first }
//                .sortedWith(
//                    compareByDescending<SmsThread> { pinnedSet.contains(it.phone) }
//                        .thenByDescending { it.date }
//                )
//        }
//    }


    BackHandler(enabled = isSearching) {
        isSearching = false
        searchText = ""
    }



    if (selectedChat != null) {

        BackHandler {
            selectedChat = null
        }

        val contactName = getContactName(context, selectedChat!!.phone)

        var messages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }

        LaunchedEffect(selectedChat) {

            messages = withContext(Dispatchers.IO) {
                SmsRepository.loadMessages(
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

                        val updated = SmsRepository.loadMessages(
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

            onDispose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }

        ChatScreen(
            contactName = contactName,
            phoneNumber = selectedChat!!.phone,
            messages = messages.map { msg ->
                ChatMessage(
                    text = msg.body,
                    time = formatMessageDate(msg.date),
                    date = msg.date,
                    isMe = msg.type == 2
                )
            },
            onBackClick = {
                selectedChat = null
            }
        )

        return
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

                    } else if (isSearching) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            IconButton(
                                onClick = {
                                    isSearching = false
                                    searchText = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Black
                                )
                            }

                            BasicTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.Black,
                                    fontSize = 18.sp,
                                    fontFamily = GeneralSansMedium
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp),
                                decorationBox = { innerTextField ->
                                    if (searchText.isEmpty()) {
                                        Text(
                                            text = "Search messages",
                                            color = Color(0xFF757575),
                                            fontSize = 18.sp,
                                            fontFamily = GeneralSansMedium
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }else {

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

                            markSelectedAsRead(context, selectedMessages)

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

                            val senders =
                                smsList
                                    .filter { selectedMessages.contains(it.date) }
                                    .map { it.phone }

                            pinnedSenders =
                                if (senders.all { pinnedSenders.contains(it) }) {
                                    pinnedSenders - senders
                                } else {
                                    pinnedSenders + senders
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

                            deleteMessages(context, selectedMessages)

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

                        IconButton(
                            onClick = {
                                isSearching = !isSearching
                                if (!isSearching) searchText = ""
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search_custom),
                                contentDescription = null,
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
                    containerColor = if (isSearching) Color.White else Color(0xFF3E6AE1)
                )
                )        },
        floatingActionButton = {
            FloatingActionButton(
              onClick = {

                  NewChatAdManager.onClick(activity) {

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
        },
        bottomBar = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {

                if (RemoteConfigManager.homeBannerEnabled()) {
                    BannerAdSection()
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
            if (RemoteConfigManager.homeNativeEnabled() && !isSearching) {
                NativeAdSection(nativeAd)
            }

            if (!isSearching) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    filters.forEach {
                        FilterChipItem(
                            text = it,
                            selected = selectedFilter == it,
                            onClick = { selectedFilter = it }
                        )
                    }
                }
            }




            if (filteredList.isEmpty() && searchText.isNotEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages found",
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
                        key = { it.threadId }
                    ) { sms ->
                        Log.d("TRACE_UI", "Rendering -> id=${sms.threadId} date=${sms.date}")
                        MessageItem(
                            sender = contactNames[sms.phone] ?: sms.phone,
                            message = sms.lastMessage,
                            time = formatMessageDate(sms.date),
                            isRead = if (selectedChat?.phone == sms.phone) true else sms.isRead,
                            isSelected = selectedMessages.contains(sms.date),
                            isPinned = pinnedSenders.contains(sms.phone),

                            onClick = {

                                if (selectedMessages.isNotEmpty()) {

                                    selectedMessages =
                                        if (selectedMessages.contains(sms.date))
                                            selectedMessages - sms.date
                                        else
                                            selectedMessages + sms.date

                                } else {

                                    scope.launch {

                                        withContext(Dispatchers.IO) {
                                            SmsRepository.markThreadAsRead(context, sms.threadId)
                                        }

                                        viewModel.refreshInbox()

                                        OpenChatAdManager.onClick(activity) {
                                            selectedChat = sms
                                        }
                                    }
                                }
                            },

                            onLongClick = {
                                selectedMessages = selectedMessages + sms.date
                            }
                        )
                    }
                }
            }
        }
    }
    if (openSettings) {

        SettingsScreen(
            onBack = { openSettings = false }
        )

    }

    if (openNewChat) {

        NewConversationScreen(
            onBack = { openNewChat = false },
            onStartChat = { name, phone ->

                selectedChat = SmsThread(
                    phone = phone,
                    lastMessage = "",
                    date = System.currentTimeMillis(),
                    isRead = true,
                    threadId = 0L
                )

                openNewChat = false
            }
        )
    }
}


////////////////////////////////////////////////////////
// 🔵 MESSAGE ITEM
////////////////////////////////////////////////////////

@Composable
fun MessageItem(
    sender: String,
    message: String,
    time: String,
    isRead: Boolean,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected)
                    Color(0x223E6AE1)
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

        // Avatar Circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center

        ) {
            Text(
                text = sender.trim().firstOrNull()?.uppercase() ?: "#",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }


        Spacer(modifier = Modifier.width(12.dp))

        // Message Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {

            Text(
                text = sender,
                fontSize = 17.sp,
                fontFamily = GeneralSansSemiBold,
                fontWeight = if (!isRead) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                fontFamily = GeneralSansMedium,
                color = if (!isRead) Color.Black else Color(0xFF5F6368),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time + Unread Dot (RIGHT SIDE)
        Column(
            horizontalAlignment = Alignment.End
        ) {

            Text(
                text = time,
                fontSize = 13.sp,
                fontFamily = GeneralSansMedium,
                color = if (!isRead) Color.Black else Color(0xFF757575)
            )

            if (isPinned) {

                Spacer(modifier = Modifier.height(4.dp))

                Icon(
                    painter = painterResource(R.drawable.ic_pin_small),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF3E6AE1)
                )
            }

            if (!isRead) {

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3E6AE1))
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

@Composable
fun NativeAdSection(nativeAd: NativeAd?) {

    if (nativeAd != null) {

        AndroidView(
            factory = { context ->
                val inflater = LayoutInflater.from(context)
                val view =
                    inflater.inflate(R.layout.native_small_ad_layout, null)
                val adView = view as NativeAdView

                val headline =
                    adView.findViewById<TextView>(R.id.ad_headline)

                adView.headlineView = headline
                headline.text = nativeAd.headline

                adView.setNativeAd(nativeAd)
                adView
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 12.dp)
        )

    } else {
        ShimmerNativeAd()
    }
}

////////////////////////////////////////////////////////
// 🔵 NATIVE SIMMER EFFECT
////////////////////////////////////////////////////////

@Composable
fun ShimmerNativeAd() {

    val transition = rememberInfiniteTransition(label = "shimmer")

    val xShimmer by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFFF5F5F5),
            Color(0xFFE0E0E0)
        ),
        start = Offset(xShimmer, 0f),
        end = Offset(xShimmer + 300f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF2F2F2))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Ad icon shimmer
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            // Title shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body line 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Body line 2
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 BANNER AD
////////////////////////////////////////////////////////

@Composable
fun BannerAdSection() {

    val bannerView = DefaultBannerAdManager.bannerAdState.value

    bannerView?.let { adView ->

        AndroidView(
            factory = { adView },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
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

private fun formatMessageDate(timestamp: Long): String {

    val messageDate = Date(timestamp)
    val now = Date()

    val diff = now.time - messageDate.time
    val oneDay = 24 * 60 * 60 * 1000

    val calendarNow = Calendar.getInstance()
    val calendarMsg = Calendar.getInstance()
    calendarMsg.time = messageDate

    return when {
        isSameDay(calendarNow, calendarMsg) -> {
            SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(messageDate)
        }

        diff < oneDay * 2 && calendarNow.get(Calendar.DAY_OF_YEAR) - calendarMsg.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday"
        }

        diff < oneDay * 7 -> {
            SimpleDateFormat("EEE", Locale.getDefault())
                .format(messageDate) // Mon, Tue
        }

        else -> {
            SimpleDateFormat("dd MMM", Locale.getDefault())
                .format(messageDate) // 12 Jan
        }
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

                val sender = thread.phone

                // If sender contains letters → service SMS
                val isServiceSender = sender.any { it.isLetter() }

                !isServiceSender
            }
        }

        "Transaction" -> {
            list.filter {
                it.lastMessage.contains("debited", true) ||
                        it.lastMessage.contains("credited", true) ||
                        it.lastMessage.contains("transaction", true) ||
                        it.lastMessage.contains("payment", true)
            }
        }

        "OTPs" -> {
            list.filter {
                it.lastMessage.contains("otp", true) ||
                        it.lastMessage.contains("verification code", true) ||
                        it.lastMessage.contains("one time password", true)
            }
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

private fun generateColorFromName(name: String): Color {

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

fun deleteMessages(context: Context, ids: Set<Long>) {

    ids.forEach { id ->

        context.contentResolver.delete(
            android.provider.Telephony.Sms.CONTENT_URI,
            "date = ?",
            arrayOf(id.toString())
        )
    }
}

fun markSelectedAsRead(context: Context, ids: Set<Long>) {

    val values = android.content.ContentValues().apply {
        put(android.provider.Telephony.Sms.READ, 1)
    }

    ids.forEach { id ->

        context.contentResolver.update(
            android.provider.Telephony.Sms.CONTENT_URI,
            values,
            "date = ?",
            arrayOf(id.toString())
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

