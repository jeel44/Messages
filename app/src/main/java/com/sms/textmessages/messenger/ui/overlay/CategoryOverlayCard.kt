package com.sms.textmessages.messenger.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.receiver.NotificationCategory
import com.sms.textmessages.messenger.ui.home.extractCopyableCode
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.InputPillBg
import com.sms.textmessages.messenger.ui.theme.OverlayCardBorder
import com.sms.textmessages.messenger.ui.theme.OverlayCardCreditBg
import com.sms.textmessages.messenger.ui.theme.OverlayCardDebitBg
import com.sms.textmessages.messenger.ui.theme.OverlayCardNeutralBg
import com.sms.textmessages.messenger.ui.theme.OverlayCreditIconColor
import com.sms.textmessages.messenger.ui.theme.OverlayCreditDivider
import com.sms.textmessages.messenger.ui.theme.OverlayCreditTextMuted
import com.sms.textmessages.messenger.ui.theme.OverlayCreditTextPrimary
import com.sms.textmessages.messenger.ui.theme.OverlayDebitDivider
import com.sms.textmessages.messenger.ui.theme.OverlayDebitRed
import com.sms.textmessages.messenger.ui.theme.OverlayDebitTextMuted
import com.sms.textmessages.messenger.ui.theme.OverlayDebitTextPrimary
import com.sms.textmessages.messenger.ui.theme.OverlayIconBgBlue
import com.sms.textmessages.messenger.ui.theme.OverlayIconBgCredit
import com.sms.textmessages.messenger.ui.theme.OverlayIconBgDebit
import com.sms.textmessages.messenger.ui.theme.OverlayTextMuted
import com.sms.textmessages.messenger.ui.theme.OverlayTextPrimary
import com.sms.textmessages.messenger.ui.theme.OverlayTextSecondary

private val OverlaySansSemiBold = FontFamily(Font(R.font.general_sans_bold, FontWeight.SemiBold))
private val OverlaySansMedium = FontFamily(Font(R.font.general_sans_medium, FontWeight.Medium))

private data class OverlayAction(
    val label: String,
    val background: Color,
    val textColor: Color,
    val icon: ImageVector? = null
)

private data class CategoryVisuals(
    val cardBackground: Color,
    val onCard: Color,
    val secondaryText: Color,
    val dividerColor: Color,
    val circleBackground: Color,
    val circleIconTint: Color,
    val circleIcon: ImageVector,
    val action1: OverlayAction,
    val action2: OverlayAction,
    val buttonBorderColor: Color
)

private fun visualsFor(category: NotificationCategory): CategoryVisuals = when (category) {
    NotificationCategory.PERSONAL -> CategoryVisuals(
        cardBackground = OverlayCardNeutralBg,
        onCard = OverlayTextPrimary,
        secondaryText = OverlayTextSecondary,
        dividerColor = InputPillBg,
        circleBackground = AccentBlue,
        circleIconTint = Color.White,
        circleIcon = Icons.AutoMirrored.Filled.Chat,
        action1 = OverlayAction("reply", InputPillBg, AccentBlue),
        action2 = OverlayAction("mark as read", AccentBlue, Color.White),
        buttonBorderColor = AccentBlue
    )
    NotificationCategory.OTP -> CategoryVisuals(
        cardBackground = OverlayCardNeutralBg,
        onCard = OverlayTextPrimary,
        secondaryText = OverlayTextSecondary,
        dividerColor = InputPillBg,
        circleBackground = OverlayIconBgBlue,
        circleIconTint = AccentBlue,
        circleIcon = Icons.Filled.Lock,
        action1 = OverlayAction("copy OTP", InputPillBg, AccentBlue, Icons.Filled.ContentCopy),
        action2 = OverlayAction("dismiss", InputPillBg, OverlayTextSecondary),
        buttonBorderColor = AccentBlue
    )
    NotificationCategory.OFFER -> CategoryVisuals(
        cardBackground = OverlayCardNeutralBg,
        onCard = OverlayTextPrimary,
        secondaryText = OverlayTextSecondary,
        dividerColor = InputPillBg,
        circleBackground = OverlayIconBgBlue,
        circleIconTint = AccentBlue,
        circleIcon = Icons.Filled.LocalOffer,
        action1 = OverlayAction("view", InputPillBg, AccentBlue),
        action2 = OverlayAction("mute sender", InputPillBg, OverlayTextSecondary),
        buttonBorderColor = AccentBlue
    )
    NotificationCategory.TRANSACTION_DEBIT -> CategoryVisuals(
        cardBackground = OverlayCardDebitBg,
        onCard = OverlayDebitTextPrimary,
        secondaryText = OverlayDebitTextMuted,
        dividerColor = OverlayDebitDivider,
        circleBackground = OverlayIconBgDebit,
        circleIconTint = OverlayDebitRed,
        circleIcon = Icons.AutoMirrored.Filled.CallMade,
        action1 = OverlayAction("view statement", Color.White, OverlayDebitRed),
        action2 = OverlayAction("dismiss", Color.White, OverlayDebitTextMuted),
        buttonBorderColor = OverlayDebitRed
    )
    NotificationCategory.TRANSACTION_CREDIT -> CategoryVisuals(
        cardBackground = OverlayCardCreditBg,
        onCard = OverlayCreditTextPrimary,
        secondaryText = OverlayCreditTextMuted,
        dividerColor = OverlayCreditDivider,
        circleBackground = OverlayIconBgCredit,
        circleIconTint = OverlayCreditIconColor,
        circleIcon = Icons.AutoMirrored.Filled.CallReceived,
        action1 = OverlayAction("view statement", Color.White, OverlayCreditIconColor),
        action2 = OverlayAction("dismiss", Color.White, OverlayCreditTextMuted),
        buttonBorderColor = OverlayCreditIconColor
    )
    NotificationCategory.SERVICE_DEFAULT -> CategoryVisuals(
        cardBackground = OverlayCardNeutralBg,
        onCard = OverlayTextPrimary,
        secondaryText = OverlayTextSecondary,
        dividerColor = InputPillBg,
        circleBackground = InputPillBg,
        circleIconTint = OverlayTextSecondary,
        circleIcon = Icons.Filled.Notifications,
        action1 = OverlayAction("view", InputPillBg, AccentBlue),
        action2 = OverlayAction("dismiss", InputPillBg, OverlayTextSecondary),
        buttonBorderColor = AccentBlue
    )
}

@Composable
fun CategoryOverlayCard(
    category: NotificationCategory,
    senderName: String,
    messageBody: String,
    onAction1: () -> Unit,
    onAction2: () -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = visualsFor(category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(visuals.cardBackground)
            .border(1.dp, OverlayCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ic_splash_logo is a full-color splash/launcher asset (rounded-square
            // blue background + bubble mark + baked-in "Messages" wordmark), not a
            // dedicated small monochrome icon - Image (not Icon) so its own colors
            // render instead of being flattened to a single tint.
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${stringResource(R.string.app_name)} · now",
                color = OverlayTextMuted,
                fontFamily = OverlaySansMedium,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = OverlayTextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDismiss)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(visuals.circleBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visuals.circleIcon,
                    contentDescription = null,
                    tint = visuals.circleIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = senderName,
                    color = visuals.onCard,
                    fontFamily = OverlaySansSemiBold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (category == NotificationCategory.OTP) {
                        boldOtpDigits(messageBody, visuals.secondaryText, visuals.onCard)
                    } else {
                        buildAnnotatedString { append(messageBody) }
                    },
                    color = visuals.secondaryText,
                    fontFamily = OverlaySansMedium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.5.dp, color = visuals.dividerColor)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayActionButton(
                action = visuals.action1,
                borderColor = visuals.buttonBorderColor,
                onClick = onAction1,
                modifier = Modifier.weight(1f)
            )
            OverlayActionButton(
                action = visuals.action2,
                borderColor = visuals.buttonBorderColor,
                onClick = onAction2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverlayActionButton(
    action: OverlayAction,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(action.background)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (action.icon != null) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = action.textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = action.label,
            color = action.textColor,
            fontFamily = OverlaySansSemiBold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

private fun boldOtpDigits(body: String, base: Color, boldColor: Color) = buildAnnotatedString {
    val code = extractCopyableCode(body)
    if (code == null) {
        append(body)
        return@buildAnnotatedString
    }
    val start = body.indexOf(code)
    if (start < 0) {
        append(body)
        return@buildAnnotatedString
    }
    append(body.substring(0, start))
    withStyle(style = androidx.compose.ui.text.SpanStyle(color = boldColor, fontWeight = FontWeight.Bold)) {
        append(code)
    }
    append(body.substring(start + code.length))
}
