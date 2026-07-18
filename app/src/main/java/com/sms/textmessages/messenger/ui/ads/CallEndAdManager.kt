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
// Context rather than Activity since the caller here is CallEndOverlayManager,
// not an Activity - AdLoader.Builder only ever needs a Context.
object CallEndAdManager {

    // Same tag as CallStateListener/CallEndOverlayManager for this debugging
    // pass - filter logcat on this single tag to trace a call end top to bottom.
    private const val TAG = "CALLEND_DEBUG"

    var nativeAdState = mutableStateOf<NativeAd?>(null)

    fun loadNative(context: Context) {

        val adsEnabled = RemoteConfigManager.callEndAdsEnabled()
        val adId = RemoteConfigManager.callEndNativeId()

        Log.d(TAG, "CallEndAdManager.loadNative: callEndAdsEnabled=$adsEnabled callEndNativeId=${adId.ifEmpty { "empty" }}")

        if (!adsEnabled) {
            Log.d(TAG, "CallEndAdManager.loadNative: skipped - call_end_ads_enabled is false")
            return
        }

        if (adId.isEmpty()) {
            Log.d(TAG, "CallEndAdManager.loadNative: skipped - call_end_ad_id is empty (not set in Remote Config)")
            return
        }

        val adLoader = AdLoader.Builder(context, adId)
            .forNativeAd { ad ->
                Log.d(TAG, "CallEndAdManager.loadNative: ad loaded successfully")
                nativeAdState.value?.destroy()
                nativeAdState.value = ad
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "CallEndAdManager.loadNative: Failed: code=${error.code} domain=${error.domain} message=${error.message}")
                    nativeAdState.value?.destroy()
                    nativeAdState.value = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // Called from CallEndOverlayManager's teardown() - unlike HomeAdManager's
    // ad (which lives for the whole app process), this ad is short-lived per
    // call, so it must be explicitly released instead of relying on process
    // death.
    fun destroy() {
        nativeAdState.value?.destroy()
        nativeAdState.value = null
    }
}
