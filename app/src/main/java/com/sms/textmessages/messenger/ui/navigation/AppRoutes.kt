package com.sms.textmessages.messenger.ui.navigation

sealed class Screen(val route: String) {

    object Home : Screen("home")

    object Chat : Screen("chat/{phone}?startSearch={startSearch}&autoFocus={autoFocus}") {
        fun createRoute(phone: String, startSearch: Boolean = false, autoFocus: Boolean = false) =
            "chat/$phone?startSearch=$startSearch&autoFocus=$autoFocus"
    }

    object Settings : Screen("settings")

    object NewChat : Screen("newchat")

    object ContactInfo : Screen("contact_info/{phone}/{name}") {
        fun createRoute(phone: String, name: String) = "contact_info/$phone/$name"
    }

    object Search : Screen("search")

    object Archived : Screen("archived")

    object Blocked : Screen("blocked")

    object NewGroup : Screen("new_group")

    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }

    object MediaViewer : Screen("media_viewer/{phone}/{index}") {
        fun createRoute(phone: String, index: Int) = "media_viewer/$phone/$index"
    }

    object SharedMedia : Screen("shared_media/{phone}") {
        fun createRoute(phone: String) = "shared_media/$phone"
    }
}