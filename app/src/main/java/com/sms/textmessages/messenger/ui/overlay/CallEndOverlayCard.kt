package com.sms.textmessages.messenger.ui.overlay

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.sms.textmessages.messenger.ui.home.generateColorFromName
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.InputPillBg
import com.sms.textmessages.messenger.ui.theme.OverlayCardBorder
import com.sms.textmessages.messenger.ui.theme.OverlayCardNeutralBg
import com.sms.textmessages.messenger.ui.theme.OverlayTextMuted
import com.sms.textmessages.messenger.ui.theme.OverlayTextPrimary
import com.sms.textmessages.messenger.ui.theme.OverlayTextSecondary
import com.sms.textmessages.messenger.ui.theme.SecondaryTextGray
import java.util.Locale

private val CallEndSansSemiBold = FontFamily(Font(R.font.general_sans_bold, FontWeight.SemiBold))
private val CallEndSansMedium = FontFamily(Font(R.font.general_sans_medium, FontWeight.Medium))

private data class CallTypeVisuals(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

private fun visualsFor(callType: CallEndType): CallTypeVisuals = when (callType) {
    CallEndType.INCOMING -> CallTypeVisuals("Incoming call", Icons.AutoMirrored.Filled.CallReceived, Color(0xFF2E7D32))
    CallEndType.OUTGOING -> CallTypeVisuals("Outgoing call", Icons.AutoMirrored.Filled.CallMade, AccentBlue)
    CallEndType.MISSED -> CallTypeVisuals("Missed call", Icons.AutoMirrored.Filled.CallMissed, Color(0xFFC0392B))
}

@Composable
fun CallEndOverlayCard(
    displayName: String,
    phoneNumber: String?,
    callType: CallEndType,
    durationMs: Long,
    nativeAd: NativeAd?,
    unreadCount: Int?,
    onMessage: () -> Unit,
    onCallBack: () -> Unit,
    onSave: () -> Unit,
    onBlock: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = visualsFor(callType)
    val avatarColor = generateColorFromName(displayName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0B0B0B)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = OverlayTextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDismiss)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.trim().firstOrNull()?.uppercase() ?: "#",
                        fontSize = 28.sp,
                        fontFamily = CallEndSansSemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = displayName,
                    fontSize = 19.sp,
                    fontFamily = CallEndSansSemiBold,
                    color = OverlayTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (phoneNumber != null && phoneNumber != displayName) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phoneNumber,
                        fontSize = 13.sp,
                        fontFamily = CallEndSansMedium,
                        color = SecondaryTextGray
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = visuals.tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (callType == CallEndType.MISSED)
                            visuals.label
                        else
                            "${visuals.label} · ${formatCallDuration(durationMs)}",
                        fontSize = 13.sp,
                        fontFamily = CallEndSansMedium,
                        color = OverlayTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallEndActionButton(Icons.AutoMirrored.Filled.Chat, "Message", onMessage)
                CallEndActionButton(Icons.Filled.Call, "Call back", onCallBack)
                CallEndActionButton(Icons.Filled.PersonAdd, "Save", onSave)
                CallEndActionButton(Icons.Filled.Block, "Block", onBlock)
            }

            Spacer(modifier = Modifier.height(20.dp))

            CallEndNativeAdSection(nativeAd)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = OverlayCardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OverlayCardNeutralBg)
                    .clickable(onClick = onOpenApp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Back to Messages",
                        fontSize = 14.sp,
                        fontFamily = CallEndSansSemiBold,
                        color = OverlayTextPrimary
                    )
                    if (unreadCount != null && unreadCount > 0) {
                        Text(
                            text = "$unreadCount unread message${if (unreadCount == 1) "" else "s"}",
                            fontSize = 12.sp,
                            fontFamily = CallEndSansMedium,
                            color = OverlayTextSecondary
                        )
                    }
                }
                Text(
                    text = "Open",
                    fontSize = 13.sp,
                    fontFamily = CallEndSansSemiBold,
                    color = AccentBlue
                )
            }
        }
    }
}

@Composable
private fun CallEndActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(InputPillBg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AccentBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = CallEndSansMedium,
            color = OverlayTextSecondary
        )
    }
}

private fun formatCallDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

////////////////////////////////////////////////////////
// 🔵 NATIVE AD (same NativeAdView binding contract as HomeScreen's
// bindHomeNativeAd/NativeAdSection - own layout + own AdShimmer loading state)
////////////////////////////////////////////////////////

private fun NativeAdView.bindCallEndNativeAd(nativeAd: NativeAd) {

    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView>(R.id.ad_body)
    val cta = findViewById<Button>(R.id.ad_call_to_action)
    val icon = findViewById<ImageView>(R.id.ad_icon)
    val media = findViewById<MediaView>(R.id.ad_media)

    headlineView = headline
    bodyView = body
    callToActionView = cta
    iconView = icon
    mediaView = media

    headline.text = nativeAd.headline

    val adBody = nativeAd.body
    if (adBody.isNullOrEmpty()) {
        body.visibility = View.GONE
    } else {
        body.text = adBody
        body.visibility = View.VISIBLE
    }

    val adCta = nativeAd.callToAction
    if (adCta.isNullOrEmpty()) {
        cta.visibility = View.GONE
    } else {
        cta.text = adCta
        cta.visibility = View.VISIBLE
    }

    val adIcon = nativeAd.icon
    if (adIcon == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(adIcon.drawable)
        icon.visibility = View.VISIBLE
    }

    val mediaContent = nativeAd.mediaContent
    if (mediaContent == null) {
        media.visibility = View.GONE
    } else {
        media.mediaContent = mediaContent
        media.visibility = View.VISIBLE
    }

    setNativeAd(nativeAd)
}

@Composable
private fun CallEndNativeAdSection(nativeAd: NativeAd?) {

    if (nativeAd != null) {

        AndroidView(
            factory = { context ->
                val inflater = LayoutInflater.from(context)
                val adView =
                    inflater.inflate(R.layout.native_call_end_ad_layout, null) as NativeAdView
                adView.bindCallEndNativeAd(nativeAd)
                adView
            },
            update = { adView ->
                adView.bindCallEndNativeAd(nativeAd)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )

    } else {
        AdShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            variant = AdShimmerVariant.COMPACT_ROW
        )
    }
}
