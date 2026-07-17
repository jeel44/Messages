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

    // Blocks a second concurrent load while one is already in flight - does
    // NOT block future reloads once a load succeeds (that used to be
    // `if (currentAdView != null) return`, which made every loadBanner() call
    // after the first a permanent no-op for the rest of the process).
    private var isLoading = false

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

        if (isLoading) return

        // Reload means destroy old, then load new - not load new alongside
        // old, and not refuse to load new.
        if (currentAdView != null) {
            destroyBanner()
        }

        isLoading = true

        val adView = AdView(activity)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = adId

        adView.adListener = object : AdListener() {

            override fun onAdLoaded() {
                Log.d(TAG, "Chat banner loaded")
                isLoading = false
                currentAdView = adView
                bannerAdState.value = adView
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "Chat banner failed: code=${error.code} domain=${error.domain} message=${error.message}")
                isLoading = false
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