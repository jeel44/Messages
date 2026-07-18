package com.sms.textmessages.messenger.ui.ads

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.sms.textmessages.messenger.ads.RemoteConfigManager

// Single native ad slot for the post-call "call end" overlay - mirrors
// HomeAdManager's loadNative() (same destroy-before-replace pattern, own
// call_end_* Remote Config keys instead of home_native_*). Takes a plain
// Context rather than Activity since the caller here is CallEndOverlayService,
// not an Activity - AdLoader.Builder only ever needs a Context.
object CallEndAdManager {

    var nativeAdState = mutableStateOf<NativeAd?>(null)

    fun loadNative(context: Context) {

        if (!RemoteConfigManager.callEndAdsEnabled()) return

        val adId = RemoteConfigManager.callEndNativeId()
        if (adId.isEmpty()) return

        val adLoader = AdLoader.Builder(context, adId)
            .forNativeAd { ad ->
                nativeAdState.value?.destroy()
                nativeAdState.value = ad
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("CALL_END_NATIVE", "Failed: code=${error.code} domain=${error.domain} message=${error.message}")
                    nativeAdState.value?.destroy()
                    nativeAdState.value = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // Called from CallEndOverlayService.onDestroy() - unlike HomeAdManager's
    // ad (which lives for the whole app process), this service is short-lived
    // per call, so its ad must be explicitly released instead of relying on
    // process death.
    fun destroy() {
        nativeAdState.value?.destroy()
        nativeAdState.value = null
    }
}
