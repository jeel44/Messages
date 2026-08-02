package com.sms.textmessages.messenger.ui.overlay

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.GeneralSans
import com.sms.textmessages.messenger.ui.theme.OverlayTextPrimary
import com.sms.textmessages.messenger.ui.theme.OverlayTextSecondary
import com.sms.textmessages.messenger.ui.theme.SecondaryTextGray
import java.util.Locale

// Design spec colors - screen fill is a flat light gray (not a scrim+centered
// card - the earlier version read as a small dialog because its content was a
// WRAP_CONTENT card centered inside a full-size scrim; this version's Column
// itself is the full-screen surface).
private val ScreenBg = Color(0xFFF1F1F1)
private val AdSlotDark = Color(0xFF1A1A1A)

// Compact-width breakpoint. Below this, we shrink padding/spacing/sizes so
// the 4 action tiles and top bar keep real breathing room instead of being
// squeezed edge-to-edge on small phones (e.g. 320-360dp wide devices).
private val CompactWidthBreakpoint = 360.dp

private fun callTypeLabel(callType: CallEndType): String = when (callType) {
    CallEndType.INCOMING -> "Incoming call"
    CallEndType.OUTGOING -> "Outgoing call"
    CallEndType.MISSED -> "Missed call"
}

@Composable
fun CallEndOverlayCard(
    displayName: String,
    phoneNumber: String?,
    callType: CallEndType,
    durationMs: Long,
    bannerAd: AdView?,
    unreadCount: Int?,
    onMessage: () -> Unit,
    onCallBack: () -> Unit,
    onSave: () -> Unit,
    onBlock: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onCopyNumber: () -> Unit,
    onViewContact: () -> Unit,
    onReportSpam: () -> Unit
) {
    // Derived from what's already on hand (no extra params needed): a real
    // contact match means displayName came from getContactName(), which is
    // neither the raw number nor the "Unknown" fallback used when there's no
    // match or no number at all.
    val canViewContact = phoneNumber != null && phoneNumber != displayName && displayName != "Unknown"
    val canCopyNumber = phoneNumber != null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < CompactWidthBreakpoint

        // Scale the handful of values that actually cause crowding on small
        // widths. Everything else (colors, structure, ad slot) stays as-is.
        val screenHPadding = if (isCompact) 12.dp else 20.dp
        val tileSpacing = if (isCompact) 6.dp else 10.dp
        val tileIconSize = if (isCompact) 20.dp else 22.dp
        val tileVerticalPadding = if (isCompact) 10.dp else 14.dp
        val tileLabelSize = if (isCompact) 10.sp else 11.sp
        val avatarSize = if (isCompact) 64.dp else 76.dp
        val topBarIconSize = if (isCompact) 24.dp else 28.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(horizontal = screenHPadding)
        ) {

            ////////////////////////////////////////////////////////
            // 🔵 TOP BAR - chevron-down (dismiss) / "Call ended" / kebab
            ////////////////////////////////////////////////////////

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Dismiss",
                    tint = OverlayTextPrimary,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .clickable(onClick = onDismiss)
                        .padding((44.dp - topBarIconSize) / 2)
                        .size(topBarIconSize)
                )

                Text(
                    text = "Call ended",
                    fontSize = if (isCompact) 15.sp else 16.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.SemiBold,
                    color = OverlayTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {

                    var menuExpanded by remember { mutableStateOf(false) }

                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = "More",
                        tint = OverlayTextPrimary,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clickable { menuExpanded = true }
                            .padding((44.dp - 20.dp) / 2)
                            .size(20.dp)
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {

                        if (canCopyNumber) {
                            DropdownMenuItem(
                                text = { Text("Copy number") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onCopyNumber()
                                }
                            )
                        }

                        if (canViewContact) {
                            DropdownMenuItem(
                                text = { Text("View contact") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onViewContact()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Report as spam") },
                            leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onReportSpam()
                            }
                        )
                    }
                }
            }

            ////////////////////////////////////////////////////////
            // 🔵 AVATAR + NAME + NUMBER + CALL TYPE
            ////////////////////////////////////////////////////////

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isCompact) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.trim().firstOrNull()?.uppercase() ?: "#",
                        fontSize = if (isCompact) 24.sp else 28.sp,
                        fontFamily = GeneralSans,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

                Text(
                    text = displayName,
                    fontSize = if (isCompact) 17.sp else 19.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.Bold,
                    color = OverlayTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                if (phoneNumber != null && phoneNumber != displayName) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = phoneNumber,
                        fontSize = 13.sp,
                        fontFamily = GeneralSans,
                        fontWeight = FontWeight.Normal,
                        color = SecondaryTextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (callType == CallEndType.MISSED)
                        callTypeLabel(callType)
                    else
                        "${callTypeLabel(callType)} · ${formatCallDuration(durationMs)}",
                    fontSize = 13.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.Normal,
                    color = OverlayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 20.dp))

            ////////////////////////////////////////////////////////
            // 🔵 ACTION TILES - Message / Call back / Save / Block
            ////////////////////////////////////////////////////////

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tileSpacing)
            ) {
                CallEndActionTile(Icons.AutoMirrored.Filled.Chat, "Message", onMessage, Modifier.weight(1f), tileIconSize, tileVerticalPadding, tileLabelSize)
                CallEndActionTile(Icons.Filled.Call, "Call back", onCallBack, Modifier.weight(1f), tileIconSize, tileVerticalPadding, tileLabelSize)
                CallEndActionTile(Icons.Filled.PersonAdd, "Save", onSave, Modifier.weight(1f), tileIconSize, tileVerticalPadding, tileLabelSize)
                CallEndActionTile(Icons.Filled.Block, "Block", onBlock, Modifier.weight(1f), tileIconSize, tileVerticalPadding, tileLabelSize)
            }

            Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

            ////////////////////////////////////////////////////////
            // 🔵 AD SLOT - one large dark rounded box filling the remaining space
            ////////////////////////////////////////////////////////

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AdSlotDark)
            ) {
                CallEndBannerAdSection(bannerAd)
            }

            Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

            ////////////////////////////////////////////////////////
            // 🔵 ENGAGEMENT BANNER - unread count + open app
            ////////////////////////////////////////////////////////

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isCompact) 14.dp else 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue)
                    .clickable(onClick = onOpenApp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (unreadCount != null && unreadCount > 0)
                            "$unreadCount unread message${if (unreadCount == 1) "" else "s"}"
                        else
                            "No unread messages",
                        fontSize = 14.sp,
                        fontFamily = GeneralSans,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Open Messages to reply",
                        fontSize = 12.sp,
                        fontFamily = GeneralSans,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun CallEndActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    verticalPadding: Dp,
    labelSize: androidx.compose.ui.unit.TextUnit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AccentBlue,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = labelSize,
            fontFamily = GeneralSans,
            fontWeight = FontWeight.Medium,
            color = OverlayTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
// 🔵 BANNER AD - preloaded via AdCache during the call popup
////////////////////////////////////////////////////////

@Composable
private fun CallEndBannerAdSection(bannerAd: AdView?) {
    // Medium Rectangle (300×250) — AdMob MREC / "square" banner for call-end.
    val mrecWidth = 300.dp
    val mrecHeight = 250.dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bannerAd != null) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        (bannerAd.parent as? android.view.ViewGroup)?.removeView(bannerAd)
                        addView(bannerAd)
                    }
                },
                update = { container ->
                    if (container.getChildAt(0) !== bannerAd) {
                        container.removeAllViews()
                        (bannerAd.parent as? android.view.ViewGroup)?.removeView(bannerAd)
                        container.addView(bannerAd)
                    }
                },
                onRelease = { container ->
                    container.removeAllViews()
                },
                modifier = Modifier
                    .width(mrecWidth)
                    .height(mrecHeight)
            )
        } else {
            AdShimmer(
                modifier = Modifier
                    .width(mrecWidth)
                    .height(mrecHeight)
                    .clip(RoundedCornerShape(12.dp)),
                variant = AdShimmerVariant.MEDIA_BLOCK
            )
        }
    }
}