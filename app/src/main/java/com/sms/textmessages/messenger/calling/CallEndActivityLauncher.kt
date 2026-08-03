package com.sms.textmessages.messenger.calling

import android.content.Context
import android.util.Log

/**
 * After-call UI entry: Activity first, overlay only if Activity is blocked.
 * During-call bubble is owned by [DuringCallOverlayHost] and dismissed on hang-up.
 */
object CallEndActivityLauncher {

    private const val TAG = "CALLEND_DEBUG"

    fun launch(context: Context, event: CallEndEvent, fullscreen: Boolean) {
        Log.d(
            TAG,
            "Launcher: activity-first (overlay fallback) fullscreen=$fullscreen " +
                "type=${event.type} number=${event.number}"
        )
        CallEndOverlayHost.openActivityWithOverlayFallback(
            context.applicationContext,
            event,
            fullscreen
        )
    }

    fun onActivityShown(context: Context) {
        CallEndOverlayHost.onActivityShown()
        Log.d(TAG, "Launcher: Activity shown — overlay cancelled/dismissed")
    }
}
