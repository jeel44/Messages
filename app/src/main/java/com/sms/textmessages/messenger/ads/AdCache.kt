package com.sms.textmessages.messenger.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.sms.textmessages.messenger.App
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single source of truth for AdMob load / cache / show.
 *
 * Filter logcat with tag **AdCache** (or `adb logcat -s AdCache`).
 * Event vocabulary:
 * - WARM / ENSURE / CACHE_HIT / SKIP / DEFER / QUEUE
 * - LOAD_START / LOAD_OK / LOAD_FAIL
 * - SHOW / SHOW_SKIP / SHOW_FAIL / DISMISS
 * - RELEASE / RETRY_RC
 */
object AdCache {

    private const val TAG = "AdCache"
    private const val MAX_IN_FLIGHT = 2

    private lateinit var app: Application
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = AtomicInteger(0)
    private val pendingEnsure = ArrayDeque<Pair<AdPlacement, Context>>()
    // ensure() calls that arrived before Remote Config defaults were ready.
    private val deferredUntilRc = ArrayDeque<Pair<AdPlacement, Context>>()

    private val loading = ConcurrentHashMap<AdPlacement, Boolean>()
    private val loadedAt = ConcurrentHashMap<AdPlacement, Long>()

    private val nativeAds = ConcurrentHashMap<AdPlacement, NativeAd?>()
    private val nativeStates = AdPlacement.entries
        .filter { it.format == AdFormat.NATIVE }
        .associateWith { mutableStateOf<NativeAd?>(null) }

    private val bannerViews = ConcurrentHashMap<AdPlacement, AdView?>()
    private val bannerStates = AdPlacement.entries
        .filter { it.format == AdFormat.BANNER }
        .associateWith { mutableStateOf<AdView?>(null) }

    private var appOpenAd: AppOpenAd? = null
    private var appOpenShowing = false

    private val interstitials = ConcurrentHashMap<AdPlacement, InterstitialAd?>()
    private val interstitialShowing = ConcurrentHashMap<AdPlacement, Boolean>()
    private val clickCounts = ConcurrentHashMap<AdPlacement, Int>()
    // AdMob: do not re-show an ad after impression. Cleared on release().
    private val impressed = ConcurrentHashMap<AdPlacement, Boolean>()

    var skipNextAppOpen = false

    fun start(application: Application) {
        app = application
        Log.d(TAG, "START | AdCache bound to Application")
    }

    /**
     * Call-end only — safe on PHONE_STATE cold start.
     * Do NOT pull home/chat ads here; those wait for [warmHome].
     */
    fun warmCallEnd() {
        if (!::app.isInitialized) {
            Log.w(TAG, "WARM_CALL | skipped - AdCache.start() not called")
            return
        }
        if (com.sms.textmessages.messenger.utils.PreferenceManager.isPremiumSubscribed(app)) {
            Log.d(TAG, "WARM_CALL | skipped - premium subscribed")
            return
        }
        Log.d(TAG, "WARM_CALL | begin inFlight=${inFlight.get()} rcReady=${RemoteConfigManager.isReady}")
        drainDeferredUntilRc()
        ensure(AdPlacement.CALL_END_BANNER, app)
        Log.d(TAG, "WARM_CALL | done | ${snapshot()}")
    }

    /**
     * Home / chat placements — call from MainActivity (or GetStarted), never from
     * a call-only process start. Avoids loading 5 unused ads behind the call-end UI.
     */
    fun warmHome() {
        if (!::app.isInitialized) {
            Log.w(TAG, "WARM_HOME | skipped - AdCache.start() not called")
            return
        }
        if (!RemoteConfigManager.isReady) {
            Log.d(TAG, "WARM_HOME | RC not ready - will warm after defaults")
            RemoteConfigManager.init { warmHome() }
            return
        }
        Log.d(TAG, "WARM_HOME | begin inFlight=${inFlight.get()}")
        ensure(AdPlacement.HOME_APP_OPEN, app)
        ensure(AdPlacement.HOME_NATIVE, app)
        ensure(AdPlacement.HOME_BANNER, app)
        ensure(AdPlacement.OPEN_CHAT_INTERSTITIAL, app)
        ensure(AdPlacement.NEW_CHAT_INTERSTITIAL, app)
        Log.d(TAG, "WARM_HOME | queued | ${snapshot()}")
    }

    @Deprecated("Use warmCallEnd() + warmHome()", ReplaceWith("warmCallEnd()"))
    fun warmStartup() {
        warmCallEnd()
        warmHome()
    }

    fun nativeState(placement: AdPlacement): State<NativeAd?> {
        require(placement.format == AdFormat.NATIVE)
        return nativeStates.getValue(placement)
    }

    fun bannerState(placement: AdPlacement): State<AdView?> {
        require(placement.format == AdFormat.BANNER)
        return bannerStates.getValue(placement)
    }

    /**
     * Non-blocking: starts a load if slot is empty, expired, or failed.
     * No-ops if already Ready+fresh or Loading, unless [forceRefresh] is true
     * (used by on-screen banner refresh loops — not the same as AdMob expiry).
     */
    fun ensure(placement: AdPlacement, context: Context, forceRefresh: Boolean = false) {
        if (!RemoteConfigManager.isReady) {
            val appCtx = context.applicationContext
            val added = synchronized(deferredUntilRc) {
                if (deferredUntilRc.none { it.first == placement }) {
                    deferredUntilRc.addLast(placement to appCtx)
                    true
                } else false
            }
            Log.d(
                TAG,
                "DEFER | $placement | RC not ready queued=$added deferred=${deferredUntilRc.size}"
            )
            return
        }
        if (!placement.isEnabled()) {
            Log.d(TAG, "SKIP | $placement | reason=disabled")
            return
        }
        val id = placement.adUnitId()
        if (id.isEmpty()) {
            Log.w(TAG, "SKIP | $placement | reason=empty_ad_unit_id")
            return
        }

        if (!forceRefresh && isFresh(placement)) {
            if (impressed[placement] == true) {
                val bannerStillOnScreen = placement.format == AdFormat.BANNER &&
                    bannerViews[placement]?.parent != null
                // Native stays bound in Compose until release — keep for this screen.
                val nativeStillBound = placement.format == AdFormat.NATIVE &&
                    nativeAds[placement] != null
                if (bannerStillOnScreen || nativeStillBound) {
                    Log.d(
                        TAG,
                        "CACHE_HIT | $placement | impressed=true stillOnScreen=true " +
                            "(keep until detach/forceRefresh)"
                    )
                    return
                }
                Log.d(
                    TAG,
                    "ENSURE | $placement | impressed=true and not on-screen → release + reload"
                )
                release(placement)
            } else {
                Log.d(
                    TAG,
                    "CACHE_HIT | $placement | ageMs=${cacheAgeMs(placement)} " +
                        "expiryMs=${placement.expiryMs} impressed=false ${statusOf(placement)}"
                )
                return
            }
        }
        if (loading[placement] == true) {
            Log.d(TAG, "SKIP | $placement | reason=already_loading inFlight=${inFlight.get()}")
            return
        }

        // AdView prefers an Activity context; other formats are fine with app context.
        val loadCtx = if (placement.format == AdFormat.BANNER && context is Activity) {
            context
        } else {
            context.applicationContext
        }
        val ctxKind = if (loadCtx is Activity) "activity" else "app"
        Log.d(
            TAG,
            "ENSURE | $placement | format=${placement.format} id=...${id.takeLast(6)} " +
                "force=$forceRefresh ctx=$ctxKind inFlight=${inFlight.get()}/${MAX_IN_FLIGHT} " +
                "cached=${statusOf(placement)}"
        )

        if (inFlight.get() >= MAX_IN_FLIGHT) {
            val queued = synchronized(pendingEnsure) {
                if (pendingEnsure.none { it.first == placement }) {
                    pendingEnsure.addLast(placement to loadCtx)
                    true
                } else false
            }
            Log.d(
                TAG,
                "QUEUE | $placement | reason=max_in_flight queued=$queued pending=${pendingEnsure.size}"
            )
            return
        }

        startLoad(placement, loadCtx)
    }

    fun release(placement: AdPlacement) {
        val hadCache = isFresh(placement) || loading[placement] == true
        val wasImpressed = impressed.remove(placement) == true
        when (placement.format) {
            AdFormat.NATIVE -> {
                nativeAds.remove(placement)?.destroy()
                setNative(placement, null)
                loadedAt.remove(placement)
            }
            AdFormat.BANNER -> {
                destroyBanner(placement)
                loadedAt.remove(placement)
            }
            AdFormat.INTERSTITIAL -> {
                interstitials.remove(placement)
                loadedAt.remove(placement)
            }
            AdFormat.APP_OPEN -> {
                appOpenAd = null
                loadedAt.remove(placement)
            }
        }
        loading[placement] = false
        Log.d(
            TAG,
            "RELEASE | $placement | hadCacheOrLoading=$hadCache wasImpressed=$wasImpressed"
        )
    }

    /**
     * Remove banner from the overlay/Activity view tree.
     *
     * - If it already recorded an impression → destroy it and preload a **new**
     *   ad (AdMob: never re-show an impressed ad).
     * - If not yet impressed (loaded but user closed early) → keep in cache for
     *   the next call so we can still get a first impression.
     */
    fun detachBanner(placement: AdPlacement) {
        require(placement.format == AdFormat.BANNER)
        bannerViews[placement]?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        val wasImpressed = impressed[placement] == true
        if (wasImpressed) {
            Log.d(TAG, "DETACH | $placement | impressed=true → release + preload fresh")
            release(placement)
            if (::app.isInitialized) ensure(placement, app)
        } else {
            Log.d(
                TAG,
                "DETACH | $placement | impressed=false keptInCache=${isFresh(placement)} " +
                    "ageMs=${cacheAgeMs(placement)} (ok to show once next time)"
            )
        }
    }

    /** True when a cached creative has already been counted — must not show again. */
    fun wasImpressed(placement: AdPlacement): Boolean = impressed[placement] == true

    private fun markImpressed(placement: AdPlacement) {
        impressed[placement] = true
        Log.d(TAG, "IMPRESSION | $placement | spent=true (will not re-show this creative)")
    }

    fun showAppOpen(activity: Activity) {
        if (skipNextAppOpen) {
            skipNextAppOpen = false
            Log.d(TAG, "SHOW_SKIP | HOME_APP_OPEN | reason=skipNextAppOpen")
            return
        }
        if (appOpenShowing) {
            Log.d(TAG, "SHOW_SKIP | HOME_APP_OPEN | reason=already_showing")
            return
        }
        val ad = appOpenAd
        if (ad == null || !isFresh(AdPlacement.HOME_APP_OPEN)) {
            Log.d(
                TAG,
                "SHOW_SKIP | HOME_APP_OPEN | reason=not_cached " +
                    "adNull=${ad == null} fresh=${isFresh(AdPlacement.HOME_APP_OPEN)} → ensure"
            )
            ensure(AdPlacement.HOME_APP_OPEN, activity.application)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "DISMISS | HOME_APP_OPEN | → reload")
                appOpenShowing = false
                appOpenAd = null
                loadedAt.remove(AdPlacement.HOME_APP_OPEN)
                ensure(AdPlacement.HOME_APP_OPEN, activity.application)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    TAG,
                    "SHOW_FAIL | HOME_APP_OPEN | code=${adError.code} msg=${adError.message}"
                )
                appOpenShowing = false
                appOpenAd = null
                loadedAt.remove(AdPlacement.HOME_APP_OPEN)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "SHOW | HOME_APP_OPEN | onAdShowedFullScreenContent")
            }

            override fun onAdImpression() {
                markImpressed(AdPlacement.HOME_APP_OPEN)
            }

            override fun onAdClicked() {
                Log.d(TAG, "CLICK | HOME_APP_OPEN")
            }
        }
        appOpenShowing = true
        Log.d(
            TAG,
            "SHOW | HOME_APP_OPEN | from cache ageMs=${cacheAgeMs(AdPlacement.HOME_APP_OPEN)}"
        )
        ad.show(activity)
    }

    /**
     * Click-gated interstitial (open chat / new chat / chat back).
     * Invokes [onFinish] either after the ad or immediately when under the limit.
     */
    fun onClickGated(activity: Activity, placement: AdPlacement, onFinish: () -> Unit) {
        require(placement.format == AdFormat.INTERSTITIAL)
        val count = (clickCounts[placement] ?: 0) + 1
        clickCounts[placement] = count
        val limit = placement.clickLimit().coerceAtLeast(1)
        if (count >= limit) {
            Log.d(TAG, "GATE | $placement | click $count/$limit → SHOW")
            clickCounts[placement] = 0
            showInterstitial(activity, placement, onFinish)
        } else {
            Log.d(TAG, "GATE | $placement | click $count/$limit → pass (no ad)")
            onFinish()
        }
    }

    fun showInterstitial(activity: Activity, placement: AdPlacement, onFinish: () -> Unit) {
        require(placement.format == AdFormat.INTERSTITIAL)
        if (interstitialShowing[placement] == true) {
            Log.d(TAG, "SHOW_SKIP | $placement | reason=already_showing → onFinish")
            onFinish()
            return
        }
        val ad = interstitials[placement]
        if (ad == null || !isFresh(placement)) {
            Log.d(
                TAG,
                "SHOW_SKIP | $placement | reason=not_cached " +
                    "adNull=${ad == null} fresh=${isFresh(placement)} → ensure + onFinish"
            )
            ensure(placement, activity)
            onFinish()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "DISMISS | $placement | → reload")
                interstitialShowing[placement] = false
                App.isFullScreenAdInFlight = false
                interstitials.remove(placement)
                loadedAt.remove(placement)
                ensure(placement, activity)
                onFinish()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    TAG,
                    "SHOW_FAIL | $placement | code=${adError.code} msg=${adError.message}"
                )
                interstitialShowing[placement] = false
                App.isFullScreenAdInFlight = false
                interstitials.remove(placement)
                loadedAt.remove(placement)
                onFinish()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "SHOW | $placement | onAdShowedFullScreenContent")
            }

            override fun onAdImpression() {
                markImpressed(placement)
            }

            override fun onAdClicked() {
                Log.d(TAG, "CLICK | $placement")
            }
        }
        interstitialShowing[placement] = true
        App.isFullScreenAdInFlight = true
        Log.d(TAG, "SHOW | $placement | from cache ageMs=${cacheAgeMs(placement)}")
        ad.show(activity)
    }

    // ---- internals ----

    private fun drainDeferredUntilRc() {
        val deferred = synchronized(deferredUntilRc) {
            if (deferredUntilRc.isEmpty()) return
            deferredUntilRc.toList().also { deferredUntilRc.clear() }
        }
        Log.d(TAG, "RETRY_RC | draining ${deferred.size} deferred ensure(s)")
        deferred.forEach { (placement, ctx) ->
            Log.d(TAG, "RETRY_RC | $placement")
            ensure(placement, ctx)
        }
    }

    private fun isFresh(placement: AdPlacement): Boolean {
        val at = loadedAt[placement] ?: return false
        if (System.currentTimeMillis() - at > placement.expiryMs) return false
        return when (placement.format) {
            AdFormat.APP_OPEN -> appOpenAd != null
            AdFormat.INTERSTITIAL -> interstitials[placement] != null
            AdFormat.NATIVE -> nativeAds[placement] != null
            AdFormat.BANNER -> bannerViews[placement] != null
        }
    }

    private fun cacheAgeMs(placement: AdPlacement): Long {
        val at = loadedAt[placement] ?: return -1L
        return System.currentTimeMillis() - at
    }

    private fun statusOf(placement: AdPlacement): String {
        val loadingNow = loading[placement] == true
        val fresh = isFresh(placement)
        val age = cacheAgeMs(placement)
        return when {
            loadingNow -> "status=LOADING"
            fresh -> "status=CACHED ageMs=$age"
            age >= 0 -> "status=EXPIRED ageMs=$age"
            else -> "status=EMPTY"
        }
    }

    private fun snapshot(): String {
        val cached = AdPlacement.entries.filter { isFresh(it) }.joinToString(",") { it.name }
        val loadingNow = AdPlacement.entries.filter { loading[it] == true }.joinToString(",") { it.name }
        return "cached=[${cached.ifEmpty { "-" }}] loading=[${loadingNow.ifEmpty { "-" }}] " +
            "inFlight=${inFlight.get()} pending=${pendingEnsure.size} deferredRc=${deferredUntilRc.size}"
    }

    private fun startLoad(placement: AdPlacement, context: Context) {
        loading[placement] = true
        val n = inFlight.incrementAndGet()
        Log.d(
            TAG,
            "LOAD_START | $placement | format=${placement.format} " +
                "id=...${placement.adUnitId().takeLast(6)} inFlight=$n/${MAX_IN_FLIGHT}"
        )

        when (placement.format) {
            AdFormat.APP_OPEN -> loadAppOpen(placement, context)
            AdFormat.INTERSTITIAL -> loadInterstitial(placement, context)
            AdFormat.NATIVE -> loadNative(placement, context)
            AdFormat.BANNER -> loadBanner(placement, context)
        }
    }

    private fun finishLoad() {
        inFlight.decrementAndGet()
        mainHandler.post { drainPending() }
    }

    private fun drainPending() {
        while (inFlight.get() < MAX_IN_FLIGHT) {
            val next = synchronized(pendingEnsure) {
                if (pendingEnsure.isEmpty()) return
                pendingEnsure.removeFirst()
            }
            if (isFresh(next.first) || loading[next.first] == true) {
                Log.d(
                    TAG,
                    "QUEUE | drop ${next.first} | already ${statusOf(next.first)} " +
                        "loading=${loading[next.first] == true}"
                )
                continue
            }
            Log.d(TAG, "QUEUE | drain → LOAD_START ${next.first} pendingLeft=${pendingEnsure.size}")
            startLoad(next.first, next.second)
        }
    }

    private fun markLoaded(placement: AdPlacement) {
        loadedAt[placement] = System.currentTimeMillis()
        loading[placement] = false
        Log.d(
            TAG,
            "LOAD_OK | $placement | cached=true expiryMs=${placement.expiryMs} | ${snapshot()}"
        )
        finishLoad()
    }

    private fun markFailed(placement: AdPlacement) {
        loadedAt.remove(placement)
        loading[placement] = false
        finishLoad()
    }

    private fun loadAppOpen(placement: AdPlacement, context: Context) {
        val id = placement.adUnitId()
        AppOpenAd.load(
            context,
            id,
            AdRequest.Builder().build(),
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    markLoaded(placement)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(
                        TAG,
                        "LOAD_FAIL | $placement | code=${error.code} domain=${error.domain} " +
                            "msg=${error.message}"
                    )
                    appOpenAd = null
                    markFailed(placement)
                }
            }
        )
    }

    private fun loadInterstitial(placement: AdPlacement, context: Context) {
        val id = placement.adUnitId()
        InterstitialAd.load(
            context,
            id,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitials[placement] = ad
                    markLoaded(placement)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(
                        TAG,
                        "LOAD_FAIL | $placement | code=${error.code} domain=${error.domain} " +
                            "msg=${error.message}"
                    )
                    interstitials.remove(placement)
                    markFailed(placement)
                }
            }
        )
    }

    private fun loadNative(placement: AdPlacement, context: Context) {
        val id = placement.adUnitId()
        val adLoader = AdLoader.Builder(context, id)
            .forNativeAd { ad ->
                nativeAds[placement]?.destroy()
                nativeAds[placement] = ad
                setNative(placement, ad)
                markLoaded(placement)
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(
                        TAG,
                        "LOAD_FAIL | $placement | code=${error.code} domain=${error.domain} " +
                            "msg=${error.message}"
                    )
                    nativeAds[placement]?.destroy()
                    nativeAds.remove(placement)
                    setNative(placement, null)
                    markFailed(placement)
                }

                override fun onAdImpression() {
                    markImpressed(placement)
                }

                override fun onAdClicked() {
                    Log.d(TAG, "CLICK | $placement | native")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadBanner(placement: AdPlacement, context: Context) {
        val id = placement.adUnitId()
        // Destroy previous before starting a new request
        destroyBanner(placement)

        val adView = AdView(context)
        // Call-end uses Medium Rectangle (300×250 "square"); other slots stay 320×50.
        val size = if (placement == AdPlacement.CALL_END_BANNER) {
            AdSize.MEDIUM_RECTANGLE
        } else {
            AdSize.BANNER
        }
        adView.setAdSize(size)
        adView.adUnitId = id
        Log.d(TAG, "BANNER_CFG | $placement | size=${size.width}x${size.height} id=...${id.takeLast(6)}")
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                bannerViews[placement] = adView
                setBanner(placement, adView)
                markLoaded(placement)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(
                    TAG,
                    "LOAD_FAIL | $placement | code=${error.code} domain=${error.domain} " +
                        "msg=${error.message}"
                )
                try {
                    adView.destroy()
                } catch (_: Exception) {
                }
                bannerViews.remove(placement)
                setBanner(placement, null)
                markFailed(placement)
            }

            override fun onAdImpression() {
                markImpressed(placement)
            }

            override fun onAdClicked() {
                Log.d(TAG, "CLICK | $placement | banner")
            }

            override fun onAdOpened() {
                Log.d(TAG, "OPEN | $placement | banner")
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun destroyBanner(placement: AdPlacement) {
        bannerViews.remove(placement)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            try {
                view.destroy()
            } catch (_: Exception) {
            }
        }
        setBanner(placement, null)
    }

    private fun setNative(placement: AdPlacement, ad: NativeAd?) {
        mainHandler.post {
            nativeStates[placement]?.value = ad
            Log.d(TAG, "UI_BIND | $placement | native bound=${ad != null}")
        }
    }

    private fun setBanner(placement: AdPlacement, view: AdView?) {
        mainHandler.post {
            bannerStates[placement]?.value = view
            Log.d(TAG, "UI_BIND | $placement | banner bound=${view != null}")
        }
    }
}
