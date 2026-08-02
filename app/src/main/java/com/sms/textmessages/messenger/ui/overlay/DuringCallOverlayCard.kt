package com.sms.textmessages.messenger.ui.overlay

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.GeneralSans

private val BubbleBg = Color(0xFFF8F9FC)
private val BubbleBorder = Color(0xFFE2E6EF)

enum class DuringCallPhase {
    RINGING,
    IN_CALL,
    OUTGOING
}

@Composable
fun DuringCallOverlayCard(
    displayName: String,
    phoneNumber: String?,
    phase: DuringCallPhase,
    onDismiss: () -> Unit
) {
    val status = when (phase) {
        DuringCallPhase.RINGING -> "Incoming call"
        DuringCallPhase.IN_CALL -> "On call"
        DuringCallPhase.OUTGOING -> "Calling…"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BubbleBg)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (phase == DuringCallPhase.OUTGOING) Icons.Filled.Call else Icons.Filled.Person,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status,
                            fontFamily = GeneralSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = displayName,
                            fontFamily = GeneralSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = Color(0xFF1A1A1A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!phoneNumber.isNullOrBlank() && phoneNumber != displayName) {
                            Text(
                                text = phoneNumber,
                                fontFamily = GeneralSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Caller ID by Messages",
                fontFamily = GeneralSans,
                fontSize = 11.sp,
                color = Color(0xFF9AA0A6)
            )
        }
    }
}
