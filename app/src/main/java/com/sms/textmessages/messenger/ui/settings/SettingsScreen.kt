package com.sms.textmessages.messenger.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ui.ads.SettingsAdManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenArchived: () -> Unit = {},
    onOpenBlocked: () -> Unit = {}
) {

    val context = LocalContext.current
    val activity = context as Activity



    BackHandler {
        SettingsAdManager.show(activity) {
            onBack()
        }
    }

    // Load interstitial
    LaunchedEffect(Unit) {
        SettingsAdManager.load(activity)
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.White
                    )
                },

                navigationIcon = {
                    IconButton(
                        onClick = {
                            SettingsAdManager.show(activity) {
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
        ) {

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
                icon = R.drawable.ic_privacy,
                text = "Privacy Policy"
            ) {

                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://yourwebsite.com/privacy")
                )

                context.startActivity(intent)
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
                text = "Privacy",
                fontSize = 13.sp,
                fontFamily = GeneralSansSemiBold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )

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
            fontFamily = GeneralSansSemiBold
        )
    }
}

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)