package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.sms.textmessages.messenger.App
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object SettingsAdManager {

    private var interstitialAd: InterstitialAd? = null

    fun load(activity: Activity) {

        if (!RemoteConfigManager.settingsInterstitialEnabled()) return

        val adId = RemoteConfigManager.settingsInterstitialId()
        if (adId.isEmpty()) return

        InterstitialAd.load(
            activity,
            adId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun show(activity: Activity, onFinish: () -> Unit) {

        interstitialAd?.let {

            App.disableAppOpenAd = true

            it.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        App.disableAppOpenAd = false
                        onFinish()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        App.disableAppOpenAd = false
                        onFinish()
                    }
                }

            it.show(activity)

        } ?: onFinish()
    }
}