package com.sms.textmessages.messenger.ads

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigManager {

    private const val TAG = "RemoteConfig"

    private val remoteConfig: FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance()

    fun init(onComplete: () -> Unit) {

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(10)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(

            // ================= SPLASH =================
            "splash_app_open_ad_id" to "",
            "splash_interstitial_ad_id" to "",
            "splash_native_ad_id" to "",
            "splash_ads_enabled" to true,

            // ================= GET STARTED =================
            "get_started_native_ad_id" to "",
            "get_started_ads_enabled" to true,

            // ================= LANGUAGE =================
            "language_native_ad_id" to "",
            "language_ads_enabled" to true,

            // ================= DEFAULT SCREEN =================
            "default_banner_ad_id" to "",
            "default_banner_enabled" to true,

            // ================= HOME SCREEN =================
            "home_native_ad_id" to "",
            "home_native_enabled" to true,

            "home_banner_ad_id" to "",
            "home_banner_enabled" to true,

            // ================= HOME APP OPEN =================
            "home_app_open_ad_id" to "",
            "home_app_open_enabled" to true,

            // ================= Setting Back Click =================
            "settings_interstitial_ad_id" to "",
            "settings_interstitial_enabled" to true,

            // ================= New Chat Click =================
            "new_chat_interstitial_id" to "",
            "new_chat_interstitial_enabled" to true,
            "new_chat_click_limit" to 2,

            // ================= Chat Banner =================
            "chat_banner_ad_id" to "",
            "chat_banner_enabled" to true,

            // ================= Chat Back Click =================
            "chat_back_interstitial_id" to "",
            "chat_back_interstitial_enabled" to true,
            "chat_back_click_limit" to 3L,

            // ================= Open Chat Click =================
            "open_chat_interstitial_id" to "",
            "open_chat_interstitial_enabled" to true,
            "open_chat_click_limit" to 3L

        )

        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener {
                Log.d(TAG, "Remote config fetch complete")
            }

        onComplete()
    }

    // ================= SPLASH =================

    fun splashAppOpenId() =
        remoteConfig.getString("splash_app_open_ad_id")

    fun splashInterstitialId() =
        remoteConfig.getString("splash_interstitial_ad_id")

    fun splashNativeId() =
        remoteConfig.getString("splash_native_ad_id")

    fun splashAdsEnabled() =
        remoteConfig.getBoolean("splash_ads_enabled")

    // ================= GET STARTED =================

    fun getStartedNativeId() =
        remoteConfig.getString("get_started_native_ad_id")

    fun getStartedAdsEnabled() =
        remoteConfig.getBoolean("get_started_ads_enabled")

    // ================= LANGUAGE =================

    fun languageNativeId() =
        remoteConfig.getString("language_native_ad_id")

    fun languageAdsEnabled() =
        remoteConfig.getBoolean("language_ads_enabled")

    // ================= DEFAULT SCREEN =================

    fun defaultBannerId() =
        remoteConfig.getString("default_banner_ad_id")

    fun defaultBannerEnabled() =
        remoteConfig.getBoolean("default_banner_enabled")

    // ================= HOME =================

    fun homeNativeId() =
        remoteConfig.getString("home_native_ad_id")

    fun homeNativeEnabled() =
        remoteConfig.getBoolean("home_native_enabled")

    fun homeBannerId() =
        remoteConfig.getString("home_banner_ad_id")

    fun homeBannerEnabled() =
        remoteConfig.getBoolean("home_banner_enabled")

    fun homeAppOpenId() =
        remoteConfig.getString("home_app_open_ad_id")

    fun homeAppOpenEnabled() =
        remoteConfig.getBoolean("home_app_open_enabled")

    // ================= APP OPEN =================

    fun chatBannerId() =
        remoteConfig.getString("chat_banner_ad_id")

    fun chatBannerEnabled() =
        remoteConfig.getBoolean("chat_banner_enabled")

    fun settingsInterstitialEnabled(): Boolean {
        return remoteConfig.getBoolean("settings_interstitial_enabled")
    }

    fun settingsInterstitialId(): String {
        return remoteConfig.getString("settings_interstitial_ad_id")
    }

    fun newChatInterstitialId() =
        remoteConfig.getString("new_chat_interstitial_id")

    fun newChatInterstitialEnabled() =
        remoteConfig.getBoolean("new_chat_interstitial_enabled")

    fun newChatClickLimit() =
        remoteConfig.getLong("new_chat_click_limit").toInt()

    fun chatBackInterstitialEnabled(): Boolean {
        return remoteConfig.getBoolean("chat_back_interstitial_enabled")
    }

    fun chatBackInterstitialId(): String {
        return remoteConfig.getString("chat_back_interstitial_id")
    }

    fun chatBackClickLimit(): Int {
        return remoteConfig.getLong("chat_back_click_limit").toInt()
    }

    fun openChatInterstitialEnabled(): Boolean {
        return remoteConfig.getBoolean("open_chat_interstitial_enabled")
    }

    fun openChatInterstitialId(): String {
        return remoteConfig.getString("open_chat_interstitial_id")
    }

    fun openChatClickLimit(): Int {
        return remoteConfig.getLong("open_chat_click_limit").toInt()
    }

}