package com.sms.textmessages.messenger.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.GeneralSans

private val WithoutCardBg = Color(0xFFF5F5F5)
private val WithoutCardBorder = Color(0xFFD0D0D0)
private val WithoutAvatarBg = Color(0xFFBDBDBD)
private val WithAccessCardBg = AccentBlue.copy(alpha = 0.08f)
private val WithAccessCardBorder = Color(0xFFBBD0F5)
private val ReasonTextColor = Color(0xFF4A4A4A)

@Composable
fun CallLogDisclosureScreen(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "See who's calling",
            fontFamily = GeneralSans,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "With call log access, your after-call screen looks like this:",
            fontFamily = GeneralSans,
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            WithoutCard(modifier = Modifier.weight(1f))
            WithAccessCard(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        ReasonLine(
            icon = Icons.Outlined.Lock,
            text = "Matched locally against your saved contacts — never uploaded"
        )

        Spacer(modifier = Modifier.height(12.dp))

        ReasonLine(
            icon = Icons.Outlined.PowerSettingsNew,
            text = "Not spam detection — just your own saved names"
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(AccentBlue)
                .clickable { onAllow() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Turn on caller names",
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun WithoutCard(modifier: Modifier = Modifier) {

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WithoutCardBg)
            .dashedBorder(
                color = WithoutCardBorder,
                strokeWidth = 1.5.dp,
                cornerRadius = 14.dp
            )
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "WITHOUT",
            fontFamily = GeneralSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(WithoutAvatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Unknown",
            fontFamily = GeneralSans,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "+91 98••• •••12",
            fontFamily = GeneralSans,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun WithAccessCard(modifier: Modifier = Modifier) {

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WithAccessCardBg)
            .border(1.5.dp, WithAccessCardBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "WITH ACCESS",
            fontFamily = GeneralSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = AccentBlue
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AccentBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P",
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Priya Sharma",
            fontFamily = GeneralSans,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Mobile",
            fontFamily = GeneralSans,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun ReasonLine(icon: ImageVector, text: String) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            fontFamily = GeneralSans,
            fontSize = 13.sp,
            color = ReasonTextColor
        )
    }
}

// Compose has no built-in dashed-border modifier - draws a dashed rounded-rect
// stroke inset by half the stroke width so the dashes render fully inside the
// card's own clip bounds instead of being cut off at the edge.
private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    cornerRadius: Dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawBehind {
    val strokeWidthPx = strokeWidth.toPx()
    val inset = strokeWidthPx / 2

    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                0f
            )
        )
    )
}
