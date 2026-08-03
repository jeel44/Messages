package com.sms.textmessages.messenger.calling

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.sms.textmessages.messenger.ads.AdCache
import com.sms.textmessages.messenger.ads.AdPlacement

/**
 * Fullscreen after-call screen for locked device or missed calls.
 * Manifest: showWhenLocked + turnScreenOn.
 */
class CallEndFullscreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("CALLEND_DEBUG", "CallEndFullscreenActivity.onCreate")
        CallEndActivityLauncher.onActivityShown(this)
        val event = readCallEndEvent()
        if (event == null) {
            android.util.Log.e("CALLEND_DEBUG", "CallEndFullscreenActivity: missing EXTRA_EVENT - finish")
            finish()
            return
        }
        android.util.Log.d(
            "CALLEND_DEBUG",
            "CallEndFullscreenActivity show type=${event.type} number=${event.number} name=${event.displayName}"
        )
        renderCallEndScreen(event, showWhenLocked = true)
    }

    override fun onDestroy() {
        AdCache.detachBanner(AdPlacement.CALL_END_BANNER)
        super.onDestroy()
    }
}
