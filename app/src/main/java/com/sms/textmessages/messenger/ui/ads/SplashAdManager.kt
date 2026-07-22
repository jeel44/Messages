package com.sms.textmessages.messenger.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.sms.textmessages.messenger.ui.ads.HomeAdManager

object SplashAdManager {

    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null

    // Compose state for Native
    var nativeAdState by mutableStateOf<NativeAd?>(null)
        private set

    private var isAdShowing = false
    private var isAdAlreadyShown = false

    fun loadAds(activity: Activity) {

        if (!RemoteConfigManager.splashAdsEnabled()) {
            Log.d("ADS", "Splash ads disabled")
            return
        }

        val appOpenId = RemoteConfigManager.splashAppOpenId()
        val interstitialId = RemoteConfigManager.splashInterstitialId()
        val nativeId = RemoteConfigManager.splashNativeId()

        // ---------------- APP OPEN ----------------
        if (appOpenId.isNotEmpty()) {

            AppOpenAd.load(
                activity,
                appOpenId,
                AdRequest.Builder().build(),
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                object : AppOpenAd.AppOpenAdLoadCallback() {

                    override fun onAdLoaded(ad: AppOpenAd) {
                        Log.d("ADS", "Splash AppOpen Loaded")
                        appOpenAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d("ADS", "Splash AppOpen Failed: code=${error.code} domain=${error.domain} message=${error.message}")
                        appOpenAd = null
                    }
                }
            )
        }

        // ---------------- INTERSTITIAL ----------------
        if (interstitialId.isNotEmpty()) {

            InterstitialAd.load(
                activity,
                interstitialId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d("ADS", "Splash Interstitial Loaded")
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d("ADS", "Splash Interstitial Failed: code=${error.code} domain=${error.domain} message=${error.message}")
                        interstitialAd = null
                    }
                }
            )
        }

        // ---------------- NATIVE ----------------
        if (nativeId.isNotEmpty()) {

            val adLoader = AdLoader.Builder(activity, nativeId)
                .forNativeAd { ad ->
                    nativeAdState = ad
                }
                .withAdListener(object : AdListener() {

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        nativeAdState = null
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder().build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    // ---------------- SHOW AD ----------------
    fun showAdIfAvailable(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onAdFailed: () -> Unit
    ) {

        // TEMPORARY DEBUG LOGGING - diagnosing "only animation shows" splash
        // regression: if isAdShowing is somehow stuck true from a prior
        // splash (resetAdState() not called / stale state), this returns
        // here without calling onAdDismissed() or onAdFailed(), so
        // SplashActivity.goToHome() never runs and the UI is stranded on the
        // now-finished progress animation.
        Log.d("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} isAdAlreadyShown=$isAdAlreadyShown isAdShowing=$isAdShowing appOpenAdLoaded=${appOpenAd != null} interstitialAdLoaded=${interstitialAd != null}")

        // Prevent double ad
        if (isAdAlreadyShown) {
            Log.d("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} isAdAlreadyShown - calling onAdDismissed() and returning")
            onAdDismissed()
            return
        }

        if (isAdShowing) {
            Log.w("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} isAdShowing already true - returning WITHOUT calling onAdDismissed/onAdFailed (possible stuck-splash cause)")
            return
        }

        // Priority 1 → App Open
        appOpenAd?.let { ad ->

            isAdShowing = true
            isAdAlreadyShown = true

            ad.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        isAdShowing = false
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        isAdShowing = false
                        onAdFailed()
                    }
                }

            Log.d("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} showing AppOpen ad")
            ad.show(activity)
            return
        }

        // Priority 2 → Interstitial
        interstitialAd?.let { ad ->

            isAdShowing = true
            isAdAlreadyShown = true

            ad.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        isAdShowing = false
                        HomeAdManager.splashAdJustShown = true
                        onAdDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        isAdShowing = false
                        onAdFailed()
                    }
                }

            Log.d("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} showing Interstitial ad")
            ad.show(activity)
            return
        }

        Log.d("ADS", "showAdIfAvailable: ts=${System.currentTimeMillis()} no ad loaded (appOpenAd and interstitialAd both null) - calling onAdFailed()")
        onAdFailed()
    }

    // Reset when app goes background
    fun resetAdState() {
        isAdAlreadyShown = false
    }
}