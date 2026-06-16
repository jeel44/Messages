package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.*
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object ChatBannerAdManager {

    private const val TAG = "CHAT_BANNER"

    // Compose state
    var bannerAdState = mutableStateOf<AdView?>(null)
        private set

    private var currentAdView: AdView? = null

    fun loadBanner(activity: Activity) {

        // 🔥 Check if enabled from RemoteConfig
        if (!RemoteConfigManager.chatBannerEnabled()) {
            Log.d(TAG, "Chat banner disabled")
            bannerAdState.value = null
            return
        }

        val adId = RemoteConfigManager.chatBannerId()

        if (adId.isEmpty()) {
            Log.d(TAG, "Chat banner id empty")
            bannerAdState.value = null
            return
        }

        // Prevent multiple banner creation
        if (currentAdView != null) return

        val adView = AdView(activity)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = adId

        adView.adListener = object : AdListener() {

            override fun onAdLoaded() {
                Log.d(TAG, "Chat banner loaded")
                currentAdView = adView
                bannerAdState.value = adView
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "Chat banner failed: ${error.message}")
                bannerAdState.value = null
            }
        }

        adView.loadAd(AdRequest.Builder().build())
    }

    fun destroyBanner() {
        currentAdView?.destroy()
        currentAdView = null
        bannerAdState.value = null
    }
}