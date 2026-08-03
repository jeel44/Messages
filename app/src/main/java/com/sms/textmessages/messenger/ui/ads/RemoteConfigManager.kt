package com.sms.textmessages.messenger.ads

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Remote Config for ad **enable flags** and click limits only.
 * Ad unit IDs live in strings.xml via [AdUnitIds] — not here.
 */
object RemoteConfigManager {

    private const val TAG = "RemoteConfig"

    private val remoteConfig: FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance()

    var isReady by mutableStateOf(false)
        private set

    private var initStarted = false
    private val pendingCompletes = mutableListOf<() -> Unit>()

    fun init(onComplete: () -> Unit = {}) {

        if (isReady) {
            onComplete()
            return
        }

        pendingCompletes.add(onComplete)

        if (initStarted) return
        initStarted = true

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(10)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(
            "splash_ads_enabled" to true,
            "get_started_ads_enabled" to true,
            "language_ads_enabled" to true,
            "default_banner_enabled" to false,
            "home_native_enabled" to true,
            "home_banner_enabled" to true,
            "home_app_open_enabled" to true,
            "settings_interstitial_enabled" to true,
            "settings_click_limit" to 2L,
            "new_chat_interstitial_enabled" to true,
            "new_chat_click_limit" to 2L,
            "chat_banner_enabled" to true,
            "chat_back_interstitial_enabled" to true,
            "chat_back_click_limit" to 3L,
            "open_chat_interstitial_enabled" to true,
            "open_chat_click_limit" to 3L,
            "call_end_ads_enabled" to true,
            "after_call_screen_enabled" to true,
            "after_call_post_call_path_enabled" to true
        )

        // Mark ready as soon as defaults are applied so call-end / cold-start
        // overlays can load ads immediately. Do NOT wait on network fetch —
        // that delayed isReady by ~5s and left CALL_END_BANNER on shimmer forever.
        remoteConfig.setDefaultsAsync(defaults)
            .addOnCompleteListener { defaultsTask ->
                if (!defaultsTask.isSuccessful) {
                    Log.e(TAG, "setDefaultsAsync failed", defaultsTask.exception)
                }
                markReadyAndFlush()
                logFlagSnapshot("after_defaults")
                remoteConfig.fetchAndActivate()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Remote config fetch complete - activated=${task.result}")
                            if (task.result == true) {
                                logFlagSnapshot("after_activate")
                                // Stale Firebase templates often ship every *_enabled=false with
                                // empty ad unit IDs after IDs moved to strings.xml — that bricks
                                // monetization. Prefer in-app defaults in that case.
                                if (looksLikeStaleAllOffTemplate()) {
                                    Log.w(
                                        TAG,
                                        "Ignoring stale Remote Config (all ads off + empty unit IDs) — " +
                                            "re-applying in-app defaults. Fix flags in Firebase console."
                                    )
                                    remoteConfig.reset().addOnCompleteListener {
                                        remoteConfig.setDefaultsAsync(defaults)
                                            .addOnCompleteListener {
                                                logFlagSnapshot("after_stale_reset")
                                                AdCache.warmCallEnd()
                                                AdCache.warmHome()
                                            }
                                    }
                                }
                            }
                        } else {
                            Log.w(TAG, "Remote config fetch failed - using defaults/cache", task.exception)
                        }
                    }
            }
    }

    /** True when RC looks like an empty kill-all published after unit IDs left Remote Config. */
    private fun looksLikeStaleAllOffTemplate(): Boolean {
        val allOff = !homeNativeEnabled() &&
            !homeBannerEnabled() &&
            !homeAppOpenEnabled() &&
            !chatBannerEnabled() &&
            !callEndAdsEnabled() &&
            !openChatInterstitialEnabled() &&
            !newChatInterstitialEnabled() &&
            !settingsInterstitialEnabled() &&
            !getStartedAdsEnabled() &&
            !languageAdsEnabled()
        if (!allOff) return false
        // Empty legacy unit-id keys (no longer used by the app, but still in old templates).
        val emptyIds = listOf(
            "home_native_ad_id",
            "home_banner_ad_id",
            "home_app_open_ad_id",
            "chat_banner_ad_id",
            "call_end_ad_id"
        ).all { remoteConfig.getString(it).isBlank() }
        return emptyIds
    }

    private fun logFlagSnapshot(phase: String) {
        Log.d(
            TAG,
            "flags[$phase] homeNative=${homeNativeEnabled()} homeBanner=${homeBannerEnabled()} " +
                "homeAppOpen=${homeAppOpenEnabled()} chatBanner=${chatBannerEnabled()} " +
                "callEnd=${callEndAdsEnabled()} openChat=${openChatInterstitialEnabled()} " +
                "newChat=${newChatInterstitialEnabled()} settings=${settingsInterstitialEnabled()} " +
                "getStarted=${getStartedAdsEnabled()} language=${languageAdsEnabled()} " +
                "defaultBanner=${defaultBannerEnabled()} afterCall=${afterCallScreenEnabled()} " +
                "afterCallPostCall=${afterCallPostCallPathEnabled()}"
        )
    }

    private fun markReadyAndFlush() {
        if (isReady) return
        isReady = true
        val callbacks = pendingCompletes.toList()
        pendingCompletes.clear()
        callbacks.forEach { it() }
    }

    fun splashAdsEnabled() =
        remoteConfig.getBoolean("splash_ads_enabled")

    fun getStartedAdsEnabled() =
        remoteConfig.getBoolean("get_started_ads_enabled")

    fun languageAdsEnabled() =
        remoteConfig.getBoolean("language_ads_enabled")

    fun defaultBannerEnabled() =
        remoteConfig.getBoolean("default_banner_enabled")

    fun homeNativeEnabled() =
        remoteConfig.getBoolean("home_native_enabled")

    fun homeBannerEnabled() =
        remoteConfig.getBoolean("home_banner_enabled")

    fun homeAppOpenEnabled() =
        remoteConfig.getBoolean("home_app_open_enabled")

    fun chatBannerEnabled() =
        remoteConfig.getBoolean("chat_banner_enabled")

    fun settingsInterstitialEnabled(): Boolean =
        remoteConfig.getBoolean("settings_interstitial_enabled")

    fun settingsClickLimit(): Int =
        remoteConfig.getLong("settings_click_limit").toInt()

    fun newChatInterstitialEnabled() =
        remoteConfig.getBoolean("new_chat_interstitial_enabled")

    fun newChatClickLimit() =
        remoteConfig.getLong("new_chat_click_limit").toInt()

    fun chatBackInterstitialEnabled(): Boolean =
        remoteConfig.getBoolean("chat_back_interstitial_enabled")

    fun chatBackClickLimit(): Int =
        remoteConfig.getLong("chat_back_click_limit").toInt()

    fun openChatInterstitialEnabled(): Boolean =
        remoteConfig.getBoolean("open_chat_interstitial_enabled")

    fun openChatClickLimit(): Int =
        remoteConfig.getLong("open_chat_click_limit").toInt()

    fun callEndAdsEnabled() =
        remoteConfig.getBoolean("call_end_ads_enabled")

    /** Master kill switch for the after-call screen feature (all entry points). */
    fun afterCallScreenEnabled(): Boolean =
        if (!isReady) true else remoteConfig.getBoolean("after_call_screen_enabled")

    /** Toggle for Telecom ACTION_POST_CALL path only (API 29+). */
    fun afterCallPostCallPathEnabled(): Boolean =
        if (!isReady) true else remoteConfig.getBoolean("after_call_post_call_path_enabled")
}
