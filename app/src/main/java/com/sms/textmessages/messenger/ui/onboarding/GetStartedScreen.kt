package com.sms.textmessages.messenger.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.GetStartedAdManager
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.sms.textmessages.messenger.ui.language.LanguageActivity
import com.sms.textmessages.messenger.utils.PreferenceManager

@Composable
fun GetStartedScreen() {

    val permissions = mutableListOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )
    val context = LocalContext.current
    val activity = context as Activity

    // 🔥 Load Native Ad
    LaunchedEffect(Unit) {

        if (GetStartedAdManager.nativeAdState == null) {
            GetStartedAdManager.loadNativeAd(activity)
        }
    }

    val generalSans = FontFamily(
        Font(R.font.general_sans_bold)
    )

    // ✅ ONLY PERMISSION LAUNCHER (NO DEFAULT ROLE HERE)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {

        // After permission → go to Language screen
        PreferenceManager.setFirstLaunchDone(context)
        context.startActivity(Intent(context, LanguageActivity::class.java))
        activity.finish()
    }

    // 🔥 Shining Button Animation
    val transition = rememberInfiniteTransition(label = "shine")
    val shimmerX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineAnim"
    )

    val buttonBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2F6BDE),
            Color(0xFF4A90E2),
            Color(0xFF2F6BDE)
        ),
        start = Offset(shimmerX - 200f, 0f),
        end = Offset(shimmerX, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {

        // 🔵 TOP SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(R.drawable.ic_get_started_illustration),
                contentDescription = null,
                modifier = Modifier.size(260.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to",
                fontSize = 18.sp,
                fontFamily = generalSans,
                color = Color.Black
            )

            Text(
                text = "Messages App",
                fontSize = 26.sp,
                fontFamily = generalSans,
                color = Color(0xFF2F6BDE)
            )
        }

        // 🔵 BOTTOM SECTION
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🔥 ENABLE BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(buttonBrush)
                    .clickable {

                        val permissions = mutableListOf(
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_CONTACTS
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enable",
                    fontSize = 20.sp,
                    fontFamily = generalSans,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Processing means you accept our terms",
                fontSize = 12.sp,
                fontFamily = generalSans,
                color = Color.Gray
            )

            Text(
                text = "Privacy Policy",
                fontSize = 12.sp,
                fontFamily = generalSans,
                color = Color(0xFF2F6BDE),
                textDecoration = TextDecoration.Underline
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 🔥 Native Ad Section
            val nativeAd = GetStartedAdManager.nativeAdState

            if (nativeAd != null) {

                AndroidView(
                    factory = { ctx ->
                        val inflater = LayoutInflater.from(ctx)
                        val view = inflater.inflate(R.layout.native_splash_ad, null)
                        val adView = view as NativeAdView

                        val headline = adView.findViewById<TextView>(R.id.ad_headline)
                        val body = adView.findViewById<TextView>(R.id.ad_body)
                        val cta = adView.findViewById<Button>(R.id.ad_call_to_action)
                        val icon = adView.findViewById<ImageView>(R.id.ad_icon)
                        val media = adView.findViewById<MediaView>(R.id.ad_media)

                        adView.headlineView = headline
                        adView.bodyView = body
                        adView.callToActionView = cta
                        adView.iconView = icon
                        adView.mediaView = media

                        headline.text = nativeAd.headline
                        body.text = nativeAd.body
                        cta.text = nativeAd.callToAction

                        nativeAd.icon?.let {
                            icon.setImageDrawable(it.drawable)
                        }

                        val mediaContent = nativeAd.mediaContent
                        if (mediaContent == null) {
                            media.visibility = View.GONE
                        } else {
                            media.mediaContent = mediaContent
                            media.visibility = View.VISIBLE
                        }

                        adView.setNativeAd(nativeAd)

                        adView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(horizontal = 16.dp)
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