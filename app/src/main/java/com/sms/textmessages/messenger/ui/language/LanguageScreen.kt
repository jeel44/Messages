package com.sms.textmessages.messenger.ui.language

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.ads.AdShimmer
import com.sms.textmessages.messenger.ui.ads.AdShimmerVariant
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.sms.textmessages.messenger.utils.LocaleManager



@Composable
fun LanguageScreen() {

    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(Unit) {
        AdCache.ensure(AdPlacement.LANGUAGE_NATIVE, activity)
    }

    var selectedLanguage by remember { mutableStateOf<String?>(null) }

    val generalSans = FontFamily(
        Font(R.font.general_sans_bold)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {

        // 🔵 TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2F6BDE))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = stringResource(R.string.select_language),
                color = Color.White,
                fontSize = 25.sp,
                fontFamily = FontFamily(
                    Font(R.font.general_sans_bold)
                ),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )

            if (selectedLanguage != null) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.clickable {

                        // OPTIONAL: Save selected language
                        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        prefs.edit().putString("app_lang", selectedLanguage).apply()

                        LocaleManager.setLocale(activity, selectedLanguage!!)

                        // 🔥 GO TO SET DEFAULT SCREEN
                        activity.startActivity(
                            Intent(activity, com.sms.textmessages.messenger.MainActivity::class.java)
                        )
                        activity.finish()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔵 LANGUAGE LIST
        val languages = listOf(
            Triple("English", "English", R.drawable.ic_flag_english),
            Triple("Hindi", "हिंदी", R.drawable.ic_flag_hindi),
            Triple("China", "中国人", R.drawable.ic_flag_chinese),
            Triple("Spain", "Española", R.drawable.ic_flag_spanish),
            Triple("Saudi Arabia", "عربي", R.drawable.ic_flag_arabic),
            Triple("Germany", "Deutsch", R.drawable.ic_flag_german),
            Triple("France", "Français", R.drawable.ic_flag_french),
            Triple("Norway", "norsk", R.drawable.ic_flag_norway)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            items(languages) { (name, translation, flag) ->

                val isSelected = selectedLanguage == name

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) Color(0xFFEAF2FF)
                            else Color(0xFFF0F0F0)
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) Color(0xFF2F6BDE) else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            selectedLanguage = name
                            AdCache.ensure(AdPlacement.LANGUAGE_NATIVE, activity)
                        }
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Image(
                            painter = painterResource(flag),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    Color(0xFFE0E0E0),
                                    CircleShape
                                ),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {

                            Text(
                                text = name,
                                fontSize = 18.sp,
                                fontFamily = generalSans,
                                color = Color.Black
                            )

                            Text(
                                text = translation,
                                fontSize = 14.sp,
                                fontFamily = generalSans,
                                color = Color.Gray
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedLanguage = name },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF2F6BDE)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔵 BIG NATIVE AD
        val nativeAd: NativeAd? = AdCache.nativeState(AdPlacement.LANGUAGE_NATIVE).value

        if (nativeAd != null) {

            AndroidView(
                factory = { ctx ->

                    val inflater = LayoutInflater.from(ctx)
                    val view =
                        inflater.inflate(R.layout.native_big_ad_layout, null)

                    val adView = view as NativeAdView

                    val headline =
                        adView.findViewById<TextView>(R.id.ad_headline)
                    val body =
                        adView.findViewById<TextView>(R.id.ad_body)
                    val cta =
                        adView.findViewById<Button>(R.id.ad_call_to_action)
                    val icon =
                        adView.findViewById<ImageView>(R.id.ad_icon)
                    val media =
                        adView.findViewById<MediaView>(R.id.ad_media)

                    adView.headlineView = headline
                    adView.bodyView = body
                    adView.callToActionView = cta
                    adView.iconView = icon
                    adView.mediaView = media

                    headline.text = nativeAd.headline ?: ""
                    body.text = nativeAd.body ?: ""
                    cta.text = nativeAd.callToAction ?: ""

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
                    .height(260.dp)
                    .padding(horizontal = 20.dp)
            )

        } else {
            AdShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 20.dp),
                variant = AdShimmerVariant.MEDIA_BLOCK
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}