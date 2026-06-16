package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.AdLoader
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object LanguageAdManager {

    var nativeAdState = mutableStateOf<NativeAd?>(null)
        private set

    fun loadAd(activity: Activity) {

        if (!RemoteConfigManager.languageAdsEnabled()) return

        val adId = RemoteConfigManager.languageNativeId()

        if (adId.isEmpty()) return

        val adLoader = AdLoader.Builder(activity, adId)
            .forNativeAd { ad ->
                Log.d("LANGUAGE_AD", "Loaded")
                nativeAdState.value = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("LANGUAGE_AD", "Failed: ${error.message}")
                    nativeAdState.value = null
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}