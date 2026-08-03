package com.sms.textmessages.messenger.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneLocked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.common.AppSwitch
import com.sms.textmessages.messenger.ui.theme.GeneralSans
import com.sms.textmessages.messenger.utils.CallEndMetrics
import com.sms.textmessages.messenger.utils.CallScreeningRole
import com.sms.textmessages.messenger.utils.OemBatteryGuide
import com.sms.textmessages.messenger.utils.PRIVACY_POLICY_URL
import com.sms.textmessages.messenger.utils.PreferenceManager
import com.sms.textmessages.messenger.App

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenArchived: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    onRequestCallScreeningRole: () -> Unit = {}
) {

    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var isCallScreener by remember {
        mutableStateOf(CallScreeningRole.isHeld(context))
    }

    // Refresh after returning from the system role picker.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isCallScreener = CallScreeningRole.isHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        AdCache.onClickGated(activity, AdPlacement.SETTINGS_INTERSTITIAL) {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        AdCache.ensure(AdPlacement.SETTINGS_INTERSTITIAL, activity)
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontFamily = GeneralSans,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },

                navigationIcon = {
                    IconButton(
                        onClick = {
                            AdCache.onClickGated(activity, AdPlacement.SETTINGS_INTERSTITIAL) {
                                onBack()
                            }
                        }
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
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "General",
                fontSize = 13.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )

            SettingsItem(
                icon = R.drawable.ic_feedback,
                text = "Feedback"
            ) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:ramanirajatmaganbhai@gmail.com\n")
                }
                context.startActivity(intent)
            }

            SettingsItem(
                icon = R.drawable.ic_rate,
                text = "Rate Us"
            ) {

                val uri =
                    Uri.parse("market://details?id=" + context.packageName)

                val intent = Intent(Intent.ACTION_VIEW, uri)

                context.startActivity(intent)
            }

            SettingsItem(
                icon = R.drawable.ic_share,
                text = "Share with friends"
            ) {

                val intent = Intent(Intent.ACTION_SEND).apply {

                    type = "text/plain"

                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Check this SMS app https://play.google.com/store/apps/details?id=${context.packageName}"
                    )
                }

                context.startActivity(
                    Intent.createChooser(intent, "Share")
                )
            }

            SettingsItem(
                icon = R.drawable.ic_about,
                text = "About Messages"
            ) {

                val version =
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).versionName

                val dialog = android.app.AlertDialog.Builder(context)

                dialog.setTitle("About")

                dialog.setMessage("Messages\nVersion $version")

                dialog.setPositiveButton("OK", null)

                dialog.show()
            }

            Text(
                text = "Calling",
                fontSize = 13.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )

            if (CallScreeningRole.isAvailable(context)) {
                SettingsItemWithSubtitle(
                    icon = Icons.Filled.PhoneLocked,
                    text = if (isCallScreener) "Call screener is on" else "Set as call screener",
                    subtitle = if (isCallScreener) {
                        "Blocks spam and numbers on your blocked list before they ring"
                    } else {
                        "Block spam and blocked numbers before they ring"
                    },
                    onClick = {
                        if (!isCallScreener) {
                            onRequestCallScreeningRole()
                        }
                    }
                )

                var blockFromBlocked by remember {
                    mutableStateOf(PreferenceManager.isBlockCallsFromBlockedEnabled(context))
                }
                SettingsToggleItem(
                    icon = Icons.Filled.PhoneLocked,
                    text = "Block calls from blocked numbers",
                    subtitle = "Reject phone calls from numbers you've blocked in Messages",
                    checked = blockFromBlocked,
                    enabled = isCallScreener,
                    onCheckedChange = { enabled ->
                        blockFromBlocked = enabled
                        PreferenceManager.setBlockCallsFromBlockedEnabled(context, enabled)
                    }
                )

                var silenceUnknown by remember {
                    mutableStateOf(PreferenceManager.isSilenceUnknownCallersEnabled(context))
                }
                SettingsToggleItem(
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    text = "Silence unknown callers",
                    subtitle = "Ring silently for numbers not in your contacts",
                    checked = silenceUnknown,
                    enabled = isCallScreener,
                    onCheckedChange = { enabled ->
                        silenceUnknown = enabled
                        PreferenceManager.setSilenceUnknownCallersEnabled(context, enabled)
                    }
                )
            }

            var callEndEnabled by remember { mutableStateOf(PreferenceManager.isCallEndEnabled(context)) }

            SettingsToggleItem(
                icon = Icons.Filled.Call,
                text = "Call end screen",
                subtitle = "Show caller info in an after-call screen when a call ends",
                checked = callEndEnabled,
                onCheckedChange = { enabled ->
                    callEndEnabled = enabled
                    PreferenceManager.setCallEndEnabled(context, enabled)
                }
            )

            // Advanced: only for OEMs that still miss after during-call overlay.
            // Not part of primary onboarding (Truecaller path is the main fix).
            if (OemBatteryGuide.isAggressiveOem()) {
                Text(
                    text = "Advanced",
                    fontSize = 13.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
                SettingsItemWithSubtitle(
                    icon = Icons.Filled.PhoneLocked,
                    text = "Improve call reliability",
                    subtitle = "If caller ID sometimes misses, allow background / Autostart for this device",
                    onClick = {
                        val steps = OemBatteryGuide.steps(context)
                        val first = steps.firstOrNull()?.intent
                        if (first != null) {
                            try {
                                App.disableAppOpenAd = true
                                context.startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                OemBatteryGuide.openAppDetails(context)
                            }
                        } else {
                            OemBatteryGuide.openAppDetails(context)
                        }
                    }
                )
                Text(
                    text = "Diagnostics: ${CallEndMetrics.summary(context)}",
                    fontSize = 11.sp,
                    fontFamily = GeneralSans,
                    color = Color.LightGray,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Privacy",
                fontSize = 13.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )

            SettingsItem(
                icon = R.drawable.ic_privacy,
                text = "Privacy Policy"
            ) {

                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(PRIVACY_POLICY_URL)
                )

                context.startActivity(intent)
            }

            SettingsItem(
                icon = R.drawable.ic_archive,
                text = "Archived conversations",
                onClick = onOpenArchived
            )

            SettingsItem(
                icon = R.drawable.ic_block,
                text = "Blocked numbers",
                onClick = onOpenBlocked
            )
        }
    }
}

////////////////////////////////////////////////////////
// SETTINGS ITEM
////////////////////////////////////////////////////////

@Composable
fun SettingsItem(
    icon: Int,
    text: String,
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
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 16.sp,
            fontFamily = GeneralSans,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingsItemWithSubtitle(
    icon: ImageVector,
    text: String,
    subtitle: String,
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
                    tint = Color(0xFF3E6AE1)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Normal,
                color = Color.Gray
            )
        }
    }
}

////////////////////////////////////////////////////////
// SETTINGS TOGGLE ITEM
////////////////////////////////////////////////////////

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    text: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    tint = if (enabled) Color(0xFF3E6AE1) else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = text,
                fontSize = 16.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.Unspecified else Color.Gray
            )

            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Normal,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
