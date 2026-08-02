package com.sms.textmessages.messenger.utils

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.sms.textmessages.messenger.App

object OverlayPermission {

    private const val TAG = "CALLEND_DEBUG"

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            App.disableAppOpenAd = true
            activity.startActivity(intent)
        }
    }

    // Android 11+: ROLE_CALL_SCREENING auto-grants SYSTEM_ALERT_WINDOW when the
    // app "requests" it. There is no runtime permission dialog - holding the
    // role + declaring the permission should flip AppOps / canDrawOverlays.
    // We note the op and re-check; if still false (OEM quirk / prior deny),
    // callers should offer Appear-on-top Settings as last resort.
    fun ensureOverlayAfterCallScreener(context: Context): Boolean {
        if (canDrawOverlays(context)) {
            Log.d(TAG, "ensureOverlayAfterCallScreener: already granted")
            return true
        }

        if (!CallScreeningRole.isHeld(context)) {
            Log.d(TAG, "ensureOverlayAfterCallScreener: role not held - cannot auto-grant")
            return false
        }

        // Touch AppOps so OEMs that grant lazily on note/check evaluate the role
        // permission. OP_SYSTEM_ALERT_WINDOW = 24.
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            }
            Log.d(TAG, "ensureOverlayAfterCallScreener: appOps mode=$mode canDraw=${canDrawOverlays(context)}")
        } catch (e: Exception) {
            Log.w(TAG, "ensureOverlayAfterCallScreener: AppOps check failed - ${e.message}")
        }

        val granted = canDrawOverlays(context)
        Log.d(TAG, "ensureOverlayAfterCallScreener: final canDrawOverlays=$granted")
        return granted
    }
}
