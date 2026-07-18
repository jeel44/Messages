package com.sms.textmessages.messenger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sms.textmessages.messenger.service.OverlayHostService

// A foreground service does not survive a device reboot on its own - the OS
// tears down every process at shutdown, so OverlayHostService needs to be
// restarted from somewhere once the device comes back up, before the user
// has necessarily opened the app themselves. BOOT_COMPLETED is one of the
// broadcasts exempt from Android 12+'s restriction on starting a foreground
// service from the background, so it's safe to call startForegroundService()
// here.
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CALLEND_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "BootCompletedReceiver.onReceive: BOOT_COMPLETED - restarting OverlayHostService")
        OverlayHostService.start(context.applicationContext)
    }
}
