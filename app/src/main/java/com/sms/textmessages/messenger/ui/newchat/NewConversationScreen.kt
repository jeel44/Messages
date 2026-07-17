package com.sms.textmessages.messenger.ui.newchat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sms.textmessages.messenger.R

data class Contact(
    val name: String,
    val phone: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onStartChat: (String, String) -> Unit
) {

    val currentOnBack by rememberUpdatedState(onBack)

    BackHandler {
        currentOnBack()
    }

    val viewModel: NewChatViewModel = viewModel()

    val contacts by viewModel.filteredContacts.collectAsState(initial = emptyList())

    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(searchText) {
        viewModel.updateSearch(searchText)
    }

    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text(
                        text = "New Conversation",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = FontFamily(Font(R.font.general_sans_bold))
                    )
                },

                navigationIcon = {

                    IconButton(onClick = { onBack() }) {

                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
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
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "To:",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    TextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = {
                            Text("Search contacts or type number")
                        },
                        singleLine = true,

                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(30.dp)),

                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F3F6),
                            unfocusedContainerColor = Color(0xFFF1F3F6),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                if (searchText.isNotEmpty() && contacts.none { it.phone == searchText }) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStartChat(searchText, searchText)
                            }
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3E6AE1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Send message to $searchText",
                            fontSize = 16.sp
                        )
                    }
                }
            }

            if (contacts.isEmpty() && searchText.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {

                    items(
                        items = contacts,
                        key = { it.phone }
                    ) { contact ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStartChat(contact.name, contact.phone)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3E6AE1)),
                                contentAlignment = Alignment.Center
                            ) {

                                Text(
                                    text = contact.name.trim().firstOrNull()?.toString() ?: "#",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {

                                Text(
                                    text = contact.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = contact.phone,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}