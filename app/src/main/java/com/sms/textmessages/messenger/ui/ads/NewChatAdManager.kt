package com.sms.textmessages.messenger.ui.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.sms.textmessages.messenger.App
import com.sms.textmessages.messenger.ads.RemoteConfigManager

object NewChatAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isAdShowing = false
    private var clickCount = 0

    // ---------------- LOAD AD ----------------

    fun load(activity: Activity) {

        if (!RemoteConfigManager.newChatInterstitialEnabled())
            return

        val adId = RemoteConfigManager.newChatInterstitialId()

        if (adId.isEmpty())
            return

        InterstitialAd.load(
            activity,
            adId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {

                    Log.d("ADS", "NewChat Interstitial Loaded")

                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {

                    Log.d("ADS", "NewChat Interstitial Failed: ${error.message}")

                    interstitialAd = null
                }
            }
        )
    }

    // ---------------- CLICK LOGIC ----------------

    fun onClick(activity: Activity, onFinish: () -> Unit) {

        clickCount++

        val limit = RemoteConfigManager.newChatClickLimit()

        Log.d("ADS", "NewChat Click: $clickCount / $limit")

        if (clickCount >= limit) {

            clickCount = 0

            show(activity, onFinish)

        } else {

            onFinish()
        }
    }

    // ---------------- SHOW AD ----------------

    private fun show(activity: Activity, onFinish: () -> Unit) {

        if (isAdShowing || interstitialAd == null) {

            onFinish()
            return
        }

        // 🚫 Disable App Open Ad
        App.disableAppOpenAd = true

        interstitialAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {

                    isAdShowing = false
                    interstitialAd = null

                    // ✅ Re-enable App Open Ad
                    App.disableAppOpenAd = false

                    load(activity)

                    onFinish()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {

                    isAdShowing = false

                    // ✅ Re-enable App Open Ad
                    App.disableAppOpenAd = false

                    onFinish()
                }
            }

        isAdShowing = true
        interstitialAd?.show(activity)
    }
}