package com.sms.textmessages.messenger

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.sms.textmessages.messenger.ads.RemoteConfigManager
import com.sms.textmessages.messenger.ui.ads.HomeAdManager
import com.sms.textmessages.messenger.ui.splash.SplashActivity

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
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)

        // Must run before any ad manager's first load attempt - AdMob SDK
        // calls made before this completes are undefined per Google's docs.
        // Callback variant (not the no-arg overload) so initialization is
        // actually verifiable in logcat rather than assumed.
        MobileAds.initialize(this) { status ->
            val adapters = status.adapterStatusMap.entries.joinToString {
                "${it.key}=${it.value.initializationState}"
            }
            Log.d(TAG, "MobileAds.initialize() completed - adapters: $adapters")
        }

        // Preload the app-open ad only once Remote Config has real values
        // activated - homeAppOpenId() otherwise returns the "" default set in
        // RemoteConfigManager.init(), silently no-opping loadAppOpen() on
        // every fresh install / cold start (fetchAndActivate() hasn't had
        // time to complete synchronously this early in Application.onCreate()).
        RemoteConfigManager.init {
            Log.d(TAG, "Remote Config activated - loading initial app-open ad")
            HomeAdManager.loadAppOpen(this)
        }
    }

    override fun onActivityStarted(activity: Activity) {

        // Skip splash
        if (activity is SplashActivity) return

        // Only show when coming from background
        if (activityReferences == 0 && !isActivityChangingConfigurations && !isFullScreenAdInFlight) {

            if (!disableAppOpenAd) {

                HomeAdManager.showAppOpenIfAvailable(activity)

            } else {

                // skip only once
                disableAppOpenAd = false
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