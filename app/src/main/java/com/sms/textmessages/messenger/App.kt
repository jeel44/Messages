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
            preloadContacts()
            preloadThreads()
            Log.d("CALLEND_DEBUG", "App.onCreate metrics ${CallEndMetrics.summary(this)}")
            scheduleAdsInit()
        }
    }

    private fun preloadContacts() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )
                cursor?.use {
                    val numberIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (numberIdx != -1 && nameIdx != -1) {
                        while (it.moveToNext()) {
                            val number = it.getString(numberIdx) ?: continue
                            val name = it.getString(nameIdx) ?: continue
                            // Strip formatting ("+91 79900-96382") before the
                            // last-10 key, or it never matches the thread-side
                            // phone.takeLast(10) lookup in getContactName().
                            val normalized = number.filter { c -> c.isDigit() || c == '+' }.takeLast(10)
                            if (normalized.isNotBlank()) {
                                com.sms.textmessages.messenger.ui.home.contactNameCache[normalized] = name
                                com.sms.textmessages.messenger.ui.home.contactNameCache[number] = name
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactPreload", "Failed to preload contacts", e)
            } finally {
                Log.d("ContactPreload", "contactPreloadDone entries=${com.sms.textmessages.messenger.ui.home.contactNameCache.size}")
                com.sms.textmessages.messenger.ui.home.contactPreloadDone.complete(Unit)
            }
        }
    }

    // Opens Room and reads the inbox rows before Home is shown, so
    // HomeViewModel can start from them instead of waiting on a cold
    // database open + first query after the screen appears.
    private fun preloadThreads() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val threads = com.sms.textmessages.messenger.data.db.AppDatabase
                    .getDatabase(this).threadDao().getThreadsSnapshot()
                // Don't clobber a fresher list HomeViewModel may already have written.
                if (com.sms.textmessages.messenger.ui.home.SmsRepository.threadSnapshot == null) {
                    com.sms.textmessages.messenger.ui.home.SmsRepository.threadSnapshot = threads
                }
                Log.d("ThreadPreload", "threadSnapshot ready size=${threads.size}")
            } catch (e: Exception) {
                Log.e("ThreadPreload", "Failed to preload threads", e)
            }
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
