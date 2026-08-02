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
            "default_banner_enabled" to true,
            "home_native_enabled" to true,
            "home_banner_enabled" to true,
            "home_app_open_enabled" to true,
            "settings_interstitial_enabled" to true,
            "new_chat_interstitial_enabled" to true,
            "new_chat_click_limit" to 2,
            "chat_banner_enabled" to true,
            "chat_back_interstitial_enabled" to true,
            "chat_back_click_limit" to 3L,
            "open_chat_interstitial_enabled" to true,
            "open_chat_click_limit" to 3L,
            "call_end_ads_enabled" to true
        )

        // Mark ready as soon as defaults are applied so call-end / cold-start
        // overlays can load ads immediately. Do NOT wait on network fetch —
        // that delayed isReady by ~5s and left CALL_END_BANNER on shimmer forever.
        remoteConfig.setDefaultsAsync(defaults)
            .addOnCompleteListener {
                markReadyAndFlush()
                remoteConfig.fetchAndActivate()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Remote config fetch complete - activated=${task.result}")
                        } else {
                            Log.w(TAG, "Remote config fetch failed - using defaults/cache", task.exception)
                        }
                    }
            }
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
}
