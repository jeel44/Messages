package com.sms.textmessages.messenger.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.ui.theme.PrimaryBlue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private data class BlockedEntry(
    val phoneNumber: String,
    val displayName: String
)

////////////////////////////////////////////////////////
// 🔵 BLOCKED NUMBERS SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dao = remember { AppDatabase.getDatabase(context).threadDao() }

    // Single reactive source of truth (ThreadDao.getBlockedThreadsFlow, WHERE
    // blocked = 1) - re-emits automatically when a number is blocked/unblocked
    // anywhere in the app, no manual refresh needed.
    val blockedThreads by dao.getBlockedThreadsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var blockedEntries by remember { mutableStateOf<List<BlockedEntry>>(emptyList()) }
    var selectedPhoneNumbers by remember { mutableStateOf(setOf<String>()) }

    BackHandler {
        if (selectedPhoneNumbers.isNotEmpty()) {
            selectedPhoneNumbers = emptySet()
        } else {
            onBack()
        }
    }

    // Reused by both the bulk top-bar action and the single-row "Unblock"
    // button - writes through SmsRepository so PreferenceManager and the Room
    // row (which drives this screen's list) stay in sync.
    fun unblockNumbers(phoneNumbers: Set<String>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                phoneNumbers.forEach { SmsRepository.unblockThread(context, it) }
            }
        }
    }

    LaunchedEffect(blockedThreads) {
        val entries = withContext(Dispatchers.IO) {
            blockedThreads.map { thread ->
                BlockedEntry(
                    phoneNumber = thread.phone,
                    displayName = getContactName(context, thread.phone).ifEmpty { thread.phone }
                )
            }
        }
        blockedEntries = entries
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    if (selectedPhoneNumbers.isNotEmpty()) {
                        Text(
                            text = "${selectedPhoneNumbers.size} Selected",
                            fontSize = 20.sp,
                            fontFamily = GeneralSansSemiBold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Blocked numbers",
                            fontSize = 20.sp,
                            fontFamily = GeneralSansSemiBold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (selectedPhoneNumbers.isNotEmpty()) {
                        IconButton(
                            onClick = { selectedPhoneNumbers = emptySet() }
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
                    if (selectedPhoneNumbers.isNotEmpty()) {

                        IconButton(onClick = {
                            selectedPhoneNumbers = blockedEntries.map { it.phoneNumber }.toSet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            unblockNumbers(selectedPhoneNumbers)
                            selectedPhoneNumbers = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Unblock",
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

            if (blockedEntries.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No blocked numbers",
                        fontSize = 15.sp,
                        fontFamily = GeneralSansMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Blocked numbers can't call or text you.",
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
                    items(blockedEntries, key = { it.phoneNumber }) { entry ->
                        BlockedNumberRow(
                            entry = entry,
                            isSelected = selectedPhoneNumbers.contains(entry.phoneNumber),
                            isSelectionMode = selectedPhoneNumbers.isNotEmpty(),
                            onClick = {
                                if (selectedPhoneNumbers.isNotEmpty()) {
                                    selectedPhoneNumbers =
                                        if (selectedPhoneNumbers.contains(entry.phoneNumber))
                                            selectedPhoneNumbers - entry.phoneNumber
                                        else
                                            selectedPhoneNumbers + entry.phoneNumber
                                }
                            },
                            onLongClick = {
                                selectedPhoneNumbers = selectedPhoneNumbers + entry.phoneNumber
                            },
                            onUnblock = {
                                unblockNumbers(setOf(entry.phoneNumber))
                            }
                        )
                    }
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 BLOCKED NUMBER ROW
////////////////////////////////////////////////////////

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockedNumberRow(
    entry: BlockedEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUnblock: () -> Unit
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
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = entry.displayName,
                fontSize = 16.sp,
                fontFamily = GeneralSansSemiBold,
                color = Color.Black,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Blocked",
                fontSize = 12.sp,
                fontFamily = GeneralSansMedium,
                color = Color.Gray
            )
        }

        if (!isSelectionMode) {
            TextButton(onClick = onUnblock) {
                Text(
                    text = "Unblock",
                    fontSize = 14.sp,
                    fontFamily = GeneralSansSemiBold,
                    color = AccentBlue
                )
            }
        }
    }
}
