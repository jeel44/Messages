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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.ads.ChatBannerAdManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.sms.textmessages.messenger.ui.ads.ChatBackAdManager
import android.telephony.SmsManager
import android.widget.Toast
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.itemsIndexed
import com.sms.textmessages.messenger.ui.home.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.sms.textmessages.messenger.ui.ads.OverlayAdService
import kotlinx.coroutines.withContext


private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_semibold, FontWeight.SemiBold)
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
    messages: List<ChatMessage>,
    onBackClick: () -> Unit
) {

    val context = LocalContext.current
    val activity = context as Activity
    val listState = rememberLazyListState()
    var isSearching by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(messages.toMutableList()) }
    var searchText by remember { mutableStateOf("") }
    val filteredMessages = if (searchText.isEmpty()) {
        chatMessages
    } else {
        chatMessages.filter {
            it.text.contains(searchText, ignoreCase = true)
        }
    }


    LaunchedEffect(phoneNumber) {

        // mark all messages from this sender as read
        val values = android.content.ContentValues().apply {
            put("read", 1)
        }

        context.contentResolver.update(
            android.net.Uri.parse("content://sms/inbox"),
            values,
            "address = ? AND read = 0",
            arrayOf(phoneNumber)
        )

        // reload messages
        chatMessages = loadMessages(context, phoneNumber).toMutableList()

        // notify inbox to refresh
        context.sendBroadcast(Intent("SMS_INBOX_UPDATED"))
    }

    DisposableEffect(phoneNumber) {

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {

                if (intent.action != "NEW_SMS_RECEIVED") return

                val sender = intent.getStringExtra("sender") ?: return

                val normalizedSender = sender.takeLast(10)
                val normalizedChat = phoneNumber.takeLast(10)

                if (normalizedSender == normalizedChat) {

                    CoroutineScope(Dispatchers.IO).launch {

                        kotlinx.coroutines.delay(300)

                        val updated = loadMessages(context, phoneNumber)

                        withContext(Dispatchers.Main) {
                            chatMessages = updated.toMutableList()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter("NEW_SMS_RECEIVED")

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

    var firstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(chatMessages.size) {

        if (chatMessages.isNotEmpty()) {

            if (firstLoad) {
                listState.scrollToItem(chatMessages.lastIndex)
                firstLoad = false
            } else {
                listState.animateScrollToItem(chatMessages.lastIndex)
            }
        }
    }


    LaunchedEffect(Unit) {
        ChatBannerAdManager.loadBanner(activity)
        Log.d("CHAT_BANNER", "loadBanner called")
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

            TopAppBar(

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
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
                            tint = Color.Black
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

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3E6AE1)),
                                contentAlignment = Alignment.Center
                            ) {

                                Text(
                                    text = contactName.trim().firstOrNull()?.uppercase() ?: "#",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = contactName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },

                actions = {

                    // Hide buttons for company/service SMS
                    if (!isServiceSender(phoneNumber)) {

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
                                tint = Color.Black
                            )
                        }

                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more),
                                contentDescription = "More"
                            )
                        }
                    }
                }
            )
        },

        ////////////////////////////////////////////////////////
        // 🔵 BOTTOM BAR
        ////////////////////////////////////////////////////////

        bottomBar = {

            Column {

                if (isServiceSender(phoneNumber)) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    painter = painterResource(R.drawable.ic_warning),
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "Can't reply to this short code.",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontFamily = GeneralSansMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp)) // ⭐ space before ad
                    }

                } else {

                    MessageInputBar(
                        phoneNumber = phoneNumber,
                        onMessageSent = {

                            CoroutineScope(Dispatchers.IO).launch {

                                val updated = loadMessages(context, phoneNumber)

                                withContext(Dispatchers.Main) {
                                    chatMessages = updated.toMutableList()
                                }
                            }
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
        ) {


            ////////////////////////////////////////////////////////
            // 🔵 MESSAGE LIST
            ////////////////////////////////////////////////////////

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false
            ) {

                itemsIndexed(filteredMessages) { index, message ->

                    val showDateHeader =
                        index == 0 ||
                                !isSameDay(
                                    filteredMessages[index - 1].date,
                                    message.date
                                )

                    if (showDateHeader) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = formatChatDate(message.date),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    ChatBubble(message)
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 CHAT MESSAGE MODEL
////////////////////////////////////////////////////////

data class ChatMessage(
    val text: String,
    val time: String,
    val date: Long,
    val isMe: Boolean
)

////////////////////////////////////////////////////////
// 🔵 CHAT BUBBLE
////////////////////////////////////////////////////////

    @Composable
    fun ChatBubble(message: ChatMessage) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement =
                if (message.isMe) Arrangement.End else Arrangement.Start
        ) {

            Column(
                horizontalAlignment =
                    if (message.isMe) Alignment.End else Alignment.Start
            ) {

                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = if (message.isMe) 20.dp else 6.dp,
                                bottomEnd = if (message.isMe) 6.dp else 20.dp
                            )
                        )
                        .background(
                            if (message.isMe)
                                Color(0xFF3E6AE1)
                            else
                                Color(0xFFEDEDED)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {

                    Text(
                        text = message.text,
                        color =
                            if (message.isMe) Color.White else Color.Black,
                        fontSize = 15.sp,
                        fontFamily = GeneralSansMedium
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = message.time,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = GeneralSansMedium,
                    modifier = Modifier.padding(
                        start = if (message.isMe) 0.dp else 6.dp,
                        end = if (message.isMe) 6.dp else 0.dp
                    )
                )
            }
        }
    }

////////////////////////////////////////////////////////
// 🔵 MESSAGE INPUT BAR
////////////////////////////////////////////////////////

    @Composable
    fun MessageInputBar(
        phoneNumber: String,
        onMessageSent: (String) -> Unit
    ) {

        var text by remember { mutableStateOf("") }
        val context = LocalContext.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .imePadding() // ⭐ fixes keyboard overlap
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFF1F1F1))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.Black,
                        fontFamily = GeneralSansMedium
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = "Write a message...",
                                color = Color.Gray,
                                fontFamily = GeneralSansMedium
                            )
                        }
                        innerTextField()
                    }
                )


                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Image(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clickable {

                            if (text.isNotEmpty()) {

                                sendSms(context, phoneNumber, text)

                                CoroutineScope(Dispatchers.IO).launch {

                                    val updated = loadMessages(context, phoneNumber)

                                    withContext(Dispatchers.Main) {
                                        onMessageSent(text)
                                    }
                                }

                                text = ""
                            }
                        }
                )
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

            AndroidView(
                factory = { bannerView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
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
// 🔵 LOAD SMS FROM DATABASE
////////////////////////////////////////////////////////

fun loadMessages(context: Context, phoneNumber: String): List<ChatMessage> {

    val list = mutableListOf<ChatMessage>()

    val uri = android.net.Uri.parse("content://sms")

    val normalized = phoneNumber.takeLast(10)

    val cursor = context.contentResolver.query(
        uri,
        null,
        "address LIKE ? OR address LIKE ?",
        arrayOf("%$normalized%", "%$phoneNumber%"),
        "date ASC"
    )

    cursor?.use {

        val bodyIndex = it.getColumnIndex("body")
        val typeIndex = it.getColumnIndex("type")
        val dateIndex = it.getColumnIndex("date")

        while (it.moveToNext()) {

            val body = it.getString(bodyIndex)
            val type = it.getInt(typeIndex)
            val date = it.getLong(dateIndex)

            val isMe = type == 2

            val time = java.text.SimpleDateFormat(
                "hh:mm a",
                java.util.Locale.getDefault()
            ).format(java.util.Date(date))

            list.add(
                ChatMessage(
                    text = body,
                    time = time,
                    date = date,
                    isMe = isMe
                )
            )

        }
    }

    return list
}

fun formatChatDate(timestamp: Long): String {

    val sdf = java.text.SimpleDateFormat(
        "EEEE, dd MMMM",
        java.util.Locale.getDefault()
    )

    return sdf.format(java.util.Date(timestamp))
}

fun isSameDay(t1: Long, t2: Long): Boolean {

    val c1 = java.util.Calendar.getInstance()
    val c2 = java.util.Calendar.getInstance()

    c1.timeInMillis = t1
    c2.timeInMillis = t2

    return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
            c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
}