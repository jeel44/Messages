package com.sms.textmessages.messenger.calling

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement

/**
 * Lighter after-call screen for unlocked, non-missed calls.
 * Always a real Activity — never a WindowManager overlay.
 */
class CallEndPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("CALLEND_DEBUG", "CallEndPopupActivity.onCreate")
        CallEndActivityLauncher.onActivityShown(this)
        val event = readCallEndEvent()
        if (event == null) {
            android.util.Log.e("CALLEND_DEBUG", "CallEndPopupActivity: missing EXTRA_EVENT - finish")
            finish()
            return
        }
        android.util.Log.d(
            "CALLEND_DEBUG",
            "CallEndPopupActivity show type=${event.type} number=${event.number} name=${event.displayName}"
        )
        renderCallEndScreen(event, showWhenLocked = false)
    }

    override fun onDestroy() {
        AdCache.detachBanner(AdPlacement.CALL_END_BANNER)
        super.onDestroy()
    }
}
