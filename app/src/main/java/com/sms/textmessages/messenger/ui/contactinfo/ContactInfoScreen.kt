package com.sms.textmessages.messenger.ui.contactinfo

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.generateColorFromName
import com.sms.textmessages.messenger.utils.PreferenceManager
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

////////////////////////////////////////////////////////
// 🔵 CONTACT INFO SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    contactName: String,
    phoneNumber: String,
    onBack: () -> Unit,
    onBlockContact: () -> Unit,
    onOpenSharedMedia: () -> Unit,
    onSearchInChat: () -> Unit = {}
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notificationsEnabled by remember(phoneNumber) {
        mutableStateOf(!PreferenceManager.isNotificationsMuted(context, phoneNumber))
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Contact info",
                        fontSize = 20.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
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

            ////////////////////////////////////////////////////////
            // 🔵 AVATAR + NAME + NUMBER
            ////////////////////////////////////////////////////////

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val avatarColor = remember(contactName) {
                    generateColorFromName(contactName)
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contactName.trim().firstOrNull()?.uppercase() ?: "#",
                        fontSize = 28.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = contactName,
                    fontSize = 20.sp,
                    fontFamily = GeneralSansSemiBold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = phoneNumber,
                    fontSize = 13.sp,
                    fontFamily = GeneralSansMedium,
                    color = Color.Gray
                )
            }

            ////////////////////////////////////////////////////////
            // 🔵 QUICK ACTIONS
            ////////////////////////////////////////////////////////

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                QuickActionButton(
                    icon = Icons.Default.Call,
                    contentDescription = "Call"
                ) {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                    }
                    context.startActivity(intent)
                }

                QuickActionButton(
                    icon = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    enabled = false
                ) {
                    Toast.makeText(
                        context,
                        "Video calling requires a separate app",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                QuickActionButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search in chat"
                ) {
                    onSearchInChat()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ////////////////////////////////////////////////////////
            // 🔵 SETTINGS-STYLE ROWS
            ////////////////////////////////////////////////////////

            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                ContactInfoRow(
                    icon = Icons.Default.Notifications,
                    text = "Notifications",
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                notificationsEnabled = enabled
                                PreferenceManager.setNotificationsMuted(context, phoneNumber, !enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentBlue
                            )
                        )
                    },
                    onClick = {
                        val newEnabled = !notificationsEnabled
                        notificationsEnabled = newEnabled
                        PreferenceManager.setNotificationsMuted(context, phoneNumber, !newEnabled)
                    }
                )

                ContactInfoRow(
                    icon = Icons.Default.PermMedia,
                    text = "Shared media",
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onOpenSharedMedia
                )

                ContactInfoRow(
                    icon = Icons.Default.Block,
                    text = "Block contact",
                    textColor = Color(0xFFE24B4A),
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                SmsRepository.blockThread(context, phoneNumber)
                            }
                        }
                        onBlockContact()
                    }
                )
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 QUICK ACTION BUTTON
////////////////////////////////////////////////////////

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F1F1))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) AccentBlue else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 CONTACT INFO ROW (matches SettingsScreen's SettingsItem layout)
////////////////////////////////////////////////////////

@Composable
private fun ContactInfoRow(
    icon: ImageVector,
    text: String,
    textColor: Color = Color.Black,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = CircleShape,
            color = Color(0xFFF1F3F6),
            modifier = Modifier.size(40.dp)
        ) {

            Box(
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (textColor == Color.Black) AccentBlue else textColor
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 16.sp,
            fontFamily = GeneralSansSemiBold,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        trailing()
    }
}
