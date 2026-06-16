package com.sms.textmessages.messenger.ui.navigation

sealed class Screen(val route: String) {

    object Home : Screen("home")

    object Chat : Screen("chat/{phone}") {
        fun createRoute(phone: String) = "chat/$phone"
    }

    object Settings : Screen("settings")

    object NewChat : Screen("newchat")
}