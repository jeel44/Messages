package com.sms.textmessages.messenger.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object GetStartedAdManager {

    var nativeAdState by mutableStateOf<NativeAd?>(null)
        private set

    fun loadNativeAd(activity: Activity) {

        // Ads disabled from remote config
        if (!RemoteConfigManager.getStartedAdsEnabled()) {
            nativeAdState?.destroy()
            nativeAdState = null
            return
        }

        val adId = RemoteConfigManager.getStartedNativeId()

        // No ad ID
        if (adId.isEmpty()) {
            nativeAdState?.destroy()
            nativeAdState = null
            return
        }

        // Destroy previous ad to prevent memory leak
        nativeAdState?.destroy()
        nativeAdState = null

        val adLoader = AdLoader.Builder(activity, adId)

            .forNativeAd { ad ->

                Log.d("ADS", "GetStarted Native Loaded")

                nativeAdState = ad
            }

            .withAdListener(object : AdListener() {

                override fun onAdFailedToLoad(error: LoadAdError) {

                    Log.d("ADS", "GetStarted Native Failed: code=${error.code} domain=${error.domain} message=${error.message}")

                    nativeAdState = null
                }
            })

            .withNativeAdOptions(
                NativeAdOptions.Builder().build()
            )

            .build()

        adLoader.loadAd(
            AdRequest.Builder().build()
        )
    }
}