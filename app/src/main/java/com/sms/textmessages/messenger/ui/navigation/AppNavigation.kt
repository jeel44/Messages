package com.sms.textmessages.messenger.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.sms.textmessages.messenger.ui.home.HomeScreen
import com.sms.textmessages.messenger.ui.chat.ChatScreen
import com.sms.textmessages.messenger.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    onRequestDefault: () -> Unit,
    openChatSender: String? = null
) {

    val navController = rememberNavController()

    // Handle notification click navigation
    LaunchedEffect(openChatSender) {

        if (openChatSender != null) {
            navController.navigate("chat/$openChatSender")
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        composable("home") {

            val navControllerLocal = navController

            LaunchedEffect(openChatSender) {

                if (!openChatSender.isNullOrEmpty()) {

                    navControllerLocal.navigate("chat/$openChatSender")
                }
            }

            HomeScreen(
                onRequestDefault = onRequestDefault
            )
        }

        composable("chat/{phone}") { backStackEntry ->

            val phone =
                backStackEntry.arguments?.getString("phone") ?: ""

            ChatScreen(
                contactName = "",
                phoneNumber = phone,
                messages = emptyList(),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {

            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}