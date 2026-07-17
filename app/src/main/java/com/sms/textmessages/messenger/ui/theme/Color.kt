package com.sms.textmessages.messenger.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val PrimaryBlue = Color(0xFF4A6FE3)

// The app's actual accent blue - used for FABs (HomeScreen), sent-message
// styling and AccentBlue duplicates across GroupChatScreen, ContactInfoScreen,
// GlobalSearchScreen, BlockedNumbersScreen, NewGroupScreen, MediaViewerScreen,
// SettingsScreen and NewConversationScreen. Centralized here so new code
// (CategoryOverlayCard) can reference one definition instead of another
// private per-file copy.
val AccentBlue = Color(0xFF3E6AE1)

// Category overlay popup palette (CategoryOverlayCard) - light cards matching
// the app's real light/blue theme (AccentBlue, InputPillBg above), replacing
// an earlier dark palette that never matched the app's actual light theme.
// Neutral bg is a light blue tint rather than pure white so the card has a
// visible boundary against the light-gray inbox screen behind it.
val OverlayCardNeutralBg = Color(0xFFEAF1FC)
val OverlayCardDebitBg = Color(0xFFFDF2F2)
val OverlayCardCreditBg = Color(0xFFF1F8F0)

// Blue-gray outer card border, complements OverlayCardNeutralBg - applied
// universally across all 5 categories, independent of each card's own bg tint.
val OverlayCardBorder = Color(0xFFC7D7F0)

val OverlayTextPrimary = Color(0xFF1A1A1A)
val OverlayTextSecondary = Color(0xFF6E6E6E)
// Also doubles as the close (X) icon tint on the overlay's light cards.
val OverlayTextMuted = Color(0xFF9A9A9A)

// OTP/Offer icon-circle background - a light tint of AccentBlue, both icons
// are tinted AccentBlue itself rather than a separate indigo/amber (those two
// constants below stay untouched: HomeScreen's classifyNotification pill
// styling still reads OverlayOtpIndigo/OverlayOfferAmber/OverlayCreditGreen).
val OverlayIconBgBlue = Color(0xFFE6F1FB)
val OverlayOtpIndigo = Color(0xFF6C5DD3)
val OverlayOfferAmber = Color(0xFFFFA726)
val OverlayCreditGreen = Color(0xFF4CAF50)

// Transaction-debit overlay colors - OverlayDebitRed is the icon-circle tint
// and the "View statement" button's text color; the other two are new.
val OverlayDebitRed = Color(0xFFC0392B)
val OverlayIconBgDebit = Color(0xFFF6D9D5)
val OverlayDebitTextPrimary = Color(0xFF8B2E24)
val OverlayDebitTextMuted = Color(0xFFA85B52)
val OverlayDebitDivider = Color(0xFFF5D5D5)

// Transaction-credit overlay colors - a distinct icon-tint green from
// OverlayCreditGreen above since that one is still used by HomeScreen's pill.
val OverlayCreditIconColor = Color(0xFF2E7D32)
val OverlayIconBgCredit = Color(0xFFDCEEDA)
val OverlayCreditTextPrimary = Color(0xFF1E5C22)
val OverlayCreditTextMuted = Color(0xFF5C8E58)
val OverlayCreditDivider = Color(0xFFD5EDD1)

// The app's existing light input-pill background - used for the chat/search
// text field fills (ChatScreen, GlobalSearchScreen), selection pills
// (NewGroupScreen) and message bubbles across ChatScreen/HomeScreen.
// Previously duplicated as a raw Color(0xFFF1F1F1) literal at each call site.
val InputPillBg = Color(0xFFF1F1F1)

// The app's existing muted/secondary text gray - used for message timestamps
// (ChatScreen, GroupChatScreen). Previously duplicated as a raw
// Color(0xFF8A8A8A) literal at each call site.
val SecondaryTextGray = Color(0xFF8A8A8A)