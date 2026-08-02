package com.sms.textmessages.messenger

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdUnitIds
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.receiver.CallLogCallEndObserver
import com.sms.textmessages.messenger.ui.splash.SplashActivity
import com.sms.textmessages.messenger.utils.CallEndMetrics
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class App : Application(), Application.ActivityLifecycleCallbacks {

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    companion object {
        var disableAppOpenAd = false

        // Interstitials show in their own internal AdMob Activity, which drives
        // MainActivity through onStop -> onStart even though the user never left
        // the app to the background - this flag lets onActivityStarted tell that
        // case apart from a real return from background.
        var isFullScreenAdInFlight = false

        private const val TAG = "AdsInit"
        private val adsInitStarted = AtomicBoolean(false)
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)

        // Keep process-bind work minimal. PHONE_STATE / call-screening often
        // cold-start this process; doing AdMob here caused
        // "failed to complete startup" ANRs under CPU pressure.
        AdUnitIds.init(this)
        AdCache.start(this)

        // Return from onCreate before anything heavier so AMS can finish attach.
        Handler(Looper.getMainLooper()).post {
            CallLogCallEndObserver.registerIfNeeded(this)
            Log.d("CALLEND_DEBUG", "App.onCreate metrics ${CallEndMetrics.summary(this)}")
            scheduleAdsInit()
        }
    }

    private fun scheduleAdsInit() {
        if (!adsInitStarted.compareAndSet(false, true)) return

        // Google recommends MobileAds.initialize off the main thread — sync
        // adapter work on main is a known ANR source.
        Executors.newSingleThreadExecutor().execute {
            MobileAds.initialize(this) { status ->
                val adapters = status.adapterStatusMap.entries.joinToString {
                    "${it.key}=${it.value.initializationState}"
                }
                Log.d(TAG, "MobileAds.initialize() completed - adapters: $adapters")
            }
        }

        RemoteConfigManager.init {
            Log.d(TAG, "Remote Config activated - warming call-end AdCache only")
            AdCache.warmCallEnd()
        }
    }

    override fun onActivityStarted(activity: Activity) {

        // Splash is only a zero-delay router — never show app-open on it,
        // but still count it so activityReferences stays accurate when it
        // immediately finishes into MainActivity/GetStarted.
        if (activity !is SplashActivity) {
            if (activityReferences == 0 && !isActivityChangingConfigurations && !isFullScreenAdInFlight) {

                if (!disableAppOpenAd) {
                    AdCache.showAppOpen(activity)
                } else {
                    // skip only once (cold start after Splash routes to Main)
                    disableAppOpenAd = false
                }
            }
        }

        activityReferences++
    }

    override fun onActivityStopped(activity: Activity) {

        isActivityChangingConfigurations = activity.isChangingConfigurations

        activityReferences--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
