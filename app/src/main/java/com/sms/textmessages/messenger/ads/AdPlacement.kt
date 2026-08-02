package com.sms.textmessages.messenger.ads

/**
 * Every AdMob placement. Unit IDs come from [AdUnitIds] / strings.xml only.
 * Remote Config still gates enable flags and click limits.
 *
 * ## Expiry policy (safer than Google's documented maxima)
 *
 * Documented limits (do not show past these):
 * - App open: **4 hours** (hard timeout in Google docs)
 * - Interstitial / native / *preloaded* banner: **~1 hour** cache tip
 * - Banner on-screen: AdMob console **auto-refresh 30–150s** (not a load-expiry)
 *
 * Our [expiryMs] values are intentionally shorter so cached ads stay fresh.
 */
enum class AdFormat {
    APP_OPEN,
    INTERSTITIAL,
    NATIVE,
    BANNER
}

object AdExpiry {
    const val DOC_APP_OPEN_MS = 4 * 60 * 60 * 1000L
    const val DOC_FULLSCREEN_OR_NATIVE_MS = 60 * 60 * 1000L
    const val DOC_PRELOADED_BANNER_MS = 60 * 60 * 1000L

    const val APP_OPEN_MS = 60 * 60 * 1000L          // 1h  (doc: 4h)
    const val INTERSTITIAL_MS = 30 * 60 * 1000L      // 30m (doc: 1h)
    const val NATIVE_MS = 30 * 60 * 1000L            // 30m (doc: 1h)
    const val BANNER_CACHE_MS = 30 * 60 * 1000L      // 30m (doc preload tip: 1h)

    /** How often visible home/chat banners force a new request (UX refresh). */
    const val BANNER_ON_SCREEN_REFRESH_MS = 60 * 1000L
}

enum class AdPlacement(
    val format: AdFormat,
    val expiryMs: Long
) {
    HOME_APP_OPEN(AdFormat.APP_OPEN, AdExpiry.APP_OPEN_MS),

    SETTINGS_INTERSTITIAL(AdFormat.INTERSTITIAL, AdExpiry.INTERSTITIAL_MS),
    NEW_CHAT_INTERSTITIAL(AdFormat.INTERSTITIAL, AdExpiry.INTERSTITIAL_MS),
    OPEN_CHAT_INTERSTITIAL(AdFormat.INTERSTITIAL, AdExpiry.INTERSTITIAL_MS),
    CHAT_BACK_INTERSTITIAL(AdFormat.INTERSTITIAL, AdExpiry.INTERSTITIAL_MS),

    HOME_NATIVE(AdFormat.NATIVE, AdExpiry.NATIVE_MS),
    GET_STARTED_NATIVE(AdFormat.NATIVE, AdExpiry.NATIVE_MS),
    LANGUAGE_NATIVE(AdFormat.NATIVE, AdExpiry.NATIVE_MS),

    HOME_BANNER(AdFormat.BANNER, AdExpiry.BANNER_CACHE_MS),
    DEFAULT_BANNER(AdFormat.BANNER, AdExpiry.BANNER_CACHE_MS),
    CHAT_BANNER(AdFormat.BANNER, AdExpiry.BANNER_CACHE_MS),
    CALL_END_BANNER(AdFormat.BANNER, AdExpiry.BANNER_CACHE_MS);

    fun isEnabled(): Boolean = when (this) {
        HOME_APP_OPEN -> RemoteConfigManager.homeAppOpenEnabled()
        SETTINGS_INTERSTITIAL -> RemoteConfigManager.settingsInterstitialEnabled()
        NEW_CHAT_INTERSTITIAL -> RemoteConfigManager.newChatInterstitialEnabled()
        OPEN_CHAT_INTERSTITIAL -> RemoteConfigManager.openChatInterstitialEnabled()
        CHAT_BACK_INTERSTITIAL -> RemoteConfigManager.chatBackInterstitialEnabled()
        HOME_NATIVE -> RemoteConfigManager.homeNativeEnabled()
        GET_STARTED_NATIVE -> RemoteConfigManager.getStartedAdsEnabled()
        LANGUAGE_NATIVE -> RemoteConfigManager.languageAdsEnabled()
        HOME_BANNER -> RemoteConfigManager.homeBannerEnabled()
        DEFAULT_BANNER -> RemoteConfigManager.defaultBannerEnabled()
        CHAT_BANNER -> RemoteConfigManager.chatBannerEnabled()
        CALL_END_BANNER -> RemoteConfigManager.callEndAdsEnabled()
    }

    fun adUnitId(): String = when (this) {
        CALL_END_BANNER -> AdUnitIds.callEndMrec()
        else -> AdUnitIds.forFormat(format)
    }

    fun clickLimit(): Int = when (this) {
        NEW_CHAT_INTERSTITIAL -> RemoteConfigManager.newChatClickLimit()
        OPEN_CHAT_INTERSTITIAL -> RemoteConfigManager.openChatClickLimit()
        CHAT_BACK_INTERSTITIAL -> RemoteConfigManager.chatBackClickLimit()
        else -> 1
    }
}
