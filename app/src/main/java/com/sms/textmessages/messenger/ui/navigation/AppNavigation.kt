package com.sms.textmessages.messenger.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sms.textmessages.messenger.ui.theme.PrimaryBlue
import com.sms.textmessages.messenger.data.db.AppDatabase
import com.sms.textmessages.messenger.data.db.GroupEntity
import com.sms.textmessages.messenger.ui.archived.ArchivedScreen
import com.sms.textmessages.messenger.ui.blocked.BlockedNumbersScreen
import com.sms.textmessages.messenger.ui.chat.ChatMessage
import com.sms.textmessages.messenger.ui.chat.ChatScreen
import com.sms.textmessages.messenger.ui.contactinfo.ContactInfoScreen
import com.sms.textmessages.messenger.ui.groupchat.GroupChatScreen
import com.sms.textmessages.messenger.ui.home.HomeScreen
import com.sms.textmessages.messenger.ui.home.SmsRepository
import com.sms.textmessages.messenger.ui.home.getContactName
import com.sms.textmessages.messenger.ui.media.MediaAttachment
import com.sms.textmessages.messenger.ui.media.MediaViewerScreen
import com.sms.textmessages.messenger.ui.media.SharedMediaScreen
import com.sms.textmessages.messenger.ui.media.loadMmsAttachments
import com.sms.textmessages.messenger.ui.newgroup.NewGroupScreen
import com.sms.textmessages.messenger.ui.search.GlobalSearchScreen
import com.sms.textmessages.messenger.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavigation(
    onRequestDefault: () -> Unit,
    onRequestCallEndPrerequisites: () -> Unit = {},
    onRequestCallScreeningRole: () -> Unit = {},
    openChatSender: String? = null,
    openChatAutoFocus: Boolean = false,
    onChatSenderConsumed: () -> Unit = {}
) {

    val navController = rememberNavController()

    // Navigate to the correct chat when a notification (or the category
    // overlay popup's "reply"/"view" action) is tapped. Fires on cold start
    // (onCreate) and warm start (onNewIntent) alike.
    LaunchedEffect(openChatSender) {

        if (openChatSender != null) {
            navController.navigate(Screen.Chat.createRoute(openChatSender, autoFocus = openChatAutoFocus))
            onChatSenderConsumed()
        }
    }

    // Call-end prerequisites from Home: Appear-on-top Settings on pre-11,
    // ROLE_CALL_SCREENING (auto-grants overlay) on Android 11+. One-shot
    // flags inside MainActivity ensure each path prompts at most once.
    LaunchedEffect(Unit) {
        onRequestCallEndPrerequisites()
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        composable("home") {

            HomeScreen(
                onRequestDefault = onRequestDefault,
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("startSearch") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("autoFocus") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->

            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val startSearch = backStackEntry.arguments?.getBoolean("startSearch") ?: false
            val autoFocus = backStackEntry.arguments?.getBoolean("autoFocus") ?: false
            val context = LocalContext.current

            var contactName by remember(phone) { mutableStateOf(phone) }
            var messages by remember(phone) { mutableStateOf<List<ChatMessage>>(emptyList()) }
            var threadId by remember(phone) { mutableStateOf(0L) }
            var isLoading by remember(phone) { mutableStateOf(true) }

            LaunchedEffect(phone) {
                val resolvedName = withContext(Dispatchers.IO) { getContactName(context, phone) }
                val resolvedThreadId = withContext(Dispatchers.IO) {
                    SmsRepository.findExistingThreadId(context, phone) ?: 0L
                }
                val loadedMessages = withContext(Dispatchers.IO) {
                    SmsRepository.loadThreadMessages(context, resolvedThreadId)
                }
                contactName = resolvedName.ifEmpty { phone }
                threadId = resolvedThreadId
                messages = loadedMessages
                isLoading = false
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                ChatScreen(
                    contactName = contactName,
                    phoneNumber = phone,
                    threadId = threadId,
                    messages = messages,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onContactClick = {
                        navController.navigate(Screen.ContactInfo.createRoute(phone, contactName))
                    },
                    onMediaClick = { index ->
                        navController.navigate(Screen.MediaViewer.createRoute(phone, index))
                    },
                    onOpenSharedMedia = {
                        navController.navigate(Screen.SharedMedia.createRoute(phone))
                    },
                    onBlockContact = {
                        navController.popBackStack(route = "home", inclusive = false)
                    },
                    startWithSearchOpen = startSearch,
                    autoFocusInput = autoFocus
                )
            }
        }

        composable(Screen.ContactInfo.route) { backStackEntry ->

            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""

            ContactInfoScreen(
                contactName = name,
                phoneNumber = phone,
                onBack = {
                    navController.popBackStack()
                },
                onBlockContact = {
                    navController.popBackStack(route = "home", inclusive = false)
                },
                onOpenSharedMedia = {
                    navController.navigate(Screen.SharedMedia.createRoute(phone))
                },
                onSearchInChat = {
                    navController.navigate(Screen.Chat.createRoute(phone, startSearch = true)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("settings") {

            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenArchived = {
                    navController.navigate(Screen.Archived.route)
                },
                onOpenBlocked = {
                    navController.navigate(Screen.Blocked.route)
                },
                onRequestCallScreeningRole = onRequestCallScreeningRole
            )
        }

        composable(Screen.Search.route) {

            GlobalSearchScreen(
                onBack = {
                    navController.popBackStack()
                },
                onResultClick = { phoneNumber ->
                    navController.navigate("chat/$phoneNumber")
                }
            )
        }

        composable(Screen.Archived.route) {

            ArchivedScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenChat = { phoneNumber, _ ->
                    navController.navigate("chat/$phoneNumber")
                }
            )
        }

        composable(Screen.Blocked.route) {

            BlockedNumbersScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.NewGroup.route) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            NewGroupScreen(
                onBack = {
                    navController.popBackStack()
                },
                onCreateGroup = { phones ->

                    val groupId = java.util.UUID.randomUUID().toString()

                    scope.launch {

                        withContext(Dispatchers.IO) {
                            AppDatabase.getDatabase(context).groupDao().insertGroup(
                                GroupEntity(
                                    groupId = groupId,
                                    participantNumbers = phones.joinToString(","),
                                    groupName = null,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }

                        navController.navigate(Screen.GroupChat.createRoute(groupId)) {
                            popUpTo(Screen.NewGroup.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.GroupChat.route) { backStackEntry ->

            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val context = LocalContext.current

            var participantNumbers by remember(groupId) { mutableStateOf<List<String>>(emptyList()) }

            LaunchedEffect(groupId) {
                val group = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).groupDao().getGroupById(groupId)
                }
                participantNumbers = group?.participantNumbers
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            }

            GroupChatScreen(
                groupId = groupId,
                participantNumbers = participantNumbers,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.SharedMedia.route) { backStackEntry ->

            val phone = backStackEntry.arguments?.getString("phone") ?: ""

            SharedMediaScreen(
                phoneNumber = phone,
                onBack = {
                    navController.popBackStack()
                },
                onMediaClick = { index ->
                    navController.navigate(Screen.MediaViewer.createRoute(phone, index))
                }
            )
        }

        composable(Screen.MediaViewer.route) { backStackEntry ->

            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
            val context = LocalContext.current

            var attachments by remember(phone) { mutableStateOf<List<MediaAttachment>>(emptyList()) }

            LaunchedEffect(phone) {
                attachments = withContext(Dispatchers.IO) {
                    loadMmsAttachments(context, phone)
                }
            }

            MediaViewerScreen(
                attachments = attachments,
                initialIndex = index,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}