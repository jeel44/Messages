package com.sms.textmessages.messenger.ui.common

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppSwitchBlue = Color(0xFF3E6AE1)

// The app's single styled toggle - white thumb on the app's blue track when
// checked, instead of Material3's default colors. Use this anywhere a toggle
// appears instead of a raw Switch, so every toggle in the app looks the same
// rather than each screen setting (or forgetting to set) its own SwitchColors.
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AppSwitchBlue
        )
    )
}
