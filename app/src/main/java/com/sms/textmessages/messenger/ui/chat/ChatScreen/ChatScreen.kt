package com.sms.textmessages.messenger.ui.chat

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.ads.ChatBannerAdManager
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.sms.textmessages.messenger.ui.ads.ChatBackAdManager
import android.telephony.SmsManager
import android.widget.Toast
import android.widget.FrameLayout
import android.Manifest
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.content.Intent
import androidx.compose.foundation.lazy.itemsIndexed
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.copyCodeToClipboard
import com.sms.textmessages.messenger.ui.home.extractCopyableCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sms.textmessages.messenger.ui.theme.PrimaryBlue
import com.sms.textmessages.messenger.utils.PreferenceManager
import com.sms.textmessages.messenger.ui.media.MediaAttachment
import com.sms.textmessages.messenger.ui.media.MmsThumbnail


private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

////////////////////////////////////////////////////////
// 🔵 CHAT SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactName: String,
    phoneNumber: String,
    threadId: Long,
    // Already the full, sorted SMS+MMS timeline for this thread - built by
    // SmsRepository.loadThreadMessages(), which queries both content://sms
    // and content://mms and merges them in the repository layer. Preloaded
    // by the caller before this thread's ChatScreen is ever composed (same
    // pattern HomeScreen/AppNavigation use for the old SMS-only `messages`),
    // so there's no self-fetch on mount and no separate MMS half that can
    // arrive late and reflow the timeline.
    messages: List<ChatMessage>,
    onBackClick: () -> Unit,
    onContactClick: () -> Unit = {},
    onMediaClick: (index: Int) -> Unit = {},
    onOpenSharedMedia: () -> Unit = {},
    onBlockContact: () -> Unit = {},
    startWithSearchOpen: Boolean = false,
    autoFocusInput: Boolean = false
) {

    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var isSearching by remember { mutableStateOf(startWithSearchOpen) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isMuted by remember(phoneNumber) {
        mutableStateOf(PreferenceManager.isNotificationsMuted(context, phoneNumber))
    }

    // TEMPORARY DEBUG LOGGING - runs on every recomposition of ChatScreen.
    // ChatMessage carries no thread/phone identifier of its own, so the
    // message body text is logged as the best available proxy for "which
    // thread does this data actually belong to".
    SideEffect {
        Log.d(
            "ChatFlashDebug",
            "ts=${System.currentTimeMillis()} ChatScreen ENTER/RECOMPOSE phoneNumber=$phoneNumber " +
                "messagesPropSize=${messages.size} " +
                "firstMsgText=${messages.firstOrNull()?.text?.take(30)} " +
                "firstMsgDate=${messages.firstOrNull()?.date}"
        )
    }

    // Keyed on phoneNumber so switching threads always starts from a clean
    // slate seeded by the caller's current messages, never a previous
    // thread's leftover state.
    var chatMessages by remember(phoneNumber) { mutableStateOf(messages.toMutableList()) }

    // TEMPORARY DEBUG LOGGING - runs every recomposition; shows the current
    // value of the remember(phoneNumber)-scoped chatMessages state, which is
    // what the LazyColumn below actually renders.
    Log.d(
        "ChatFlashDebug",
        "ts=${System.currentTimeMillis()} ChatScreen chatMessages CURRENT for phoneNumber=$phoneNumber " +
            "size=${chatMessages.size} firstText=${chatMessages.firstOrNull()?.text?.take(30)}"
    )

    // Personal (non service-sender) threads get the new visual design;
    // OTP/Transaction/Offers threads keep the existing look untouched.
    val isPersonalChat = !isServiceSender(phoneNumber)

    LaunchedEffect(messages) {
        Log.d("SMS_DEBUG", "Step6: LaunchedEffect(messages) fired size=${messages.size} guard=${messages.isNotEmpty()}")
        Log.d(
            "ChatFlashDebug",
            "ts=${System.currentTimeMillis()} ChatScreen LaunchedEffect(messages) fired for phoneNumber=$phoneNumber " +
                "size=${messages.size} firstText=${messages.firstOrNull()?.text?.take(30)} willAssign=${messages.isNotEmpty()}"
        )
        if (messages.isNotEmpty()) {
            chatMessages = messages.toMutableList()
        }
    }

    // Live-refresh chatMessages on a new MMS while this thread is already
    // open - same ContentObserver pattern HomeScreen/GroupChatScreen already
    // use for content://sms, applied to content://mms instead. Reloads
    // through the same unified SmsRepository.loadThreadMessages() the caller
    // used for the initial load, rather than maintaining a separate MMS-only
    // half - there's no partial state to reflow when this fires. Keyed on
    // phoneNumber and threadId so switching threads disposes the old
    // observer (and its closed-over threadId) before registering a new one -
    // it never runs against the wrong thread and never leaks.
    DisposableEffect(phoneNumber, threadId) {

        val mmsObserver = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                scope.launch(Dispatchers.IO) {
                    val updated = SmsRepository.loadThreadMessages(context, threadId)
                    withContext(Dispatchers.Main) {
                        chatMessages = updated.toMutableList()
                    }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            android.provider.Telephony.Mms.CONTENT_URI,
            true,
            mmsObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(mmsObserver)
        }
    }

    // Ordered attachment list derived from the unified timeline (rather than
    // a separately-loaded list) so the media viewer's index still lines up
    // with what's actually rendered.
    val threadAttachments = remember(chatMessages) {
        chatMessages.mapNotNull { it.attachment }
    }

    var searchText by remember { mutableStateOf("") }
    val filteredMessages = if (searchText.isEmpty()) {
        chatMessages
    } else {
        chatMessages.filter {
            it.text.contains(searchText, ignoreCase = true)
        }
    }


    // Mark-as-read keys off threadId (not phoneNumber) so it matches exactly
    // the rows HomeScreen's ContentObserver-driven `messages` prop is for -
    // ChatScreen no longer refetches messages itself; the observer in
    // HomeScreen is the single source of truth for reloading on new SMS.
    LaunchedEffect(threadId) {

        withContext(Dispatchers.IO) {
            SmsRepository.markThreadAsRead(context, threadId)
        }

        // notify inbox to refresh
        context.sendBroadcast(Intent("SMS_INBOX_UPDATED"))
    }

    var firstLoad by remember { mutableStateOf(true) }

    // The ad banner loads asynchronously and grows the bottom bar after the
    // initial layout/scroll, shrinking the LazyColumn's viewport without a
    // compensating re-scroll. Re-running the scroll-to-bottom effect when the
    // banner appears keeps the last message from ending up hidden behind it.
    val bannerVisible = isPersonalChat &&
        RemoteConfigManager.chatBannerEnabled() &&
        ChatBannerAdManager.bannerAdState.value != null

    LaunchedEffect(chatMessages.size, bannerVisible) {

        if (chatMessages.isNotEmpty()) {

            if (firstLoad) {
                listState.scrollToItem(chatMessages.lastIndex)
                firstLoad = false
            } else {
                listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }
    }


    // Reload-on-entry, then a 30s (Google's published minimum banner refresh
    // interval) auto-refresh loop for as long as this chat stays open - one
    // coroutine, not two competing timers, so there's no separate clock that
    // could fire close together with the reload-on-entry call: the first
    // iteration below IS the reload-on-entry, every later one is 30s after
    // the previous. Cancelled automatically when this ChatScreen instance
    // leaves composition (chat closed, or a different chat opened), same as
    // the equivalent effect in HomeScreen's Scaffold - it does not keep
    // running in the background after the user leaves this chat.
    LaunchedEffect(Unit) {
        while (true) {
            ChatBannerAdManager.loadBanner(activity)
            Log.d("CHAT_BANNER", "loadBanner called")
            delay(30_000)
        }
    }

    LaunchedEffect(Unit) {

        ChatBackAdManager.load(activity)

    }

    Scaffold(
        modifier = Modifier.imePadding(),

        ////////////////////////////////////////////////////////
        // 🔵 TOP BAR
        ////////////////////////////////////////////////////////

        topBar = {

            Column {
                TopAppBar(

                    // Solid primary-blue chrome for both Personal and service
                    // (OTP/Transaction/Offers) threads - same visual language,
                    // only the subtitle below distinguishes the two.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PrimaryBlue
                    ),

                    navigationIcon = {

                        IconButton(
                            onClick = {

                                ChatBackAdManager.onClick(activity) {

                                    onBackClick()

                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {

                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    },

                    title = {

                        // 🔎 SEARCH MODE
                        if (isSearching) {

                            BasicTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                decorationBox = { innerTextField ->
                                    if (searchText.isEmpty()) {
                                        Text(
                                            text = "Search messages",
                                            color = Color.Gray
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                        }
                        // 📞 NEW CHAT SCREEN
                        else if (contactName.isEmpty()) {

                            var phone by remember { mutableStateOf("") }

                            TextField(
                                value = phone,
                                onValueChange = { phone = it },
                                placeholder = { Text("Enter phone number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // 💬 NORMAL CHAT TITLE
                        else {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onContactClick() }
                            ) {

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {

                                    Text(
                                        text = contactName.trim().firstOrNull()?.uppercase() ?: "#",
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = contactName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = GeneralSansMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        // Service senders are automated/one-directional - no
                                        // presence concept, so show what kind of sender this is.
                                        text = if (isPersonalChat) "Active now" else "Automated sender",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontFamily = GeneralSansMedium
                                    )
                                }
                            }
                        }
                    },

                    actions = {

                        // Hide buttons for company/service SMS
                        if (!isServiceSender(phoneNumber)) {

                            IconButton(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:$phoneNumber")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isSearching = !isSearching
                                    if (!isSearching) searchText = ""
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search_black),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            }

                            Box {

                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more),
                                        contentDescription = "More",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {

                                    DropdownMenuItem(
                                        text = { Text("Contact info") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onContactClick()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Shared media") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onOpenSharedMedia()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(if (isMuted) "Unmute notifications" else "Mute notifications") },
                                        onClick = {
                                            showOverflowMenu = false
                                            val newMuted = !isMuted
                                            isMuted = newMuted
                                            PreferenceManager.setNotificationsMuted(context, phoneNumber, newMuted)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Block number", color = Color(0xFFE24B4A)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    SmsRepository.blockThread(context, phoneNumber)
                                                }
                                            }
                                            onBlockContact()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Delete conversation", color = Color(0xFFE24B4A)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },

        ////////////////////////////////////////////////////////
        // 🔵 BOTTOM BAR
        ////////////////////////////////////////////////////////

        bottomBar = {

            Column {

                if (isServiceSender(phoneNumber)) {

                    // Non-interactive placeholder shaped like the new pill input
                    // (same 24dp corner / muted fill as MessageInputBar's text
                    // field) so it reads as "disabled input", not leftover UI -
                    // but with no BasicTextField or send button, since this
                    // sender can never be replied to.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFF1F1F1))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = null,
                                tint = Color(0xFF9AA0AC),
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Can't reply to this short code.",
                                color = Color(0xFF9AA0AC),
                                fontSize = 14.sp,
                                fontFamily = GeneralSansMedium
                            )
                        }
                    }

                } else {

                    MessageInputBar(
                        phoneNumber = phoneNumber,
                        autoFocus = autoFocusInput,
                        onMessageSent = { sentText ->

                            val now = System.currentTimeMillis()
                            val time = java.text.SimpleDateFormat(
                                "hh:mm a",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(now))

                            chatMessages = (chatMessages + ChatMessage(
                                text = sentText,
                                time = time,
                                date = now,
                                isMe = true,
                                // Negative sentinel id - this optimistic bubble isn't backed
                                // by a real provider row yet (that arrives once loadMessages()
                                // reloads), so it can never collide with a real _id.
                                id = -now,
                                deliveryState = DeliveryState.SENT
                            )).toMutableList()
                        }
                    )

                    if (RemoteConfigManager.chatBannerEnabled()) {
                        ChatBannerAdSection()
                    }
                }
            }
        }

    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF0F2F5))
        ) {


            ////////////////////////////////////////////////////////
            // 🔵 MESSAGE LIST
            ////////////////////////////////////////////////////////

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                itemsIndexed(filteredMessages) { index, message ->

                    val showDateHeader =
                        index == 0 ||
                                !isSameDay(
                                    filteredMessages[index - 1].date,
                                    message.date
                                )

                    if (showDateHeader) {
                        DateSeparator(message.date)
                    }

                    ChatBubble(
                        message = message,
                        isPersonalChat = isPersonalChat,
                        onMediaClick = { attachment ->
                            val index = threadAttachments.indexOfFirst { it.uri == attachment.uri }
                            if (index >= 0) onMediaClick(index)
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete conversation?") },
            text = { Text("This will permanently delete all messages in this conversation.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteConversation(context, phoneNumber)
                    onBackClick()
                }) {
                    Text("Delete", color = Color(0xFFE24B4A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

////////////////////////////////////////////////////////
// 🔵 CHAT MESSAGE MODEL
////////////////////////////////////////////////////////

enum class DeliveryState { SENT, DELIVERED, FAILED }

data class ChatMessage(
    val text: String,
    val time: String,
    val date: Long,
    val isMe: Boolean,
    // Defaults to 0L for callers outside loadMessages() that don't have a
    // real SMS provider row (e.g. HomeScreen's inline chat preview) - a
    // message with id 0 simply never has a reaction attached to it.
    val id: Long = 0L,
    val deliveryState: DeliveryState = DeliveryState.SENT,
    // Non-null for MMS-derived stub messages produced by ChatScreen's
    // SMS+MMS merge step - see that comment for why this can't come from
    // loadMessages() itself. text is "" for these.
    val attachment: MediaAttachment? = null
)

////////////////////////////////////////////////////////
// 🔵 DATE SEPARATOR
////////////////////////////////////////////////////////

@Composable
fun DateSeparator(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE2E5EA))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatChatDate(timestamp),
                fontSize = 12.sp,
                color = Color(0xFF5A6070),
                fontFamily = GeneralSansMedium
            )
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 CHAT BUBBLE
////////////////////////////////////////////////////////

@Composable
fun ChatBubble(
    message: ChatMessage,
    isPersonalChat: Boolean = false,
    onMediaClick: (MediaAttachment) -> Unit = {}
) {

    val context = LocalContext.current

    var showReactionPicker by remember(message.id) { mutableStateOf(false) }
    var reaction by remember(message.id) {
        // Service (OTP/Transaction/Offers) threads never support reactions -
        // ignore any stale reaction that may have been set before this was scoped.
        mutableStateOf(if (isPersonalChat) PreferenceManager.getReaction(context, message.id) else null)
    }

    val widthFraction = if (isPersonalChat) 0.78f else 0.75f
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * widthFraction).dp

    // Service (OTP/Transaction/Offers) received bubbles now share Personal
    // chat's sharp "tail" corner for visual consistency between the two designs.
    val tailCorner = 0.dp

    // OTP/Transaction/Offers threads are one-directional (never repliable), so
    // only received messages can carry a copyable code; reuses Part 3's detection.
    // Media bubbles carry no text, so this is naturally always null for them.
    val copyableCode = remember(message.text, isPersonalChat, message.isMe) {
        if (!isPersonalChat && !message.isMe) extractCopyableCode(message.text) else null
    }

    val isMedia = message.attachment != null

    // Media bubbles use a rounder 20dp/6dp-tail corner pattern (distinct from
    // the 14dp/0dp-tail text bubble shape) per the media-viewer spec.
    val textSentShape = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomEnd = tailCorner,
        bottomStart = 14.dp
    )
    val textReceivedShape = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomEnd = 14.dp,
        bottomStart = tailCorner
    )
    val mediaSentShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 6.dp,
        bottomStart = 20.dp
    )
    val mediaReceivedShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 20.dp,
        bottomStart = 6.dp
    )
    val bubbleShape = if (isMedia) {
        if (message.isMe) mediaSentShape else mediaReceivedShape
    } else {
        if (message.isMe) textSentShape else textReceivedShape
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement =
            if (message.isMe) Arrangement.End else Arrangement.Start
    ) {

        Column(
            horizontalAlignment =
                if (message.isMe) Alignment.End else Alignment.Start
        ) {

            Box(
                modifier = Modifier
                    .widthIn(max = if (isMedia) 220.dp else maxBubbleWidth)
                    .clip(bubbleShape)
                    .then(
                        if (!message.isMe && !isMedia)
                            Modifier.border(0.5.dp, Color(0xFFDDDDDD), bubbleShape)
                        else
                            Modifier
                    )
                    .then(
                        if (!isMedia)
                            Modifier.background(if (message.isMe) PrimaryBlue else Color.White)
                        else
                            Modifier
                    )
                    .then(
                        // Reactions only make sense on real person-to-person
                        // conversations - never attach the long-press gesture
                        // for OTP/Transaction/Offers (service sender) threads.
                        if (isPersonalChat)
                            Modifier.pointerInput(message.id) {
                                detectTapGestures(onLongPress = { showReactionPicker = true })
                            }
                        else
                            Modifier
                    )
                    .then(
                        if (!isMedia)
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        else
                            Modifier
                    )
            ) {

                if (isMedia) {

                    // 🔵 MMS ATTACHMENT BUBBLE - see the SMS+MMS merge point
                    // comment above for how this stub ChatMessage was built.
                    MmsThumbnail(
                        attachment = message.attachment!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { onMediaClick(message.attachment) },
                        sampleSize = 2
                    )

                } else {

                    Text(
                        text = if (copyableCode != null) {
                            buildAnnotatedString {
                                val start = message.text.indexOf(copyableCode)
                                if (start < 0) {
                                    append(message.text)
                                } else {
                                    append(message.text.substring(0, start))
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(copyableCode)
                                    }
                                    append(message.text.substring(start + copyableCode.length))
                                }
                            }
                        } else {
                            AnnotatedString(message.text)
                        },
                        color = if (message.isMe) Color.White else Color(0xFF1A1A1A),
                        fontSize = 15.sp,
                        fontFamily = GeneralSansMedium
                    )
                }

                // 🔵 REACTION PICKER (long-press) - local-only annotation, see
                // PreferenceManager.setReaction for why this never leaves this device.
                if (showReactionPicker) {

                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = IntOffset(0, -80),
                        onDismissRequest = { showReactionPicker = false }
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(0.5.dp, Color(0xFFDDDDDD), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("❤️", "👍", "😂", "😮").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp,
                                    modifier = Modifier
                                        .clickable {
                                            PreferenceManager.setReaction(context, message.id, emoji)
                                            reaction = emoji
                                            showReactionPicker = false
                                        }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 🔵 REACTION CHIP - tap to clear
            if (reaction != null) {

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F1F1))
                        .clickable {
                            PreferenceManager.clearReaction(context, message.id)
                            reaction = null
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = reaction ?: "",
                        fontSize = 12.sp
                    )
                }
            }

            if (copyableCode != null) {

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBlue.copy(alpha = 0.1f))
                        .clickable { copyCodeToClipboard(context, copyableCode) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy code",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        fontFamily = GeneralSansMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = if (message.isMe) 0.dp else 4.dp,
                    end = if (message.isMe) 4.dp else 0.dp
                )
            ) {
                Text(
                    text = message.time,
                    fontSize = 11.sp,
                    color = Color(0xFF8A8A8A),
                    fontFamily = GeneralSansMedium
                )
                if (message.isMe) {
                    Spacer(modifier = Modifier.width(3.dp))
                    when (message.deliveryState) {
                        DeliveryState.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Failed to send",
                                tint = Color(0xFFE24B4A),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        DeliveryState.DELIVERED -> {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = Color(0xFF3E6AE1),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        DeliveryState.SENT -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 MESSAGE INPUT BAR
////////////////////////////////////////////////////////

@Composable
fun MessageInputBar(
    phoneNumber: String,
    onMessageSent: (String) -> Unit,
    autoFocus: Boolean = false
) {

    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column {

        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E0E0))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = {
                    // No emoji-picker library in this project - let the
                    // user's own keyboard (Gboard etc.) handle emoji via its
                    // built-in emoji key, we just need to bring it up.
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEmotions,
                    contentDescription = "Emoji",
                    tint = Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF1F1F1))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.Black,
                        fontFamily = GeneralSansMedium
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = "Message",
                                color = Color.Gray,
                                fontFamily = GeneralSansMedium
                            )
                        }
                        innerTextField()
                    }
                )
            }

            IconButton(
                onClick = { },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotEmpty()) PrimaryBlue else Color(0xFFBFC8E8))
                    .clickable(enabled = text.isNotEmpty()) {
                        val messageToSend = text
                        text = ""
                        sendSms(context, phoneNumber, messageToSend)
                        onMessageSent(messageToSend)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 CHAT BANNER AD
////////////////////////////////////////////////////////

@Composable
fun ChatBannerAdSection() {

    val bannerView = ChatBannerAdManager.bannerAdState.value

    if (bannerView != null) {

        // Same stable-container + update pattern as HomeScreen's
        // BannerAdSection - a raw AdView can't be rebound in place, so a
        // reload always means a new AdView instance; without this, a fresh
        // banner would silently never replace the stale one once reload
        // stops relying on a full ChatScreen remount.
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
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        )
    } else {
        AdShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            variant = AdShimmerVariant.BANNER
        )
    }
}

fun isServiceMessage(sender: String): Boolean {
    return sender.contains("-") || sender.any { it.isLetter() }
}

fun isServiceSender(sender: String): Boolean {
    return sender.any { it.isLetter() } && !sender.any { it.isDigit() }
}

fun sendSms(context: Context, phoneNumber: String, message: String) {

    try {

        val smsManager = SmsManager.getDefault()

        smsManager.sendTextMessage(
            phoneNumber,
            null,
            message,
            null,
            null
        )

        // Insert message into SMS database
        val values = android.content.ContentValues().apply {
            put("address", phoneNumber)
            put("body", message)
            put("date", System.currentTimeMillis())
            put("type", 2) // 2 = sent
        }

        context.contentResolver.insert(
            android.net.Uri.parse("content://sms/sent"),
            values
        )

        // Broadcast updates
        context.sendBroadcast(Intent("SMS_INBOX_UPDATED"))

        val chatIntent = Intent("NEW_SMS_RECEIVED")
        chatIntent.putExtra("sender", phoneNumber)
        chatIntent.putExtra("message", message)
        context.sendBroadcast(chatIntent)

        Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {

        Toast.makeText(
            context,
            "SMS failed: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

////////////////////////////////////////////////////////
// 🔵 DELETE CONVERSATION
////////////////////////////////////////////////////////

// Deletes by address rather than thread_id (unlike HomeScreen's own
// deleteMessages()) since that's the identifier this screen is given.
fun deleteConversation(context: Context, phoneNumber: String) {

    val normalized = phoneNumber.takeLast(10)

    context.contentResolver.delete(
        android.net.Uri.parse("content://sms"),
        "address LIKE ? OR address LIKE ?",
        arrayOf("%$normalized%", "%$phoneNumber%")
    )

    context.sendBroadcast(Intent("SMS_INBOX_UPDATED"))
}

fun formatChatDate(timestamp: Long): String {

    val today = System.currentTimeMillis()
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis

    return when {
        isSameDay(timestamp, today) -> "Today"
        isSameDay(timestamp, yesterday) -> "Yesterday"
        else -> java.text.SimpleDateFormat(
            "MMM d, yyyy",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
    }
}

fun isSameDay(t1: Long, t2: Long): Boolean {

    val c1 = java.util.Calendar.getInstance()
    val c2 = java.util.Calendar.getInstance()

    c1.timeInMillis = t1
    c2.timeInMillis = t2

    return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
            c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
}
