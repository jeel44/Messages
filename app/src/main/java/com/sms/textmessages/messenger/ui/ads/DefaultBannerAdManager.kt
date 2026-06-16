package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.*
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object DefaultBannerAdManager {

    var bannerAdState = mutableStateOf<AdView?>(null)
        private set

    fun loadBanner(activity: Activity) {

        // 🔥 Check Remote Config if banner enabled
        if (!RemoteConfigManager.defaultBannerEnabled()) {
            bannerAdState.value = null
            return
        }

        val adId = RemoteConfigManager.defaultBannerId()

        if (adId.isEmpty()) {
            bannerAdState.value = null
            return
        }

        val adView = AdView(activity)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = adId

        adView.adListener = object : AdListener() {

            override fun onAdLoaded() {
                Log.d("DEFAULT_BANNER", "Banner Loaded")
                bannerAdState.value = adView
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d("DEFAULT_BANNER", "Failed: ${error.message}")
                bannerAdState.value = null
            }
        }

        adView.loadAd(AdRequest.Builder().build())
    }
}