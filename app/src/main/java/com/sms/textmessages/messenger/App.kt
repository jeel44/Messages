package com.sms.textmessages.messenger

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sms.textmessages.messenger.ui.ads.HomeAdManager
import com.sms.textmessages.messenger.ui.splash.SplashActivity

class App : Application(), Application.ActivityLifecycleCallbacks {

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    companion object {
        var disableAppOpenAd = false
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)

        // preload app open
        HomeAdManager.loadAppOpen(this)
    }

    override fun onActivityStarted(activity: Activity) {

        // Skip splash
        if (activity is SplashActivity) return

        // Only show when coming from background
        if (activityReferences == 0 && !isActivityChangingConfigurations) {

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