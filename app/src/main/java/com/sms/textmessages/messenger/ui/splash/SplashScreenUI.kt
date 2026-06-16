package com.sms.textmessages.messenger.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.SplashAdManager



@Composable
fun SplashScreenUI() {

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 4000,
                easing = LinearEasing
            )
        )
    }

    val nativeAd: NativeAd? = SplashAdManager.nativeAdState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {

        // CENTER LOGO
        Image(
            painter = painterResource(id = R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
        )

        // BOTTOM CONTENT
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "This action may contain ads",
                fontSize = 14.sp,
                fontFamily = FontFamily(
                    Font(R.font.general_sans_semibold)
                ),
                color = Color(0xFF6E6E6E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .height(4.dp),
                color = Color(0xFF0073FF),
                trackColor = Color(0xFFDCDCDC)
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (nativeAd != null) {

                AndroidView(
                    factory = { context ->

                        val inflater = LayoutInflater.from(context)
                        val view =
                            inflater.inflate(R.layout.native_splash_ad, null)
                        val adView = view as NativeAdView

                        val headlineView =
                            adView.findViewById<TextView>(R.id.ad_headline)
                        val bodyView =
                            adView.findViewById<TextView>(R.id.ad_body)
                        val ctaView =
                            adView.findViewById<Button>(R.id.ad_call_to_action)
                        val iconView =
                            adView.findViewById<ImageView>(R.id.ad_icon)

                        adView.headlineView = headlineView
                        adView.bodyView = bodyView
                        adView.callToActionView = ctaView
                        adView.iconView = iconView

                        headlineView.text = nativeAd.headline
                        bodyView.text = nativeAd.body
                        ctaView.text = nativeAd.callToAction

                        val adIcon = nativeAd.icon
                        if (adIcon != null) {
                            iconView.setImageDrawable(adIcon.drawable)
                        }

                        adView.setNativeAd(nativeAd)

                        adView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

            } else {

                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")

    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnim"
    )

    val shimmerColors = listOf(
        Color(0xFFE0E0E0),
        Color(0xFFF5F5F5),
        Color(0xFFE0E0E0)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Box(
        modifier = modifier
            .background(brush)
    )
}