package com.sms.textmessages.messenger.calling

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdView
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.ui.overlay.CallEndOverlayCard
import com.sms.textmessages.messenger.ui.theme.MessagesTheme
import com.sms.textmessages.messenger.utils.PreferenceManager
import kotlinx.coroutines.delay

/**
 * Shared Compose host for [CallEndPopupActivity] / [CallEndFullscreenActivity].
 * Ads load asynchronously only after real content is composed — never via overlay.
 */
internal fun ComponentActivity.renderCallEndScreen(
    event: CallEndEvent,
    showWhenLocked: Boolean
) {
    if (showWhenLocked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    enableEdgeToEdge()
    Log.d("CALLEND_DEBUG", "renderCallEndScreen type=${event.type} locked=$showWhenLocked")

    val adsAllowed = !PreferenceManager.isPremiumSubscribed(this) &&
        com.sms.textmessages.messenger.ads.RemoteConfigManager.callEndAdsEnabled()

    setContent {
        MessagesTheme {
            var bannerAd by remember { mutableStateOf<AdView?>(null) }
            var contentReady by remember { mutableStateOf(false) }

            CallEndOverlayCard(
                displayName = event.displayName,
                phoneNumber = event.number,
                callType = event.type,
                durationMs = event.durationMs,
                bannerAd = bannerAd,
                showAdSlot = adsAllowed,
                unreadCount = null,
                onMessage = {
                    CallEndActions.openChat(this, event.number)
                    finish()
                },
                onCallBack = {
                    CallEndActions.callBack(this, event.number)
                    finish()
                },
                onSave = {
                    CallEndActions.saveContact(this, event.number)
                    finish()
                },
                onBlock = {
                    CallEndActions.blockNumber(this, event.number)
                    finish()
                },
                onOpenApp = {
                    CallEndActions.openApp(this)
                    finish()
                },
                onDismiss = { finish() },
                onCopyNumber = { CallEndActions.copyNumber(this, event.number) },
                onViewContact = {
                    CallEndActions.viewContact(this, event.number)
                    finish()
                },
                onReportSpam = {
                    CallEndActions.reportSpam(this, event.number)
                    finish()
                }
            )

            LaunchedEffect(Unit) {
                contentReady = true
                if (!adsAllowed) return@LaunchedEffect
                // Content paints first; then kick off ad load.
                delay(50)
                AdCache.ensure(AdPlacement.CALL_END_BANNER, applicationContext)
                // Poll briefly for the cached AdView without blocking first frame.
                repeat(40) {
                    val ad = AdCache.bannerState(AdPlacement.CALL_END_BANNER).value
                    if (ad != null) {
                        bannerAd = ad
                        return@LaunchedEffect
                    }
                    delay(100)
                }
            }

            // Silence unused warning for contentReady (marks first compose).
            @Suppress("UNUSED_EXPRESSION")
            contentReady
        }
    }
}

internal fun ComponentActivity.readCallEndEvent(): CallEndEvent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(CallEndContract.EXTRA_EVENT, CallEndEvent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(CallEndContract.EXTRA_EVENT)
    }
}
