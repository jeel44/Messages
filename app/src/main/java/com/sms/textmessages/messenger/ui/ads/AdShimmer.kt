package com.sms.textmessages.messenger.ui.ads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.InputPillBg
import com.sms.textmessages.messenger.ui.theme.SecondaryTextGray

////////////////////////////////////////////////////////
// 🔵 SHARED AD LOADING SHIMMER
////////////////////////////////////////////////////////

// One shimmer for every ad slot in the app (4 native placements + 2 banner
// placements). Previously each screen hand-rolled its own near-identical
// shimmer at a hardcoded size (HomeScreen's ShimmerNativeAd, LanguageScreen's
// ShimmerAdBox, SplashScreenUI's ShimmerBox, GetStartedScreen's static,
// unanimated gray Box; the two banner placements had no loading state at
// all). This centralizes that: `variant` picks the internal layout shape,
// the caller's `modifier` controls actual size, so the same composable drops
// into a ~55dp banner strip, a ~100-110dp native row, or a ~260dp media
// block unchanged.
enum class AdShimmerVariant {

    // Media placeholder block above an icon + two text bars + CTA row -
    // matches the vertical "big" native ad layouts (native_big_ad_layout.xml,
    // used by LanguageScreen).
    MEDIA_BLOCK,

    // Icon + two text bars + CTA in a single row, no media area - matches
    // the horizontal native ad layouts (native_small_ad_layout.xml /
    // native_splash_ad.xml, used by HomeScreen, SplashScreenUI and
    // GetStartedScreen).
    COMPACT_ROW,

    // Same row shape compressed into a short, wide strip - matches the
    // AdView banner slots (HomeScreen's BannerAdSection, ChatScreen's
    // ChatBannerAdSection).
    BANNER
}

private val ShimmerBlockColor = Color(0xFFE2E2E2)

@Composable
fun AdShimmer(
    modifier: Modifier = Modifier,
    variant: AdShimmerVariant = AdShimmerVariant.COMPACT_ROW
) {
    val transition = rememberInfiniteTransition(label = "adShimmer")

    // Single animated value driving every skeleton block below - each block
    // reads the same number on the same frame, so the light sweep stays in
    // lockstep across the icon, both bars, and the button, reading as one
    // continuous diagonal sweep rather than several independently-animated
    // pieces.
    val shineTranslate by transition.animateFloat(
        initialValue = -400f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "adShimmerShine"
    )

    // Diagonal (dx == dy) soft light band, composited over each block's base
    // color below - translucent so it reads as a sheen, not a solid color.
    val shineBrush = Brush.linearGradient(
        colors = listOf(
            AccentBlue.copy(alpha = 0f),
            AccentBlue.copy(alpha = 0.22f),
            AccentBlue.copy(alpha = 0f)
        ),
        start = Offset(shineTranslate, 0f),
        end = Offset(shineTranslate + 220f, 220f)
    )

    // Same sweep, stronger tint - reserved for the button-skeleton so it
    // reads as "this will be a button" against the weaker skeleton blocks.
    val buttonShineBrush = Brush.linearGradient(
        colors = listOf(
            AccentBlue.copy(alpha = 0f),
            AccentBlue.copy(alpha = 0.35f),
            AccentBlue.copy(alpha = 0f)
        ),
        start = Offset(shineTranslate, 0f),
        end = Offset(shineTranslate + 220f, 220f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(InputPillBg)
    ) {
        when (variant) {

            AdShimmerVariant.MEDIA_BLOCK -> Column(modifier = Modifier.fillMaxSize()) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ShimmerBlockColor)
                        .background(shineBrush)
                )

                AdShimmerRow(
                    shineBrush = shineBrush,
                    buttonShineBrush = buttonShineBrush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )
            }

            AdShimmerVariant.COMPACT_ROW, AdShimmerVariant.BANNER ->
                AdShimmerRow(
                    shineBrush = shineBrush,
                    buttonShineBrush = buttonShineBrush,
                    modifier = Modifier.fillMaxSize()
                )
        }
    }
}

// Icon (left) + title/subtitle bars (middle) + "Ad" label & pill button
// (right). Sizes derive from the row's own available height (via
// BoxWithConstraints) rather than fixed dp constants, so this same layout
// compresses cleanly from a ~50dp banner strip up to a full ~110dp compact
// native row instead of overflowing the shortest slot.
@Composable
private fun AdShimmerRow(
    shineBrush: Brush,
    buttonShineBrush: Brush,
    modifier: Modifier = Modifier
) {

    BoxWithConstraints(modifier = modifier) {

        val rowHeight = maxHeight
        val gap = (rowHeight * 0.14f).coerceAtMost(12.dp)
        val iconSize = rowHeight - gap * 2
        val titleBarHeight = (rowHeight * 0.16f).coerceIn(6.dp, 14.dp)
        val subtitleBarHeight = (rowHeight * 0.12f).coerceIn(5.dp, 11.dp)
        val buttonHeight = (rowHeight * 0.34f).coerceIn(16.dp, 28.dp)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = gap, vertical = gap),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Icon placeholder
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShimmerBlockColor)
                    .background(shineBrush)
            )

            Spacer(modifier = Modifier.width(gap))

            // Title + subtitle bars
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(titleBarHeight)
                        .clip(RoundedCornerShape(titleBarHeight / 2))
                        .background(ShimmerBlockColor)
                        .background(shineBrush)
                )

                Spacer(modifier = Modifier.height(gap / 2))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(subtitleBarHeight)
                        .clip(RoundedCornerShape(subtitleBarHeight / 2))
                        .background(ShimmerBlockColor)
                        .background(shineBrush)
                )
            }

            Spacer(modifier = Modifier.width(gap))

            // "Ad" label + pill-shaped CTA placeholder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = "Ad",
                    color = SecondaryTextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(gap / 2))

                Box(
                    modifier = Modifier
                        .width(buttonHeight * 2.1f)
                        .height(buttonHeight)
                        .clip(RoundedCornerShape(buttonHeight / 2))
                        .background(AccentBlue.copy(alpha = 0.16f))
                        .background(buttonShineBrush)
                )
            }
        }
    }
}
