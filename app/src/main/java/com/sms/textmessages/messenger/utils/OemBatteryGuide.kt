package com.sms.textmessages.messenger.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.sms.textmessages.messenger.App

// Optional advanced help for OEMs that still kill the process before call
// start. Not primary onboarding — during-call overlay is the main strategy.
object OemBatteryGuide {

    private const val TAG = "CALLEND_DEBUG"

    data class GuideStep(
        val title: String,
        val body: String,
        val intent: Intent?
    )

    fun manufacturerKey(): String =
        Build.MANUFACTURER.orEmpty().lowercase()

    fun isAggressiveOem(): Boolean {
        val m = manufacturerKey()
        return listOf(
            "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
            "huawei", "honor", "oneplus", "samsung", "meizu", "tecno", "infinix"
        ).any { m.contains(it) }
    }

    fun steps(context: Context): List<GuideStep> {
        val m = manufacturerKey()
        val steps = mutableListOf<GuideStep>()

        steps += GuideStep(
            title = "Battery unrestricted",
            body = "Allow unrestricted battery use so call events can wake the app.",
            intent = ignoreBatteryOptimizationsIntent(context)
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
        )

        when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> {
                steps += GuideStep(
                    title = "Autostart",
                    body = "Enable Autostart for Messages in Security / App permissions.",
                    intent = safeComponentIntent(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    ) ?: appDetails(context)
                )
            }
            m.contains("oppo") || m.contains("realme") -> {
                steps += GuideStep(
                    title = "Allow background activity",
                    body = "Disable Background freeze and allow background activity for Messages.",
                    intent = appDetails(context)
                )
            }
            m.contains("vivo") || m.contains("iqoo") -> {
                steps += GuideStep(
                    title = "Autostart",
                    body = "Enable Autostart / high background power for Messages in iManager.",
                    intent = safeComponentIntent(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    ) ?: appDetails(context)
                )
            }
            m.contains("huawei") || m.contains("honor") -> {
                steps += GuideStep(
                    title = "App launch / Protected apps",
                    body = "Set App launch to Manage manually and enable all toggles for Messages.",
                    intent = appDetails(context)
                )
            }
            m.contains("samsung") -> {
                steps += GuideStep(
                    title = "Never sleeping apps",
                    body = "Add Messages to Never sleeping apps and turn off Put unused apps to sleep.",
                    intent = appDetails(context)
                )
            }
            m.contains("oneplus") -> {
                steps += GuideStep(
                    title = "Allow background activity",
                    body = "Set battery use to Unrestricted / allow background activity.",
                    intent = appDetails(context)
                )
            }
        }

        return steps
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "openAppDetails failed: ${e.message}")
        }
    }

    // Shown after real call-end misses, when the user next opens the app —
    // never at install. Max twice, 7-day cooldown, forever-dismissable.
    fun maybeShowGentlePrompt(activity: android.app.Activity) {
        if (!CallEndMetrics.isOemPromptPending(activity)) return
        if (!isAggressiveOem()) {
            CallEndMetrics.setPendingOemPrompt(activity, false)
            return
        }
        if (PreferenceManager.isOemBatteryHelpDismissedForever(activity)) {
            CallEndMetrics.setPendingOemPrompt(activity, false)
            return
        }

        CallEndMetrics.setPendingOemPrompt(activity, false)
        PreferenceManager.markOemBatteryHelpPrompted(activity)

        val step = steps(activity).firstOrNull()
        Log.d(TAG, "OemBatteryGuide: showing gentle prompt misses=${CallEndMetrics.missCount(activity)}")

        android.app.AlertDialog.Builder(activity)
            .setTitle("Calls sometimes missed?")
            .setMessage(
                "On some phones, battery settings block caller ID after calls. " +
                    "Allowing background activity for Messages usually fixes this. " +
                    "You can change this anytime in Settings."
            )
            .setPositiveButton("Improve reliability") { _, _ ->
                App.disableAppOpenAd = true
                val intent = step?.intent
                try {
                    if (intent != null) {
                        activity.startActivity(intent)
                    } else {
                        openAppDetails(activity)
                    }
                } catch (_: Exception) {
                    openAppDetails(activity)
                }
            }
            .setNeutralButton("Not now", null)
            .setNegativeButton("Don't ask again") { _, _ ->
                PreferenceManager.setOemBatteryHelpDismissedForever(activity)
            }
            .show()
    }

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    private fun ignoreBatteryOptimizationsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun safeComponentIntent(pkg: String, cls: String): Intent? =
        try {
            Intent().setComponent(ComponentName(pkg, cls))
        } catch (_: Exception) {
            null
        }
}
