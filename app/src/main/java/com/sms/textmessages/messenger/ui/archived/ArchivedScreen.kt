package com.sms.textmessages.messenger.ui.archived

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.SmsThread
import com.sms.textmessages.messenger.ui.home.generateColorFromName
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.ui.theme.PrimaryBlue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

////////////////////////////////////////////////////////
// 🔵 ARCHIVED SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenChat: (phoneNumber: String, threadId: Long) -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dao = remember { AppDatabase.getDatabase(context).threadDao() }

    // Single reactive source of truth (ThreadDao.getArchivedThreadsFlow, WHERE
    // archived = 1) - re-emits automatically when a thread is archived/
    // unarchived anywhere in the app, no manual refresh needed.
    val archivedEntities by dao.getArchivedThreadsFlow().collectAsStateWithLifecycle(initialValue = null)
    val archivedThreads = remember(archivedEntities) {
        archivedEntities?.map {
            SmsThread(
                phone = it.phone,
                lastMessage = it.lastMessage,
                date = it.date,
                isRead = it.isRead,
                threadId = it.threadId,
                pinned = it.pinned
            )
        } ?: emptyList()
    }
    val isLoading = archivedEntities == null

    var contactNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedThreadIds by remember { mutableStateOf(setOf<Long>()) }

    BackHandler {
        if (selectedThreadIds.isNotEmpty()) {
            selectedThreadIds = emptySet()
        } else {
            onBack()
        }
    }

    // Reused by both the bulk top-bar action and (once selected) a single-item
    // unarchive - writes through SmsRepository so PreferenceManager and the
    // Room row (which drives this screen's list) stay in sync.
    fun unarchiveThreads(threadIds: Set<Long>) {
        val phones = archivedThreads
            .filter { threadIds.contains(it.threadId) }
            .map { it.phone }

        scope.launch {
            withContext(Dispatchers.IO) {
                phones.forEach { SmsRepository.unarchiveThread(context, it) }
            }
        }
    }

    LaunchedEffect(archivedThreads) {
        val names = withContext(Dispatchers.IO) {
            archivedThreads.associate { it.phone to getContactName(context, it.phone).ifEmpty { it.phone } }
        }
        contactNames = names
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    if (selectedThreadIds.isNotEmpty()) {
                        Text(
                            text = "${selectedThreadIds.size} Selected",
                            fontSize = 20.sp,
                            fontFamily = GeneralSansSemiBold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Archived",
                            fontSize = 20.sp,
                            fontFamily = GeneralSansSemiBold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (selectedThreadIds.isNotEmpty()) {
                        IconButton(
                            onClick = { selectedThreadIds = emptySet() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onBack
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (selectedThreadIds.isNotEmpty()) {

                        IconButton(onClick = {
                            selectedThreadIds = archivedThreads.map { it.threadId }.toSet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            unarchiveThreads(selectedThreadIds)
                            selectedThreadIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Unarchive",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3E6AE1)
                )
            )
        }

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {

            if (isLoading) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3E6AE1))
                }

            } else if (archivedThreads.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No archived conversations",
                        fontSize = 15.sp,
                        fontFamily = GeneralSansMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Conversations you archive will show up here.",
                        fontSize = 13.sp,
                        fontFamily = GeneralSansMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(archivedThreads, key = { it.threadId }) { thread ->
                        ArchivedConversationRow(
                            thread = thread,
                            contactName = contactNames[thread.phone] ?: thread.phone,
                            isSelected = selectedThreadIds.contains(thread.threadId),
                            onClick = {
                                if (selectedThreadIds.isNotEmpty()) {
                                    selectedThreadIds =
                                        if (selectedThreadIds.contains(thread.threadId))
                                            selectedThreadIds - thread.threadId
                                        else
                                            selectedThreadIds + thread.threadId
                                } else {
                                    onOpenChat(thread.phone, thread.threadId)
                                }
                            },
                            onLongClick = {
                                selectedThreadIds = selectedThreadIds + thread.threadId
                            }
                        )
                    }
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 ARCHIVED CONVERSATION ROW (mirrors HomeScreen's MessageItem layout)
////////////////////////////////////////////////////////

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedConversationRow(
    thread: SmsThread,
    contactName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {

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
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val circleColor = remember(contactName) {
            generateColorFromName(contactName)
        }

        val initial = contactName.trim().firstOrNull()

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial?.uppercase()?.toString() ?: "#",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {

            Text(
                text = contactName,
                fontSize = 17.sp,
                fontFamily = GeneralSansSemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = thread.lastMessage,
                fontSize = 15.sp,
                fontFamily = GeneralSansMedium,
                color = Color(0xFF5F6368),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatArchivedTime(thread.date),
            fontSize = 13.sp,
            fontFamily = GeneralSansMedium,
            color = Color(0xFF757575)
        )
    }
}

private fun formatArchivedTime(timestamp: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
