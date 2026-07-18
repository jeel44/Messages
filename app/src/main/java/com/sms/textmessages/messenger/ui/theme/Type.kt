package com.sms.textmessages.messenger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R

val GeneralSans = FontFamily(
    Font(R.font.general_sans_light, FontWeight.Light),
    Font(R.font.general_sans_regular, FontWeight.Normal),
    Font(R.font.general_sans_medium, FontWeight.Medium),
    // general_sans_semibold.otf is not bundled in res/font/ (only general_sans_semibolditalic.otf
    // exists) so FontWeight.SemiBold falls back to the medium file, the closest actual weight,
    // rather than silently resolving to bold.
    Font(R.font.general_sans_medium, FontWeight.SemiBold),
    Font(R.font.general_sans_bold, FontWeight.Bold)
)

val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GeneralSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)
