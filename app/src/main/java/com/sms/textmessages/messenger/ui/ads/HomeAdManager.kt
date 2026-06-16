package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object HomeAdManager {

    var nativeAdState = mutableStateOf<NativeAd?>(null)
    var bannerLoaded = mutableStateOf(false)
    var splashAdJustShown = false

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var isSplashAdAlreadyShown = false

    fun setSplashAdShown() {
        isSplashAdAlreadyShown = true
    }

    // ---------------- NATIVE ----------------
    fun loadNative(activity: Activity) {

        if (!RemoteConfigManager.homeNativeEnabled()) return

        val adId = RemoteConfigManager.homeNativeId()
        if (adId.isEmpty()) return

        val adLoader = AdLoader.Builder(activity, adId)
            .forNativeAd { ad ->
                nativeAdState.value = ad
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAdState.value = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // ---------------- APP OPEN ----------------
    fun loadAppOpen(application: Application) {

        if (!RemoteConfigManager.homeAppOpenEnabled()) return

        val adId = RemoteConfigManager.homeAppOpenId()
        if (adId.isEmpty()) return

        AppOpenAd.load(
            application,
            adId,
            AdRequest.Builder().build(),
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {

                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                }
            }
        )
    }

    fun showAppOpenIfAvailable(activity: Activity) {

        if (splashAdJustShown) {
            splashAdJustShown = false
            return
        }

        if (isShowingAd || appOpenAd == null) return

        appOpenAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    isShowingAd = false
                    appOpenAd = null
                    loadAppOpen(activity.application)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingAd = false
                }
            }

        isShowingAd = true
        appOpenAd?.show(activity)
    }
}