package com.sms.textmessages.messenger.ui.newgroup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ui.newchat.Contact
import com.sms.textmessages.messenger.ui.newchat.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

private val AccentBlue = Color(0xFF3E6AE1)

////////////////////////////////////////////////////////
// 🔵 NEW GROUP SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onCreateGroup: (List<String>) -> Unit
) {

    val context = LocalContext.current

    // Hosted as a plain composable overlay (see HomeScreen's openNewGroup flag),
    // not a nav-graph destination, so this screen must intercept system back
    // itself - same pattern as SettingsScreen/ArchivedScreen/BlockedNumbersScreen.
    // Without this, back falls through to the Activity's default handling and
    // exits the app instead of returning to the inbox.
    BackHandler {
        onBack()
    }

    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var selectedPhones by remember { mutableStateOf(setOf<String>()) }
    var selectedContacts by remember { mutableStateOf(listOf<Contact>()) }

    LaunchedEffect(Unit) {
        // ContactsRepository already owns contact-loading/caching - reused
        // as-is rather than duplicating that query here.
        contacts = withContext(Dispatchers.IO) { ContactsRepository.loadContacts(context) }
    }

    val filteredContacts = remember(contacts, searchText) {
        if (searchText.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                    it.phone.contains(searchText)
            }
        }
    }

    fun toggleContact(contact: Contact) {
        if (selectedPhones.contains(contact.phone)) {
            selectedPhones = selectedPhones - contact.phone
            selectedContacts = selectedContacts.filter { it.phone != contact.phone }
        } else {
            selectedPhones = selectedPhones + contact.phone
            selectedContacts = selectedContacts + contact
        }
    }

    val canCreate = selectedPhones.size >= 2

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New group",
                        fontSize = 17.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },

        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (canCreate) AccentBlue else Color(0xFFEDEDED))
                        .clickable(enabled = canCreate) {
                            onCreateGroup(selectedPhones.toList())
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create group",
                        fontSize = 16.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = if (canCreate) Color.White else Color.Gray
                    )
                }
            }
        }

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {

            if (selectedContacts.isNotEmpty()) {

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedContacts, key = { it.phone }) { contact ->

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = contact.name.ifEmpty { contact.phone },
                                fontSize = 13.sp,
                                fontFamily = GeneralSansMedium,
                                color = AccentBlue
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "×",
                                fontSize = 16.sp,
                                fontFamily = GeneralSansSemiBold,
                                color = AccentBlue,
                                modifier = Modifier.clickable { toggleContact(contact) }
                            )
                        }
                    }
                }
            }

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search contacts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(30.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF1F3F6),
                    unfocusedContainerColor = Color(0xFFF1F3F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredContacts, key = { it.phone }) { contact ->

                    val isSelected = selectedPhones.contains(contact.phone)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleContact(contact) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.trim().firstOrNull()?.uppercase()?.toString() ?: "#",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {

                            Text(
                                text = contact.name,
                                fontSize = 16.sp,
                                fontFamily = GeneralSansSemiBold
                            )

                            Text(
                                text = contact.phone,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                fontFamily = GeneralSansMedium
                            )
                        }

                        // Same circular-badge visual weight as Part 1's
                        // contact-info quick-action icon buttons.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AccentBlue else Color(0xFFF1F1F1))
                                .then(
                                    if (!isSelected)
                                        Modifier.border(1.dp, Color(0xFFDDDDDD), CircleShape)
                                    else
                                        Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
