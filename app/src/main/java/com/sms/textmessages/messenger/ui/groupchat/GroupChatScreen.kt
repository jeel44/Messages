package com.sms.textmessages.messenger.ui.groupchat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ui.chat.DateSeparator
import com.sms.textmessages.messenger.ui.chat.MessageInputBar
import com.sms.textmessages.messenger.ui.chat.isSameDay
import com.sms.textmessages.messenger.ui.chat.sendSms
import com.sms.textmessages.messenger.ui.home.generateColorFromName
import com.sms.textmessages.messenger.ui.home.getContactName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

private val AccentBlue = Color(0xFF3E6AE1)

data class GroupChatMessage(
    val text: String,
    val time: String,
    val date: Long,
    val isMe: Boolean,
    val senderNumber: String
)

////////////////////////////////////////////////////////
// 🔵 GROUP CHAT SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    participantNumbers: List<String>,
    onBackClick: () -> Unit
) {

    // Callers (e.g. AppNavigation's group_chat/{groupId} route) resolve the
    // participant list from Room asynchronously, so this can render once with
    // an empty list before the real participants arrive.
    if (participantNumbers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()

    var messages by remember(groupId) { mutableStateOf<List<GroupChatMessage>>(emptyList()) }
    var contactNames by remember(participantNumbers) { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(participantNumbers) {
        contactNames = withContext(Dispatchers.IO) {
            participantNumbers.associateWith { number ->
                getContactName(context, number).ifEmpty { number }
            }
        }
    }

    LaunchedEffect(groupId, participantNumbers) {
        messages = withContext(Dispatchers.IO) {
            loadGroupMessages(context, participantNumbers)
        }
    }

    // Cheap live-update: any change to content://sms re-runs the same
    // fallback query. There's no per-group targeting at the provider level
    // (see loadGroupMessages), so this re-queries on every SMS table change.
    DisposableEffect(participantNumbers) {

        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                CoroutineScope(Dispatchers.IO).launch {
                    val updated = loadGroupMessages(context, participantNumbers)
                    withContext(Dispatchers.Main) { messages = updated }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            android.net.Uri.parse("content://sms"),
            true,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val displayNames = participantNumbers.map { contactNames[it] ?: it }
    val titleText = if (displayNames.size > 3) {
        displayNames.take(3).joinToString(", ") + " +${displayNames.size - 3}"
    } else {
        displayNames.joinToString(", ")
    }

    Scaffold(
        modifier = Modifier.imePadding(),

        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AccentBlue
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = titleText,
                                fontSize = 15.sp,
                                fontFamily = GeneralSansMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${participantNumbers.size} participants",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = GeneralSansMedium
                            )
                        }
                    }
                }
            )
        },

        bottomBar = {
            // MessageInputBar targets a single phoneNumber and sends via its
            // own internal sendSms() call on tap - reused exactly as-is (not
            // restyled), pointed at the first participant. onMessageSent below
            // then fans the same text out to the rest of the group, so every
            // participant still gets a real individual SMS.
            MessageInputBar(
                phoneNumber = participantNumbers.first(),
                onMessageSent = { sentText ->

                    // 🔶 SCOPE NOTE: plain SMS has no multi-recipient concept.
                    // A real "group message" on Android is an MMS thread, backed
                    // by content://mms + content://mms-sms/conversations, with
                    // each message's recipients resolved per-row from
                    // content://mms/{id}/addr. That parsing surface is
                    // materially larger than this app's existing SMS-only
                    // querying (loadMessages in ChatScreen.kt), so this initial
                    // implementation intentionally falls back to N individual
                    // 1:1 SMS sends instead of true MMS group threading.
                    participantNumbers.drop(1).forEach { number ->
                        sendSms(context, number, sentText)
                    }

                    val now = System.currentTimeMillis()
                    val time = java.text.SimpleDateFormat(
                        "hh:mm a",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(now))

                    messages = messages + GroupChatMessage(
                        text = sentText,
                        time = time,
                        date = now,
                        isMe = true,
                        senderNumber = "me"
                    )
                }
            )
        }

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF0F2F5))
        ) {

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                itemsIndexed(messages) { index, message ->

                    val showDateHeader =
                        index == 0 || !isSameDay(messages[index - 1].date, message.date)

                    if (showDateHeader) {
                        DateSeparator(message.date)
                    }

                    GroupChatBubble(
                        message = message,
                        senderName = contactNames[message.senderNumber] ?: message.senderNumber
                    )
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 GROUP CHAT BUBBLE (mirrors ChatBubble's visual language)
////////////////////////////////////////////////////////

@Composable
private fun GroupChatBubble(message: GroupChatMessage, senderName: String) {

    val sentShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 6.dp,
        bottomStart = 20.dp
    )
    val receivedShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 20.dp,
        bottomStart = 6.dp
    )
    val bubbleShape = if (message.isMe) sentShape else receivedShape

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
    ) {

        Column(
            horizontalAlignment = if (message.isMe) Alignment.End else Alignment.Start
        ) {

            if (!message.isMe) {
                Text(
                    text = senderName,
                    fontSize = 11.sp,
                    fontFamily = GeneralSansSemiBold,
                    color = generateColorFromName(senderName),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(if (message.isMe) AccentBlue else Color(0xFFEDEDED))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isMe) Color.White else Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    fontFamily = GeneralSansMedium
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = message.time,
                fontSize = 11.sp,
                color = Color(0xFF8A8A8A),
                fontFamily = GeneralSansMedium,
                modifier = Modifier.padding(
                    start = if (message.isMe) 0.dp else 4.dp,
                    end = if (message.isMe) 4.dp else 0.dp
                )
            )
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 LOAD GROUP MESSAGES (SMS fallback, not real MMS threading)
////////////////////////////////////////////////////////

// 🔶 SCOPE NOTE: a genuine MMS group thread lives in content://mms +
// content://mms-sms/conversations, with recipients resolved per-message from
// content://mms/{id}/addr - a materially bigger parsing surface than plain
// SMS. This fallback instead unions each participant's individual 1:1 SMS
// thread from content://sms, sorted by date. It's enough to show something
// real for an initial implementation, but it is NOT a genuine MMS group
// conversation (e.g. a reply from one participant is really just their own
// private SMS reply, not a provider-level group message).
private fun loadGroupMessages(context: Context, participantNumbers: List<String>): List<GroupChatMessage> {

    if (participantNumbers.isEmpty()) return emptyList()

    val list = mutableListOf<GroupChatMessage>()

    val uri = android.net.Uri.parse("content://sms")

    val selectionParts = mutableListOf<String>()
    val selectionArgs = mutableListOf<String>()

    participantNumbers.forEach { number ->
        val normalized = number.takeLast(10)
        selectionParts.add("address LIKE ?")
        selectionArgs.add("%$normalized%")
    }

    val selection = selectionParts.joinToString(" OR ")

    val cursor = context.contentResolver.query(
        uri,
        null,
        selection,
        selectionArgs.toTypedArray(),
        "date ASC"
    )

    cursor?.use {

        val addressIndex = it.getColumnIndex("address")
        val bodyIndex = it.getColumnIndex("body")
        val typeIndex = it.getColumnIndex("type")
        val dateIndex = it.getColumnIndex("date")

        while (it.moveToNext()) {

            val address = it.getString(addressIndex) ?: ""
            val body = it.getString(bodyIndex) ?: ""
            val type = it.getInt(typeIndex)
            val date = it.getLong(dateIndex)

            val isMe = type == 2

            val time = java.text.SimpleDateFormat(
                "hh:mm a",
                java.util.Locale.getDefault()
            ).format(java.util.Date(date))

            list.add(
                GroupChatMessage(
                    text = body,
                    time = time,
                    date = date,
                    isMe = isMe,
                    senderNumber = if (isMe) "me" else address
                )
            )
        }
    }

    return list.sortedBy { it.date }
}
