package com.sms.textmessages.messenger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R

val GeneralSans = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
)