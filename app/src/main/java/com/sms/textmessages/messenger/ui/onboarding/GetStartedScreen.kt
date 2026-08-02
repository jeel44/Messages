package com.sms.textmessages.messenger.ui.onboarding

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.sms.textmessages.messenger.ui.language.LanguageActivity
import com.sms.textmessages.messenger.ui.theme.GeneralSans
import com.sms.textmessages.messenger.utils.PRIVACY_POLICY_URL
import com.sms.textmessages.messenger.utils.PreferenceManager

private val AccentBlue = Color(0xFF3E6AE1)
private val CardBg = Color(0xFFF1F1F1)
private val FeatureIconBg = Color(0xFFE6F1FB)

@Composable
fun GetStartedScreen() {

    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(Unit) {
        AdCache.ensure(AdPlacement.GET_STARTED_NATIVE, activity)
    }

    // ✅ ONLY PERMISSION LAUNCHER (NO DEFAULT ROLE HERE)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {

        // After permission → go to Language screen
        PreferenceManager.setFirstLaunchDone(context)
        context.startActivity(Intent(context, LanguageActivity::class.java))
        activity.finish()
    }

    fun requestRuntimePermissions() {

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
    }

    // Requests the default-SMS-handler role before any runtime permission
    // dialog, mirroring MainActivity's requestDefaultRole() - Play requires
    // the role prompt to fully resolve first. Runtime permissions are
    // requested from roleLauncher's callback once the role prompt resolves,
    // or directly below when there's no prompt to precede (role
    // unavailable/already held/pre-Q device).
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        requestRuntimePermissions()
    }

    fun requestDefaultRoleThenPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager = context.getSystemService(RoleManager::class.java)

            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {

                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                roleLauncher.launch(intent)
                return
            }
        }
        requestRuntimePermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardBg)
    ) {

        // 🔵 TOP SECTION - solid app blue, ~40% of the screen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .background(AccentBlue)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ic_splash_logo already bakes in its own rounded-square blue frame,
            // so no extra background tile is drawn behind it here - a wrapping
            // tile just fought the logo's own shape and, at low opacity, barely
            // separated from AccentBlue. A shadow gives it lift off the backdrop
            // instead.
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp), clip = false)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Messages",
                fontSize = 22.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Fast, simple texting that stays out of your way",
                fontSize = 14.sp,
                fontFamily = GeneralSans,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFDBE6FB),
                textAlign = TextAlign.Center
            )
        }

        // 🔵 BOTTOM CARD - rounded-top light card, ~60% of the screen,
        // overlapping the blue section visually via its rounded top corners.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Fixed-width block so all three rows share the same left edge -
            // each Row sizes to its own content, so without this shared width
            // a longer title pushes its icon further right than a shorter
            // one when each row centers independently.
            Column(
                modifier = Modifier.width(300.dp)
            ) {

                FeatureRow(
                    icon = Icons.Filled.Sms,
                    title = "Set as default messaging app",
                    description = "Needed to send and receive texts",
                    titleFontFamily = GeneralSans
                )

                Spacer(modifier = Modifier.height(18.dp))

                FeatureRow(
                    icon = Icons.Filled.Block,
                    title = "Block unwanted numbers anytime",
                    description = "Archive or block a conversation in one tap",
                    titleFontFamily = GeneralSans
                )

                Spacer(modifier = Modifier.height(18.dp))

                FeatureRow(
                    icon = Icons.Filled.Lock,
                    title = "Your messages stay on your device",
                    description = "Nothing is uploaded without your say",
                    titleFontFamily = GeneralSans
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🔥 Native Ad Section - plain white rounded card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
            ) {

                val nativeAd = AdCache.nativeState(AdPlacement.GET_STARTED_NATIVE).value

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
                    )

                } else {

                    AdShimmer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        variant = AdShimmerVariant.COMPACT_ROW
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🔥 GET STARTED BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue)
                    .clickable {
                        requestDefaultRoleThenPermissions()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Get started",
                    fontSize = 18.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val privacyPolicyText = buildAnnotatedString {
                append("By continuing you accept our ")
                pushStringAnnotation(tag = "privacy_policy", annotation = PRIVACY_POLICY_URL)
                withStyle(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline)) {
                    append("Privacy policy")
                }
                pop()
            }

            ClickableText(
                text = privacyPolicyText,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = GeneralSans,
                    fontWeight = FontWeight.Normal,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { offset ->
                    privacyPolicyText.getStringAnnotations(tag = "privacy_policy", start = offset, end = offset)
                        .firstOrNull()
                        ?.let { annotation ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                        }
                }
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    titleFontFamily: FontFamily
) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FeatureIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.widthIn(max = 246.dp)
        ) {

            Text(
                text = title,
                fontSize = 14.sp,
                fontFamily = titleFontFamily,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
