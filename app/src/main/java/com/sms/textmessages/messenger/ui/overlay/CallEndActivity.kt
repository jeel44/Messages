package com.sms.textmessages.messenger.ui.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement
import com.sms.textmessages.messenger.receiver.CallEndType
import com.sms.textmessages.messenger.ui.theme.MessagesTheme

// Fallback Activity when WindowManager overlay cannot be shown (no overlay
// permission or addView failed). Opened from CallEndNotifier.
class CallEndActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE)
        val callTypeName = intent.getStringExtra(EXTRA_TYPE) ?: CallEndType.MISSED.name
        val durationMs = intent.getLongExtra(EXTRA_DURATION, 0L)
        val callType = runCatching { CallEndType.valueOf(callTypeName) }.getOrDefault(CallEndType.MISSED)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?: phoneNumber
            ?: "Unknown"

        AdCache.ensure(AdPlacement.CALL_END_BANNER, this)

        setContent {
            MessagesTheme {
                val bannerAd = AdCache.bannerState(AdPlacement.CALL_END_BANNER).value
                CallEndOverlayCard(
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    callType = callType,
                    durationMs = durationMs,
                    bannerAd = bannerAd,
                    unreadCount = null,
                    onMessage = {
                        CallEndOverlayManager.openChatFromExternal(this, phoneNumber)
                        finish()
                    },
                    onCallBack = {
                        CallEndOverlayManager.callBackFromExternal(this, phoneNumber)
                        finish()
                    },
                    onSave = {
                        CallEndOverlayManager.saveContactFromExternal(this, phoneNumber)
                        finish()
                    },
                    onBlock = {
                        CallEndOverlayManager.blockFromExternal(this, phoneNumber)
                        finish()
                    },
                    onOpenApp = {
                        CallEndOverlayManager.openAppFromExternal(this)
                        finish()
                    },
                    onDismiss = { finish() },
                    onCopyNumber = {
                        CallEndOverlayManager.copyNumberFromExternal(this, phoneNumber)
                    },
                    onViewContact = {
                        CallEndOverlayManager.viewContactFromExternal(this, phoneNumber)
                        finish()
                    },
                    onReportSpam = {
                        CallEndOverlayManager.reportSpamFromExternal(this, phoneNumber)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        // Keep banner in AdCache for the next call; only detach from this Activity.
        AdCache.detachBanner(AdPlacement.CALL_END_BANNER)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_TYPE = "type"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DISPLAY_NAME = "display_name"
    }
}
