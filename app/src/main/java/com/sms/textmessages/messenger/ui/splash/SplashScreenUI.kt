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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.SplashAdManager
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.sms.textmessages.messenger.ui.theme.AccentBlue
import com.sms.textmessages.messenger.ui.theme.InputPillBg



// `progress` is owned by SplashActivity, but animateTo() must actually run
// from inside this Composable (via LaunchedEffect) since Animatable.animateTo
// needs a MonotonicFrameClock, which only exists inside Compose's own
// coroutine context - a plain Activity coroutine scope doesn't have one and
// throws IllegalStateException. Once the animation finishes, onProgressComplete
// reports that back to SplashActivity so it can gate showAdIfAvailable() -
// still one Animatable / one timer driving both the visual bar and the real
// dismiss point, just invoked from the right context.
@Composable
fun SplashScreenUI(progress: Animatable<Float, AnimationVector1D>, onProgressComplete: () -> Unit) {

    val nativeAd: NativeAd? = SplashAdManager.nativeAdState

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
        )
        onProgressComplete()
    }

    // Gentle continuous pulse on the logo while the splash is visible.
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InputPillBg)
    ) {

        // CENTER LOGO + APP NAME
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontFamily = FontFamily(
                    Font(R.font.general_sans_bold)
                ),
                color = Color(0xFF1A1A1A)
            )
        }

        // BOTTOM CONTENT
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "This app may show ads",
                fontSize = 14.sp,
                fontFamily = FontFamily(
                    Font(R.font.general_sans_bold)
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
                color = AccentBlue,
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
                        val mediaView =
                            adView.findViewById<MediaView>(R.id.ad_media)

                        adView.headlineView = headlineView
                        adView.bodyView = bodyView
                        adView.callToActionView = ctaView
                        adView.iconView = iconView
                        adView.mediaView = mediaView

                        headlineView.text = nativeAd.headline
                        bodyView.text = nativeAd.body
                        ctaView.text = nativeAd.callToAction

                        val adIcon = nativeAd.icon
                        if (adIcon != null) {
                            iconView.setImageDrawable(adIcon.drawable)
                        }

                        val mediaContent = nativeAd.mediaContent
                        if (mediaContent == null) {
                            mediaView.visibility = View.GONE
                        } else {
                            mediaView.mediaContent = mediaContent
                            mediaView.visibility = View.VISIBLE
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

                AdShimmer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(horizontal = 16.dp),
                    variant = AdShimmerVariant.COMPACT_ROW
                )
            }
        }
    }
}